from types import SimpleNamespace

from app.client.openai_usage import log_openai_usage


def test_usage_logger_handles_cached_tokens(caplog):
    response = SimpleNamespace(
        usage=SimpleNamespace(
            input_tokens=1000,
            input_tokens_details=SimpleNamespace(cached_tokens=800, cache_write_tokens=100),
            output_tokens=120,
            output_tokens_details=SimpleNamespace(reasoning_tokens=20),
            total_tokens=1120,
        )
    )
    with caplog.at_level("INFO"):
        log_openai_usage(response, "article", 10)
    text = caplog.text
    assert "type=article" in text
    assert "cached=800" in text
    assert "cacheWrite=100" in text
    assert "output=120" in text
