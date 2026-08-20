import logging
from typing import List

from openai import OpenAI
from pydantic import ValidationError

from app.config.settings import settings
from app.model.schemas import (
    ArticleAnalysisResult,
    FranchiseResponse,
    GameResponse,
    NewsArticleResponse,
)

logger = logging.getLogger(__name__)


class StructuredOutputParseError(RuntimeError):
    """Structured Output이 비어 있거나 파싱되지 않은 경우의 재시도 대상 오류."""


class OpenAIArticleAnalyzer:
    """OpenAI Structured Outputs를 사용해 기사 하나를 분석한다."""

    def __init__(self):
        self._client: OpenAI | None = None

    def analyze(
        self,
        article: NewsArticleResponse,
        known_games: List[GameResponse],
        known_franchises: List[FranchiseResponse],
    ) -> ArticleAnalysisResult:
        client = self._get_client()
        prompt = self._build_prompt(article, known_games, known_franchises)

        logger.info(
            "[OpenAI] 기사 분석 시작 - articleId=%s model=%s",
            article.id,
            settings.openai_model,
        )

        result = None
        for attempt in range(2):
            try:
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
                    raise StructuredOutputParseError(
                        "OpenAI Structured Output을 파싱하지 못했습니다"
                    )
                break
            except (ValidationError, StructuredOutputParseError) as exc:
                if attempt == 0:
                    logger.warning(
                        "[OpenAI] Structured Output 파싱 실패 - articleId=%s retry=1/1 error=%s",
                        article.id,
                        exc,
                    )
                    continue

                logger.error(
                    "[OpenAI] Structured Output 파싱 재시도 실패 - articleId=%s error=%s",
                    article.id,
                    exc,
                )
                raise

        if result is None:
            raise StructuredOutputParseError("OpenAI Structured Output을 파싱하지 못했습니다")

        logger.info(
            "[OpenAI] 기사 분석 완료 - articleId=%s relevant=%s entityType=%s category=%s games=%s franchises=%s keywords=%s",
            article.id,
            result.gameNewsRelevant,
            result.entityType.value,
            result.category.value,
            len(result.relatedGames),
            len(result.relatedFranchises),
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
        known_franchises: List[FranchiseResponse],
    ) -> str:
        content = (article.content or "").strip()
        content = content[: settings.openai_max_content_chars]

        game_lines = []
        for game in known_games[: settings.openai_known_games_limit]:
            display = game.displayName or "-"
            aliases = ", ".join(game.aliases) if game.aliases else "-"
            developer = game.developer or "-"
            publisher = game.publisher or "-"
            game_lines.append(
                f"- canonical: {game.name} | display: {display} | aliases: {aliases} "
                f"| developer: {developer} | publisher: {publisher}"
            )
        known_game_text = "\n".join(game_lines) or "- (none)"

        franchise_lines = []
        for franchise in known_franchises[: settings.openai_known_franchises_limit]:
            display = franchise.displayName or "-"
            aliases = ", ".join(franchise.aliases) if franchise.aliases else "-"
            franchise_lines.append(
                f"- canonical: {franchise.name} | display: {display} | aliases: {aliases}"
            )
        known_franchise_text = "\n".join(franchise_lines) or "- (none)"

        return f"""
Analyze this single game-news article.

Rules:
- gameNewsRelevant: true when the article is directly relevant to games or the broader game-IP ecosystem.
- Treat these as relevant: games, DLC/updates/patches, releases/delays/sequels, esports, game companies/developers/publishers, game industry/business, game platforms/services, gaming hardware, and game-IP extensions such as merchandise, figures, apparel, limited editions, branded collaborations, food collaborations, pop-up stores/events, movies, animation, or other licensed media based on a game IP.
- Treat as irrelevant only when the article has no direct connection to a game, game company/industry, gaming platform/hardware, or a game IP ecosystem. For example, a general MCU/X-Men movie article with no game connection is irrelevant.
- Do not reject an article merely because it is about merchandise, food, collectibles, film, animation, or another non-game product when the subject is directly tied to a game IP.
- If gameNewsRelevant is false, entityType must be NONE and both relatedGames and relatedFranchises must be empty.
- First decide entityType before extracting names:
  - SPECIFIC_GAME: the article identifies one or more actual game entries/titles clearly enough to distinguish them from the franchise.
  - FRANCHISE: the article is primarily about the IP/series/franchise as a whole, not a particular entry.
  - UNNAMED_ENTRY: the article is about a future/new/next entry in a franchise, but the official/specific game title is not established by the supplied article.
  - MIXED: the article centrally discusses both one or more specific games and franchise-wide information.
  - NONE: no specific game/franchise entity should be linked (for example industry-only news or an irrelevant/common-word mention).
- High confidence does NOT override entity type. Being 98% sure that an article is about the Mass Effect IP does not mean the specific 2007 game Mass Effect was identified.
- summary: write a concise Korean summary in 2 to 4 sentences.
- category: choose exactly one of RELEASE, UPDATE, INDUSTRY, ESPORTS, EVENT, CONTROVERSY, OTHER.
- keywords: return 3 to 8 useful search/matching keywords. Avoid generic words such as game/news/article.
- relatedGames: include only SPECIFIC_GAME entries directly discussed by the article, maximum 5. Every relatedGames item must use entityType=SPECIFIC_GAME.
- Never use a franchise name as a substitute game title when the article means "the next/new/upcoming [franchise] game" and the actual entry title is not identified.
- For an unnamed future entry, do NOT create/return a Game named only after the franchise. Instead set the article entityType to UNNAMED_ENTRY and return the explicitly identified franchise in relatedFranchises with entityType=UNNAMED_ENTRY. Known franchises are hints, not a whitelist; the backend verifies unknown names against IGDB before linking.
- A matching word or phrase alone is NOT enough to identify a game. Confirm from article context that the subject is actually that game.
- Be especially conservative with ambiguous/common-word game titles or short aliases such as Control, Inside, Rust, Split, Marathon, Deadlock, GTA, CS, or NTE.
- Example: "The company lost control of development costs." -> do NOT return the game Control.
- Example: "Remedy released a new update for Control." -> Control is a valid related game.
- Example: "Rust continues to affect the server." -> do NOT return the game Rust unless the article context is clearly about Facepunch's game.
- Example: "Facepunch announced a Rust gameplay update." -> Rust is a valid related game.
- If the article refers to a Known game by its canonical name, display name, or any alias, return the canonical name from that Known game entry. Use developer/publisher context when it helps disambiguate an ambiguous title.
- A specific game explicitly named by the article may be returned even when it is not in Known games. Do not guess from vague wording; the backend will verify the returned name against IGDB and route ambiguous matches to admin review.
- If the article is industry-level news with no specific game, relatedGames must be empty.
- relatedFranchises is separate from relatedGames. Use entityType=FRANCHISE when the article directly discusses an IP/franchise as a whole, and entityType=UNNAMED_ENTRY when it discusses a future/new entry whose specific title is not identified.
- Do NOT infer a specific sequel/entry when the article only refers to a franchise/series name or says "next", "new", "future", "upcoming", "new entry", "next installment", or equivalent wording without a specific official title.
- Do NOT add a franchise merely because a related game belongs to it. A specific-game-only article should normally have relatedFranchises=[] even if that game's franchise is known.
- If a franchise-level claim is itself central to the article (for example franchise-wide sales, anniversary, brand strategy, cross-entry licensing), return that Known franchise using its canonical name.
- Known franchises are identity hints, not a whitelist. If the article clearly names a franchise/IP that is not in Known franchises, you may return that explicit name; never invent a franchise that the article does not support. The backend performs IGDB verification and sends ambiguous matches to admin review.
- If the article explicitly and centrally discusses both franchise-wide information and one or more specific games, relatedGames and relatedFranchises may both be populated.
- Example: "The GTA franchise surpassed 500 million sales." -> entityType=FRANCHISE; relatedGames=[]; relatedFranchises may contain Grand Theft Auto with entityType=FRANCHISE. Use a Known canonical identity when available; otherwise use only the explicit article-supported franchise name.
- Example: "Rockstar reveals new GTA VI details." -> entityType=SPECIFIC_GAME; relatedGames may contain Grand Theft Auto VI with entityType=SPECIFIC_GAME; relatedFranchises=[] unless the article separately discusses the franchise as a whole.
- Example: "The next Mass Effect game is still years away." -> entityType=UNNAMED_ENTRY; relatedGames=[]; relatedFranchises=[Mass Effect with entityType=UNNAMED_ENTRY]. Do NOT return/create the specific 2007 game Mass Effect merely because that franchise name appears.
- Example: "BioWare is developing the next Mass Effect, but no official title has been announced." -> UNNAMED_ENTRY, not the 2007 game Mass Effect.
- Example: "Mass Effect 2 receives a new update." -> SPECIFIC_GAME and Mass Effect 2 may be returned as a related game.
- Example: "Resident Evil franchise celebrates its anniversary." -> do not guess Resident Evil 4/Village; use the Resident Evil franchise if known.
- Example: "The original 1996 Resident Evil receives a new port." -> this is about the specific original game, not automatically the whole franchise.
- Franchise confidenceScore/reason must reflect evidence for franchise-wide or unnamed-entry scope, not simple name similarity. For UNNAMED_ENTRY, explicitly mention that the specific game title is not identified.
- When entityType=FRANCHISE or UNNAMED_ENTRY, do not populate relatedGames unless the article separately identifies another specific game; if so, use MIXED at article level.
- Mark only the main game as isPrimary=true. If there is no clear main game, all may be false.
- confidenceScore must be between 0 and 1 and should reflect contextual certainty that the article is actually about that game, not just text similarity.
- reason: for every related game/franchise, use one concise Korean sentence (maximum 300 characters) stating only the contextual evidence. Do not cite mere substring matching as evidence.
- When uncertain whether a common word is a game title, omit it from relatedGames rather than guessing.
- Do not invent facts not contained in the article.

Known games:
{known_game_text}

Known franchises:
{known_franchise_text}

Article:
Title: {article.title}
Source: {article.sourceName}
Published at: {article.publishedAt or "unknown"}
Content:
{content or "(no body text; analyze from title only)"}
""".strip()


openai_article_analyzer = OpenAIArticleAnalyzer()
