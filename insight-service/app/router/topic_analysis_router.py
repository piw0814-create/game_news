import logging

from fastapi import APIRouter, HTTPException

from app.model.schemas import TopicReanalysisResponse
from app.service.topic_analysis_service import topic_analysis_service

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/api/internal/insights/topics", tags=["Topic Analysis"])


@router.post("/{topic_id}/reanalyze", response_model=TopicReanalysisResponse)
def reanalyze_topic(topic_id: int) -> TopicReanalysisResponse:
    try:
        return topic_analysis_service.reanalyze(topic_id)
    except Exception as exc:
        logger.exception(
            "[TopicAnalysisRouter] 수동 재분석 실패 - topicId=%s error=%s",
            topic_id,
            exc,
        )
        raise HTTPException(status_code=502, detail=str(exc)) from exc
