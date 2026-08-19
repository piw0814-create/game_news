import logging
from typing import Any, List

import httpx

from app.config.settings import settings
from app.model.schemas import (
    AnalysisStatus,
    ArticleGameResponse,
    GameResolveOrCreateResponse,
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

    def resolve_or_create_ai_game(
        self,
        name: str,
        review_status: str,
        registration_confidence: float,
        source_article_id: int,
    ) -> GameResolveOrCreateResponse:
        data = self._request(
            "POST",
            "/api/internal/games/resolve-or-create",
            json={
                "name": name,
                "reviewStatus": review_status,
                "registrationConfidence": registration_confidence,
                "sourceArticleId": source_article_id,
            },
        )
        return GameResolveOrCreateResponse.model_validate(data)

    def get_recovery_candidates(
        self,
        limit: int,
        processing_stale_minutes: int,
    ) -> List[NewsArticleResponse]:
        data = self._request(
            "GET",
            "/api/internal/news/recovery-candidates",
            params={
                "limit": limit,
                "processingStaleMinutes": processing_stale_minutes,
            },
        )
        return [NewsArticleResponse.model_validate(item) for item in data]

    def get_article_games(self, article_id: int) -> List[ArticleGameResponse]:
        data = self._request("GET", f"/api/news/{article_id}/games")
        return [ArticleGameResponse.model_validate(item) for item in data]

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

    def update_analysis(
        self,
        article_id: int,
        summary: str,
        category: NewsCategory,
        keywords: List[str],
    ) -> NewsArticleResponse:
        data = self._request(
            "PUT",
            f"/api/news/{article_id}/analysis",
            json={
                "summary": summary,
                "category": category.value,
                "keywords": keywords,
            },
        )
        return NewsArticleResponse.model_validate(data)

    def link_game(
        self,
        article_id: int,
        game_id: int,
        is_primary: bool,
        confidence_score: float,
    ) -> ArticleGameResponse:
        data = self._request(
            "POST",
            f"/api/news/{article_id}/games",
            json={
                "gameId": game_id,
                "isPrimary": is_primary,
                "confidenceScore": confidence_score,
            },
        )
        return ArticleGameResponse.model_validate(data)

    def get_topic_candidates(
        self,
        article_id: int,
        window_hours: int,
        limit: int,
    ) -> List[TopicCandidateResponse]:
        data = self._request(
            "POST",
            "/api/internal/topics/candidates",
            json={
                "articleId": article_id,
                "windowHours": window_hours,
                "limit": limit,
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
