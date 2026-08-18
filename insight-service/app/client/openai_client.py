import logging
from typing import List

from openai import OpenAI

from app.config.settings import settings
from app.model.schemas import ArticleAnalysisResult, GameResponse, NewsArticleResponse

logger = logging.getLogger(__name__)


class OpenAIArticleAnalyzer:
    """OpenAI Structured Outputs를 사용해 기사 하나를 분석한다."""

    def __init__(self):
        self._client: OpenAI | None = None

    def analyze(
        self,
        article: NewsArticleResponse,
        known_games: List[GameResponse],
    ) -> ArticleAnalysisResult:
        client = self._get_client()
        prompt = self._build_prompt(article, known_games)

        logger.info(
            "[OpenAI] 기사 분석 시작 - articleId=%s model=%s",
            article.id,
            settings.openai_model,
        )

        response = client.responses.parse(
            model=settings.openai_model,
            input=[
                {
                    "role": "system",
                    "content": (
                        "You analyze game-news articles for a Korean game intelligence feed. "
                        "Return only the requested structured result. "
                        "Do not perform Topic grouping, importance scoring, or why-important analysis. "
                        "Use only facts supported by the supplied article."
                    ),
                },
                {"role": "user", "content": prompt},
            ],
            text_format=ArticleAnalysisResult,
            max_output_tokens=settings.openai_max_output_tokens,
        )

        result = response.output_parsed
        if result is None:
            raise RuntimeError("OpenAI Structured Output을 파싱하지 못했습니다")

        logger.info(
            "[OpenAI] 기사 분석 완료 - articleId=%s relevant=%s category=%s games=%s keywords=%s",
            article.id,
            result.gameNewsRelevant,
            result.category.value,
            len(result.relatedGames),
            len(result.keywords),
        )
        return result

    def _get_client(self) -> OpenAI:
        if not settings.openai_api_key.strip():
            raise RuntimeError("OPENAI_API_KEY가 설정되지 않았습니다")

        if self._client is None:
            self._client = OpenAI(api_key=settings.openai_api_key)
        return self._client

    def _build_prompt(
        self,
        article: NewsArticleResponse,
        known_games: List[GameResponse],
    ) -> str:
        content = (article.content or "").strip()
        content = content[: settings.openai_max_content_chars]

        game_names = [
            game.name
            for game in known_games[: settings.openai_known_games_limit]
        ]
        known_game_text = "\n".join(f"- {name}" for name in game_names) or "- (none)"

        return f"""
Analyze this single game-news article.

Rules:
- gameNewsRelevant: true when the article is directly relevant to games or the broader game-IP ecosystem.
- Treat these as relevant: games, DLC/updates/patches, releases/delays/sequels, esports, game companies/developers/publishers, game industry/business, game platforms/services, gaming hardware, and game-IP extensions such as merchandise, figures, apparel, limited editions, branded collaborations, food collaborations, pop-up stores/events, movies, animation, or other licensed media based on a game IP.
- Treat as irrelevant only when the article has no direct connection to a game, game company/industry, gaming platform/hardware, or a game IP ecosystem. For example, a general MCU/X-Men movie article with no game connection is irrelevant.
- Do not reject an article merely because it is about merchandise, food, collectibles, film, animation, or another non-game product when the subject is directly tied to a game IP.
- If gameNewsRelevant is false, relatedGames must be empty.
- summary: write a concise Korean summary in 2 to 4 sentences.
- category: choose exactly one of RELEASE, UPDATE, INDUSTRY, ESPORTS, EVENT, CONTROVERSY, OTHER.
- keywords: return 3 to 8 useful search/matching keywords. Avoid generic words such as game/news/article.
- relatedGames: include only games directly discussed by the article, maximum 5.
- If a related game exists in Known games below, use that exact stored name.
- If the article is industry-level news with no specific game, relatedGames must be empty.
- Mark only the main game as isPrimary=true. If there is no clear main game, all may be false.
- confidenceScore must be between 0 and 1.
- Do not invent facts not contained in the article.

Known games:
{known_game_text}

Article:
Title: {article.title}
Source: {article.sourceName}
Published at: {article.publishedAt or "unknown"}
Content:
{content or "(no body text; analyze from title only)"}
""".strip()


openai_article_analyzer = OpenAIArticleAnalyzer()
