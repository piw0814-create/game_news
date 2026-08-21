import logging
from typing import Any, List

import httpx

from app.config.settings import settings
from app.model.schemas import (
    AnalysisStatus,
    ArticleAnalysisResult,
    ArticleFranchiseResponse,
    ArticleGameResponse,
    FranchiseResponse,
    EntityResolveResponse,
    GameResponse,
    NewsArticleResponse,
    NewsCategory,
    TopicAnalysisContextResponse,
    TopicCandidateResponse,
    TopicIntegrationResponse,
    TopicStoredResponse,
)

logger = logging.getLogger(__name__)


class NewsServiceError(RuntimeError):
    def __init__(self, message: str, status_code: int | None = None):
        super().__init__(message)
        self.status_code = status_code


class NewsServiceClient:
    """Insight Service가 News Service와 통신하는 동기 REST Client."""

    def __init__(self):
        self.base_url = settings.news_service_url.rstrip("/")
        self.timeout = settings.news_service_timeout_seconds

    def get_news(self, article_id: int) -> NewsArticleResponse:
        data = self._request("GET", f"/api/news/{article_id}")
        return NewsArticleResponse.model_validate(data)

    def get_games(self) -> List[GameResponse]:
        data = self._request("GET", "/api/games")
        return [GameResponse.model_validate(item) for item in data]

    def get_franchises(self) -> List[FranchiseResponse]:
        data = self._request("GET", "/api/franchises")
        return [FranchiseResponse.model_validate(item) for item in data]

    def resolve_game_entity(
        self,
        article_id: int,
        detected_name: str,
        entity_type: str,
        is_primary: bool,
        confidence_score: float,
        reason: str,
    ) -> EntityResolveResponse:
        data = self._request(
            "POST",
            "/api/internal/entity-reviews/resolve-game",
            json={
                "articleId": article_id,
                "detectedName": detected_name,
                "entityType": entity_type,
                "primary": is_primary,
                "confidenceScore": confidence_score,
                "reason": reason,
            },
        )
        return EntityResolveResponse.model_validate(data)

    def resolve_franchise_entity(
        self,
        article_id: int,
        detected_name: str,
        entity_type: str,
        is_primary: bool,
        confidence_score: float,
        reason: str,
    ) -> EntityResolveResponse:
        data = self._request(
            "POST",
            "/api/internal/entity-reviews/resolve-franchise",
            json={
                "articleId": article_id,
                "detectedName": detected_name,
                "entityType": entity_type,
                "primary": is_primary,
                "confidenceScore": confidence_score,
                "reason": reason,
            },
        )
        return EntityResolveResponse.model_validate(data)

    def get_recovery_candidates(
        self,
        limit: int,
        processing_stale_minutes: int,
        pending_stale_minutes: int = 0,
        exclude_ids: List[int] | None = None,
    ) -> List[NewsArticleResponse]:
        params = {
            "limit": limit,
            "processingStaleMinutes": processing_stale_minutes,
            "pendingStaleMinutes": pending_stale_minutes,
        }
        if exclude_ids:
            params["excludeIds"] = ",".join(str(article_id) for article_id in exclude_ids)

        data = self._request(
            "GET",
            "/api/internal/news/recovery-candidates",
            params=params,
        )
        return [NewsArticleResponse.model_validate(item) for item in data]

    def get_article_games(self, article_id: int) -> List[ArticleGameResponse]:
        data = self._request("GET", f"/api/news/{article_id}/games")
        return [ArticleGameResponse.model_validate(item) for item in data]

    def get_article_franchises(self, article_id: int) -> List[ArticleFranchiseResponse]:
        data = self._request("GET", f"/api/news/{article_id}/franchises")
        return [ArticleFranchiseResponse.model_validate(item) for item in data]

    def link_franchise(
        self,
        article_id: int,
        franchise_id: int,
        is_primary: bool,
        confidence_score: float,
        relevance_reason: str,
    ) -> ArticleFranchiseResponse:
        data = self._request(
            "POST",
            f"/api/news/{article_id}/franchises",
            json={
                "franchiseId": franchise_id,
                "isPrimary": is_primary,
                "confidenceScore": confidence_score,
                "relevanceReason": relevance_reason,
            },
        )
        return ArticleFranchiseResponse.model_validate(data)


    def save_analysis_checkpoint(
        self,
        article_id: int,
        analysis: ArticleAnalysisResult,
    ) -> NewsArticleResponse:
        data = self._request(
            "PUT",
            f"/api/internal/news/{article_id}/analysis-checkpoint",
            json={
                "summary": analysis.summary,
                "category": analysis.category.value,
                "keywords": analysis.keywords,
                "gameNewsRelevant": analysis.gameNewsRelevant,
                "entityType": analysis.entityType.value,
                "initialTopicTitle": analysis.topicTitle,
                "semanticImportanceScore": analysis.semanticImportanceScore,
                "initialWhyImportant": analysis.whyImportant,
                "analysisPayload": analysis.model_dump_json(),
            },
        )
        return NewsArticleResponse.model_validate(data)

    def get_analysis_checkpoint(self, article_id: int) -> ArticleAnalysisResult:
        data = self._request(
            "GET",
            f"/api/internal/news/{article_id}/analysis-checkpoint",
        )
        if not isinstance(data, str) or not data.strip():
            raise NewsServiceError(
                f"기사 분석 체크포인트가 비어 있습니다: articleId={article_id}"
            )
        return ArticleAnalysisResult.model_validate_json(data)

    def update_analysis_status(
        self,
        article_id: int,
        status: AnalysisStatus,
    ) -> NewsArticleResponse:
        data = self._request(
            "PATCH",
            f"/api/news/{article_id}/analysis-status",
            json={"status": status.value},
        )
        return NewsArticleResponse.model_validate(data)


    def link_game(
        self,
        article_id: int,
        game_id: int,
        is_primary: bool,
        confidence_score: float,
        relevance_reason: str,
    ) -> ArticleGameResponse:
        data = self._request(
            "POST",
            f"/api/news/{article_id}/games",
            json={
                "gameId": game_id,
                "isPrimary": is_primary,
                "confidenceScore": confidence_score,
                "relevanceReason": relevance_reason,
            },
        )
        return ArticleGameResponse.model_validate(data)

    def get_existing_topic_integration(
        self,
        article_id: int,
    ) -> TopicIntegrationResponse | None:
        data = self._request(
            "GET",
            f"/api/internal/topics/by-article/{article_id}",
        )
        if data is None:
            return None
        return TopicIntegrationResponse.model_validate(data)

    def get_topic_candidates(
        self,
        article_id: int,
        window_hours: int,
        limit: int,
        allow_recent_fallback: bool = True,
    ) -> List[TopicCandidateResponse]:
        data = self._request(
            "POST",
            "/api/internal/topics/candidates",
            json={
                "articleId": article_id,
                "windowHours": window_hours,
                "limit": limit,
                "allowRecentFallback": allow_recent_fallback,
            },
        )
        return [TopicCandidateResponse.model_validate(item) for item in data]

    def integrate_topic(
        self,
        article_id: int,
        target_topic_id: int | None,
        title: str,
        summary: str,
        category: NewsCategory,
        importance_score: int | None = None,
        why_important: str | None = None,
    ) -> TopicIntegrationResponse:
        data = self._request(
            "POST",
            "/api/internal/topics/integrate",
            json={
                "articleId": article_id,
                "targetTopicId": target_topic_id,
                "title": title,
                "summary": summary,
                "category": category.value,
                "initialImportanceScore": importance_score,
                "initialWhyImportant": why_important,
            },
        )
        return TopicIntegrationResponse.model_validate(data)

    def get_topic_analysis_context(self, topic_id: int) -> TopicAnalysisContextResponse:
        data = self._request(
            "GET",
            f"/api/internal/topics/{topic_id}/analysis-context",
        )
        return TopicAnalysisContextResponse.model_validate(data)

    def update_topic_analysis(
        self,
        topic_id: int,
        title: str,
        summary: str,
        category: NewsCategory,
        importance_score: int,
        why_important: str,
    ) -> TopicStoredResponse:
        data = self._request(
            "PUT",
            f"/api/internal/topics/{topic_id}/analysis",
            json={
                "title": title,
                "summary": summary,
                "category": category.value,
                "importanceScore": importance_score,
                "whyImportant": why_important,
            },
        )
        return TopicStoredResponse.model_validate(data)

    def _request(self, method: str, path: str, **kwargs: Any) -> Any:
        url = f"{self.base_url}{path}"
        try:
            with httpx.Client(timeout=self.timeout) as client:
                response = client.request(method, url, **kwargs)
                response.raise_for_status()
                payload = response.json()
        except httpx.HTTPStatusError as exc:
            body = exc.response.text[:500]
            logger.error(
                "[NewsClient] HTTP 오류 - method=%s url=%s status=%s body=%s",
                method,
                url,
                exc.response.status_code,
                body,
            )
            raise NewsServiceError(
                f"News Service HTTP {exc.response.status_code}: {body}",
                status_code=exc.response.status_code,
            ) from exc
        except httpx.HTTPError as exc:
            logger.error("[NewsClient] 통신 오류 - method=%s url=%s error=%s", method, url, exc)
            raise NewsServiceError(f"News Service 통신 실패: {exc}") from exc

        if not payload.get("success", False):
            raise NewsServiceError(
                f"News Service 응답 실패: {payload.get('message', 'unknown error')}"
            )

        return payload.get("data")


news_client = NewsServiceClient()
