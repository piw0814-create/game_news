import sys
import types
import unittest
from types import SimpleNamespace
from unittest.mock import patch

# 테스트 환경에는 OpenAI SDK가 없을 수 있으므로 import 경로만 만족시키는 최소 stub을 둔다.
openai_stub = types.ModuleType("openai")
openai_stub.OpenAI = object
sys.modules.setdefault("openai", openai_stub)

from app.service.article_analysis_service import ArticleAnalysisService


class FakeNewsClient:
    def __init__(self):
        self.links = []
        self.resolve_calls = []
        self.franchise_links = []

    def get_article_games(self, article_id):
        return []

    def link_game(self, **kwargs):
        self.links.append(kwargs)
        return SimpleNamespace(**kwargs)

    def get_article_franchises(self, article_id):
        return []

    def link_franchise(self, **kwargs):
        self.franchise_links.append(kwargs)
        return SimpleNamespace(**kwargs)

    def resolve_or_create_ai_game(self, **kwargs):
        self.resolve_calls.append(kwargs)
        return SimpleNamespace(
            created=True,
            game=SimpleNamespace(
                id=99,
                name=kwargs["name"],
                displayName=None,
                aliases=[],
                reviewStatus=kwargs["review_status"],
            ),
        )


class ArticleGameRelevanceTest(unittest.TestCase):
    def setUp(self):
        self.service = ArticleAnalysisService()
        self.control_game = SimpleNamespace(
            id=1,
            name="Control",
            displayName=None,
            aliases=[],
        )

    def test_low_confidence_existing_game_is_not_linked(self):
        fake_client = FakeNewsClient()
        analysis = SimpleNamespace(
            relatedGames=[
                SimpleNamespace(
                    name="Control",
                    isPrimary=True,
                    confidenceScore=0.35,
                    reason="기사에서 control이 일반적인 의미로만 사용됨",
                )
            ]
        )

        with patch("app.service.article_analysis_service.news_client", fake_client):
            self.service._link_games(10, analysis, [self.control_game])

        self.assertEqual([], fake_client.links)
        self.assertEqual([], fake_client.resolve_calls)

    def test_high_confidence_existing_game_keeps_relevance_reason(self):
        fake_client = FakeNewsClient()
        reason = "기사 제목에서 Control이 게임명으로 언급되고 Remedy 업데이트 문맥이 확인됨"
        analysis = SimpleNamespace(
            relatedGames=[
                SimpleNamespace(
                    name="Control",
                    isPrimary=True,
                    confidenceScore=0.95,
                    reason=reason,
                )
            ]
        )

        with patch("app.service.article_analysis_service.news_client", fake_client):
            self.service._link_games(11, analysis, [self.control_game])

        self.assertEqual(1, len(fake_client.links))
        self.assertEqual(1, fake_client.links[0]["game_id"])
        self.assertEqual(0.95, fake_client.links[0]["confidence_score"])
        self.assertEqual(reason, fake_client.links[0]["relevance_reason"])


    def test_high_confidence_unregistered_game_is_auto_confirmed(self):
        fake_client = FakeNewsClient()
        related_game = SimpleNamespace(
            name="High Confidence Game",
            isPrimary=True,
            confidenceScore=0.95,
            reason="기사에서 특정 게임을 명확하게 직접 다룸",
        )

        with patch("app.service.article_analysis_service.news_client", fake_client):
            result = self.service._resolve_unregistered_game(30, related_game)

        self.assertIsNotNone(result)
        self.assertEqual(1, len(fake_client.resolve_calls))
        self.assertEqual("CONFIRMED", fake_client.resolve_calls[0]["review_status"])

    def test_medium_confidence_unregistered_game_requires_review(self):
        fake_client = FakeNewsClient()
        related_game = SimpleNamespace(
            name="Review Game",
            isPrimary=True,
            confidenceScore=0.78,
            reason="게임은 식별되지만 신규 기준정보 등록은 검토가 필요함",
        )

        with patch("app.service.article_analysis_service.news_client", fake_client):
            result = self.service._resolve_unregistered_game(31, related_game)

        self.assertIsNotNone(result)
        self.assertEqual(1, len(fake_client.resolve_calls))
        self.assertEqual("REVIEW_REQUIRED", fake_client.resolve_calls[0]["review_status"])

    def test_known_franchise_is_linked_only_with_sufficient_confidence(self):
        fake_client = FakeNewsClient()
        resident_evil = SimpleNamespace(id=7, name="Resident Evil", displayName=None, aliases=[])
        analysis = SimpleNamespace(relatedFranchises=[SimpleNamespace(
            name="Resident Evil", isPrimary=True, confidenceScore=0.94,
            reason="개별 작품이 아니라 Resident Evil 프랜차이즈 전체 누적 판매량을 다룸",
        )])

        with patch("app.service.article_analysis_service.news_client", fake_client):
            self.service._link_franchises(20, analysis, [resident_evil])

        self.assertEqual(1, len(fake_client.franchise_links))
        self.assertEqual(7, fake_client.franchise_links[0]["franchise_id"])
        self.assertEqual(0.94, fake_client.franchise_links[0]["confidence_score"])

    def test_unknown_or_low_confidence_franchise_is_not_linked(self):
        fake_client = FakeNewsClient()
        resident_evil = SimpleNamespace(id=7, name="Resident Evil", displayName=None, aliases=[])
        low = SimpleNamespace(relatedFranchises=[SimpleNamespace(
            name="Resident Evil", isPrimary=True, confidenceScore=0.42, reason="프랜차이즈 범위인지 불명확",
        )])
        unknown = SimpleNamespace(relatedFranchises=[SimpleNamespace(
            name="Unknown Franchise", isPrimary=True, confidenceScore=0.99, reason="기사에서 프랜차이즈 전체를 언급",
        )])

        with patch("app.service.article_analysis_service.news_client", fake_client):
            self.service._link_franchises(21, low, [resident_evil])
            self.service._link_franchises(22, unknown, [resident_evil])

        self.assertEqual([], fake_client.franchise_links)

    def test_unnamed_entry_never_creates_game_even_with_high_confidence(self):
        fake_client = FakeNewsClient()
        related_game = SimpleNamespace(
            name="Mass Effect",
            entityType="UNNAMED_ENTRY",
            isPrimary=True,
            confidenceScore=0.98,
            reason="정식 제목이 발표되지 않은 차기 Mass Effect 작품을 다룸",
        )

        with patch("app.service.article_analysis_service.news_client", fake_client):
            result = self.service._resolve_unregistered_game(40, related_game)

        self.assertIsNone(result)
        self.assertEqual([], fake_client.resolve_calls)

    def test_unnamed_entry_article_scope_blocks_game_link(self):
        fake_client = FakeNewsClient()
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

        with patch("app.service.article_analysis_service.news_client", fake_client):
            self.service._link_games(41, analysis, [])

        self.assertEqual([], fake_client.links)
        self.assertEqual([], fake_client.resolve_calls)

    def test_unnamed_entry_links_known_franchise(self):
        fake_client = FakeNewsClient()
        mass_effect = SimpleNamespace(id=9, name="Mass Effect", displayName=None, aliases=[])
        analysis = SimpleNamespace(
            entityType="UNNAMED_ENTRY",
            relatedFranchises=[SimpleNamespace(
                name="Mass Effect",
                entityType="UNNAMED_ENTRY",
                isPrimary=True,
                confidenceScore=0.98,
                reason="정식 작품명이 특정되지 않은 차기 Mass Effect 작품을 다룸",
            )],
        )

        with patch("app.service.article_analysis_service.news_client", fake_client):
            self.service._link_franchises(42, analysis, [mass_effect])

        self.assertEqual(1, len(fake_client.franchise_links))
        self.assertEqual(9, fake_client.franchise_links[0]["franchise_id"])
        self.assertEqual(0.98, fake_client.franchise_links[0]["confidence_score"])

    def test_specific_game_scope_does_not_link_franchise(self):
        fake_client = FakeNewsClient()
        mass_effect = SimpleNamespace(id=9, name="Mass Effect", displayName=None, aliases=[])
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

        with patch("app.service.article_analysis_service.news_client", fake_client):
            self.service._link_franchises(43, analysis, [mass_effect])

        self.assertEqual([], fake_client.franchise_links)


if __name__ == "__main__":
    unittest.main()
