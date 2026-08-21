import sys
import types
from types import SimpleNamespace

openai_stub = types.ModuleType("openai")
openai_stub.OpenAI = object
sys.modules.setdefault("openai", openai_stub)

try:
    import kafka  # noqa: F401
except ModuleNotFoundError:
    kafka_stub = types.ModuleType("kafka")

    class _TopicPartition:
        def __init__(self, topic, partition):
            self.topic = topic
            self.partition = partition

        def __eq__(self, other):
            return (self.topic, self.partition) == (other.topic, other.partition)

    kafka_stub.KafkaConsumer = object
    kafka_stub.KafkaProducer = object
    kafka_stub.TopicPartition = _TopicPartition
    sys.modules["kafka"] = kafka_stub

from app.kafka import consumer as consumer_module
from app.kafka.consumer import NewsCreatedConsumer


class FakeConsumer:
    def __init__(self):
        self.commits = 0
        self.seeks = []
        self.closed = False

    def commit(self):
        self.commits += 1

    def seek(self, partition, offset):
        self.seeks.append((partition, offset))

    def close(self):
        self.closed = True


def _message(value: bytes, *, offset: int = 10):
    return SimpleNamespace(
        topic="news.created",
        partition=0,
        offset=offset,
        value=value,
    )


def test_malformed_json_goes_to_dlq_and_commits(monkeypatch):
    service = NewsCreatedConsumer()
    kafka_consumer = FakeConsumer()
    published = []

    monkeypatch.setattr(
        consumer_module.dead_letter_publisher,
        "publish",
        lambda message, error: published.append((message.offset, error)) or True,
    )
    monkeypatch.setattr(
        consumer_module.article_analysis_service,
        "process",
        lambda article_id: (_ for _ in ()).throw(AssertionError("poison event는 분석하면 안 됨")),
    )

    assert service._process_message(kafka_consumer, _message(b"{broken json")) is True
    assert kafka_consumer.commits == 1
    assert kafka_consumer.seeks == []
    assert published and "invalid json payload" in published[0][1]


def test_missing_article_id_goes_to_dlq(monkeypatch):
    service = NewsCreatedConsumer()
    kafka_consumer = FakeConsumer()
    errors = []

    monkeypatch.setattr(
        consumer_module.dead_letter_publisher,
        "publish",
        lambda message, error: errors.append(error) or True,
    )

    assert service._process_message(kafka_consumer, _message(b'{"foo":"bar"}')) is True
    assert kafka_consumer.commits == 1
    assert errors == ["articleId is required"]


def test_dlq_publish_failure_does_not_commit_and_seeks_for_retry(monkeypatch):
    service = NewsCreatedConsumer()
    kafka_consumer = FakeConsumer()

    monkeypatch.setattr(
        consumer_module.dead_letter_publisher,
        "publish",
        lambda message, error: False,
    )

    assert service._process_message(kafka_consumer, _message(b"not-json", offset=21)) is False
    assert kafka_consumer.commits == 0
    assert len(kafka_consumer.seeks) == 1
    assert kafka_consumer.seeks[0][1] == 21


def test_transient_article_processing_failure_retries_without_dlq(monkeypatch):
    service = NewsCreatedConsumer()
    kafka_consumer = FakeConsumer()

    monkeypatch.setattr(
        consumer_module.dead_letter_publisher,
        "publish",
        lambda *args, **kwargs: (_ for _ in ()).throw(AssertionError("정상 event를 DLQ로 보내면 안 됨")),
    )
    monkeypatch.setattr(
        consumer_module.article_analysis_service,
        "process",
        lambda article_id: False,
    )

    assert service._process_message(kafka_consumer, _message(b'{"articleId":123}', offset=31)) is False
    assert kafka_consumer.commits == 0
    assert len(kafka_consumer.seeks) == 1
    assert kafka_consumer.seeks[0][1] == 31


def test_normal_event_commits_after_success(monkeypatch):
    service = NewsCreatedConsumer()
    kafka_consumer = FakeConsumer()
    analyzed = []

    monkeypatch.setattr(
        consumer_module.dead_letter_publisher,
        "publish",
        lambda *args, **kwargs: (_ for _ in ()).throw(AssertionError("정상 event를 DLQ로 보내면 안 됨")),
    )
    monkeypatch.setattr(
        consumer_module.article_analysis_service,
        "process",
        lambda article_id: analyzed.append(article_id) or True,
    )

    assert service._process_message(kafka_consumer, _message(b'{"articleId":"123"}', offset=41)) is True
    assert analyzed == [123]
    assert kafka_consumer.commits == 1
    assert kafka_consumer.seeks == []


def test_consumer_supervisor_reconnects_with_exponential_backoff(monkeypatch):
    service = NewsCreatedConsumer()
    service._running = True
    attempts = []
    delays = []

    def fake_create_consumer():
        attempts.append(len(attempts) + 1)
        if len(attempts) < 3:
            raise RuntimeError("kafka temporarily unavailable")
        service._running = False
        raise RuntimeError("stop test loop")

    monkeypatch.setattr(service, "_create_consumer", fake_create_consumer)
    monkeypatch.setattr(service, "_sleep_interruptibly", lambda delay: delays.append(delay))

    service._run_supervisor()

    assert attempts == [1, 2, 3]
    assert delays == [
        consumer_module.settings.kafka_reconnect_initial_delay_seconds,
        min(
            consumer_module.settings.kafka_reconnect_initial_delay_seconds * 2,
            consumer_module.settings.kafka_reconnect_max_delay_seconds,
        ),
    ]
