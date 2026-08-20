import sys
import types
import unittest
from types import SimpleNamespace
from unittest.mock import patch

openai_stub = types.ModuleType("openai")
openai_stub.OpenAI = object
sys.modules.setdefault("openai", openai_stub)

from app.model.schemas import EntityResolutionOutcome
from app.service.article_analysis_service import ArticleAnalysisService


class FakeNewsClient:
    def __init__(self):
        self.game_resolve_calls = []
        self.franchise_resolve_calls = []
        self.game_outcome = EntityResolutionOutcome.AUTO_LINKED
        self.franchise_outcome = EntityResolutionOutcome.AUTO_LINKED

    def resolve_game_entity(self, **kwargs):
        self.game_resolve_calls.append(kwargs)
        return SimpleNamespace(
            outcome=self.game_outcome,
            gameId=99 if self.game_outcome == EntityResolutionOutcome.AUTO_LINKED else None,
            reviewId=501 if self.game_outcome == EntityResolutionOutcome.REVIEW_REQUIRED else None,
        )

    def resolve_franchise_entity(self, **kwargs):
        self.franchise_resolve_calls.append(kwargs)
        return SimpleNamespace(
            outcome=self.franchise_outcome,
            franchiseId=7 if self.franchise_outcome == EntityResolutionOutcome.AUTO_LINKED else None,
            reviewId=601 if self.franchise_outcome == EntityResolutionOutcome.REVIEW_REQUIRED else None,
        )


class ArticleEntityResolutionTest(unittest.TestCase):
    def setUp(self):
        self.service = ArticleAnalysisService()

    def test_specific_game_is_delegated_to_igdb_resolution(self):
        fake = FakeNewsClient()
        reason = "기사에서 특정 게임을 명확하게 직접 다룸"
        analysis = SimpleNamespace(
            entityType="SPECIFIC_GAME",
            relatedGames=[SimpleNamespace(
                name="Control",
                entityType="SPECIFIC_GAME",
                isPrimary=True,
                confidenceScore=0.95,
                reason=reason,
            )],
        )

        with patch("app.service.article_analysis_service.news_client", fake):
            self.service._link_games(11, analysis, [])

        self.assertEqual(1, len(fake.game_resolve_calls))
        call = fake.game_resolve_calls[0]
        self.assertEqual("Control", call["detected_name"])
        self.assertEqual(0.95, call["confidence_score"])
        self.assertEqual(reason, call["reason"])

    def test_ambiguous_game_can_be_returned_as_review_without_failing_analysis(self):
        fake = FakeNewsClient()
        fake.game_outcome = EntityResolutionOutcome.REVIEW_REQUIRED
        analysis = SimpleNamespace(
            entityType="SPECIFIC_GAME",
            relatedGames=[SimpleNamespace(
                name="Mass Effect 3",
                entityType="SPECIFIC_GAME",
                isPrimary=True,
                confidenceScore=0.99,
                reason="동일 이름 IGDB 후보가 여러 개",
            )],
        )

        with patch("app.service.article_analysis_service.news_client", fake):
            self.service._link_games(50, analysis, [])

        self.assertEqual(1, len(fake.game_resolve_calls))

    def test_unnamed_entry_article_scope_blocks_game_resolution(self):
        fake = FakeNewsClient()
        analysis = SimpleNamespace(
            entityType="UNNAMED_ENTRY",
            relatedGames=[SimpleNamespace(
                name="Mass Effect",
                entityType="SPECIFIC_GAME",
                isPrimary=True,
                confidenceScore=0.98,
                reason="모델이 잘못 특정 게임으로 반환한 경우를 방어",
            )],
        )

        with patch("app.service.article_analysis_service.news_client", fake):
            self.service._link_games(41, analysis, [])

        self.assertEqual([], fake.game_resolve_calls)

    def test_franchise_is_delegated_to_igdb_resolution(self):
        fake = FakeNewsClient()
        analysis = SimpleNamespace(
            entityType="FRANCHISE",
            relatedFranchises=[SimpleNamespace(
                name="Resident Evil",
                entityType="FRANCHISE",
                isPrimary=True,
                confidenceScore=0.94,
                reason="개별 작품이 아니라 프랜차이즈 전체 누적 판매량을 다룸",
            )],
        )

        with patch("app.service.article_analysis_service.news_client", fake):
            self.service._link_franchises(20, analysis, [])

        self.assertEqual(1, len(fake.franchise_resolve_calls))
        self.assertEqual("Resident Evil", fake.franchise_resolve_calls[0]["detected_name"])

    def test_unnamed_entry_resolves_as_franchise_not_game(self):
        fake = FakeNewsClient()
        analysis = SimpleNamespace(
            entityType="UNNAMED_ENTRY",
            relatedGames=[],
            relatedFranchises=[SimpleNamespace(
                name="Mass Effect",
                entityType="UNNAMED_ENTRY",
                isPrimary=True,
                confidenceScore=0.98,
                reason="정식 작품명이 특정되지 않은 차기 Mass Effect 작품",
            )],
        )

        with patch("app.service.article_analysis_service.news_client", fake):
            self.service._link_games(42, analysis, [])
            self.service._link_franchises(42, analysis, [])

        self.assertEqual([], fake.game_resolve_calls)
        self.assertEqual(1, len(fake.franchise_resolve_calls))
        self.assertEqual("UNNAMED_ENTRY", fake.franchise_resolve_calls[0]["entity_type"])

    def test_specific_game_scope_blocks_franchise_resolution(self):
        fake = FakeNewsClient()
        analysis = SimpleNamespace(
            entityType="SPECIFIC_GAME",
            relatedFranchises=[SimpleNamespace(
                name="Mass Effect",
                entityType="FRANCHISE",
                isPrimary=True,
                confidenceScore=0.99,
                reason="잘못 채워진 프랜차이즈 값을 방어",
            )],
        )

        with patch("app.service.article_analysis_service.news_client", fake):
            self.service._link_franchises(43, analysis, [])

        self.assertEqual([], fake.franchise_resolve_calls)


if __name__ == "__main__":
    unittest.main()
