from app.model.schemas import ArticleAnalysisResult, ArticleEntityType


def _base(**overrides):
    data = {
        "entityType": "MIXED",
        "gameNewsRelevant": True,
        "summary": "테스트 요약입니다.",
        "category": "UPDATE",
        "keywords": ["테스트", "게임", "업데이트"],
        "relatedGames": [],
        "relatedFranchises": [],
    }
    data.update(overrides)
    return ArticleAnalysisResult.model_validate(data)


def _game(name="Control", entity_type="SPECIFIC_GAME"):
    return {
        "name": name,
        "entityType": entity_type,
        "isPrimary": True,
        "confidenceScore": 0.95,
        "reason": "기사에서 특정 작품을 직접 다룬다.",
    }


def _franchise(name="Mass Effect", entity_type="FRANCHISE"):
    return {
        "name": name,
        "entityType": entity_type,
        "isPrimary": True,
        "confidenceScore": 0.95,
        "reason": "기사에서 프랜차이즈 범위를 직접 다룬다.",
    }


def test_irrelevant_article_is_forced_to_none_without_entities():
    result = _base(
        entityType="MIXED",
        gameNewsRelevant=False,
        relatedGames=[_game()],
        relatedFranchises=[_franchise()],
    )

    assert result.entityType == ArticleEntityType.NONE
    assert result.relatedGames == []
    assert result.relatedFranchises == []


def test_none_scope_drops_llm_hallucinated_entities():
    result = _base(
        entityType="NONE",
        relatedGames=[_game()],
        relatedFranchises=[_franchise()],
    )

    assert result.relatedGames == []
    assert result.relatedFranchises == []


def test_specific_game_scope_keeps_only_specific_games():
    result = _base(
        entityType="SPECIFIC_GAME",
        relatedGames=[_game(), _game("Wrong", "FRANCHISE")],
        relatedFranchises=[_franchise()],
    )

    assert [game.name for game in result.relatedGames] == ["Control"]
    assert result.relatedFranchises == []


def test_franchise_scope_drops_games_and_keeps_franchise_types():
    result = _base(
        entityType="FRANCHISE",
        relatedGames=[_game()],
        relatedFranchises=[_franchise(), _franchise("Wrong", "SPECIFIC_GAME")],
    )

    assert result.relatedGames == []
    assert [franchise.name for franchise in result.relatedFranchises] == ["Mass Effect"]


def test_mixed_scope_filters_each_relation_list_by_kind():
    result = _base(
        entityType="MIXED",
        relatedGames=[_game(), _game("Wrong", "FRANCHISE")],
        relatedFranchises=[_franchise(), _franchise("Next Mass Effect", "UNNAMED_ENTRY")],
    )

    assert [game.name for game in result.relatedGames] == ["Control"]
    assert [franchise.entityType for franchise in result.relatedFranchises] == [
        ArticleEntityType.FRANCHISE,
        ArticleEntityType.UNNAMED_ENTRY,
    ]
