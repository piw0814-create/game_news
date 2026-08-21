import logging
from typing import Any

logger = logging.getLogger(__name__)


def log_openai_usage(response: Any, request_type: str, entity_id: int | None = None) -> None:
    """Responses API usage를 비용 감사용으로 안전하게 기록한다."""
    usage = getattr(response, "usage", None)
    if usage is None:
        logger.info(
            "[OpenAIUsage] type=%s entityId=%s usage=unavailable",
            request_type,
            entity_id,
        )
        return

    input_details = getattr(usage, "input_tokens_details", None)
    output_details = getattr(usage, "output_tokens_details", None)

    logger.info(
        "[OpenAIUsage] type=%s entityId=%s input=%s cached=%s cacheWrite=%s output=%s reasoning=%s total=%s",
        request_type,
        entity_id,
        getattr(usage, "input_tokens", None),
        getattr(input_details, "cached_tokens", 0) if input_details is not None else 0,
        getattr(input_details, "cache_write_tokens", 0) if input_details is not None else 0,
        getattr(usage, "output_tokens", None),
        getattr(output_details, "reasoning_tokens", 0) if output_details is not None else 0,
        getattr(usage, "total_tokens", None),
    )
