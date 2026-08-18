from datetime import datetime
from enum import Enum
from typing import Any, List, Optional

from pydantic import BaseModel, Field


class NewsCategory(str, Enum):
    RELEASE = "RELEASE"
    UPDATE = "UPDATE"
    INDUSTRY = "INDUSTRY"
    ESPORTS = "ESPORTS"
    EVENT = "EVENT"
    CONTROVERSY = "CONTROVERSY"
    OTHER = "OTHER"


class AnalysisStatus(str, Enum):
    PENDING = "PENDING"
    PROCESSING = "PROCESSING"
    COMPLETED = "COMPLETED"
    FAILED = "FAILED"


class NewsArticleResponse(BaseModel):
    id: int
    title: str
    url: str
    sourceName: str
    sourceType: str
    publishedAt: Optional[datetime] = None
    collectedAt: datetime
    content: Optional[str] = None
    summary: Optional[str] = None
    keywords: List[str] = Field(default_factory=list)
    category: Optional[NewsCategory] = None
    analysisStatus: AnalysisStatus
    createdAt: Optional[datetime] = None
    updatedAt: Optional[datetime] = None


class GameResponse(BaseModel):
    id: int
    name: str
    publisher: Optional[str] = None
    genre: Optional[str] = None
    platform: Optional[str] = None
    imageUrl: Optional[str] = None
    createdAt: Optional[datetime] = None
    updatedAt: Optional[datetime] = None


class ArticleGameResponse(BaseModel):
    id: int
    articleId: int
    gameId: int
    gameName: str
    isPrimary: bool
    confidenceScore: Optional[float] = None
    createdAt: Optional[datetime] = None


class RelatedGameAnalysis(BaseModel):
    name: str = Field(description="기사에서 직접 다루는 게임의 공식 이름")
    isPrimary: bool = Field(description="기사의 가장 중심적인 게임인지 여부")
    confidenceScore: float = Field(
        ge=0.0,
        le=1.0,
        description="해당 게임이 기사와 관련 있다는 신뢰도",
    )


class ArticleAnalysisResult(BaseModel):
    gameNewsRelevant: bool = Field(
        description=(
            "게임 자체, 게임 산업, 게임 플랫폼/하드웨어, 또는 게임 IP 생태계와 "
            "직접 관련된 기사인지 여부"
        )
    )
    summary: str = Field(description="한국어 2~4문장의 간결한 기사 요약")
    category: NewsCategory
    keywords: List[str] = Field(
        min_length=3,
        max_length=8,
        description="기사 핵심 키워드 3~8개",
    )
    relatedGames: List[RelatedGameAnalysis] = Field(
        max_length=5,
        description="기사에서 직접 다루는 게임. 게임이 없으면 빈 배열",
    )


class TopicCandidateResponse(BaseModel):
    id: int
    title: str
    summary: Optional[str] = None
    category: Optional[NewsCategory] = None
    firstSeenAt: datetime
    lastUpdatedAt: datetime


class TopicMatchResult(BaseModel):
    sameEvent: bool = Field(description="새 기사와 후보 Topic 중 하나가 동일한 실제 사건인지 여부")
    matchedTopicId: Optional[int] = Field(
        description="sameEvent=true일 때 선택한 후보 Topic ID. 매치가 없으면 null",
    )
    confidenceScore: float = Field(
        ge=0.0,
        le=1.0,
        description="동일 사건 판단 신뢰도",
    )
    reason: str = Field(description="판단 근거를 짧게 설명")


class TopicIntegrationAction(str, Enum):
    ALREADY_LINKED = "ALREADY_LINKED"
    LINKED_EXISTING = "LINKED_EXISTING"
    CREATED_NEW = "CREATED_NEW"


class TopicIntegrationResponse(BaseModel):
    topicId: int
    action: TopicIntegrationAction


class TopicAnalysisTopicContext(BaseModel):
    id: int
    title: str
    summary: Optional[str] = None
    category: Optional[NewsCategory] = None


class TopicAnalysisGameContext(BaseModel):
    id: int
    name: str
    publisher: Optional[str] = None
    genre: Optional[str] = None
    platform: Optional[str] = None
    isPrimary: bool


class TopicAnalysisArticleContext(BaseModel):
    id: int
    title: str
    sourceName: str
    sourceType: str
    publishedAt: Optional[datetime] = None
    collectedAt: datetime
    summary: Optional[str] = None
    category: Optional[NewsCategory] = None


class TopicAnalysisContextResponse(BaseModel):
    topic: TopicAnalysisTopicContext
    games: List[TopicAnalysisGameContext] = Field(default_factory=list)
    articles: List[TopicAnalysisArticleContext] = Field(default_factory=list)


class TopicSemanticAnalysisResult(BaseModel):
    title: str = Field(min_length=1, max_length=500, description="Topic 전체를 대표하는 간결한 한국어 제목")
    summary: str = Field(min_length=1, description="중복을 제거한 한국어 2~4문장 Topic 통합 요약")
    category: NewsCategory
    semanticImportanceScore: int = Field(
        ge=0,
        le=100,
        description="기사 수/출처 가중치를 제외한 사건 자체의 의미 중요도",
    )
    whyImportant: str = Field(min_length=1, description="사건의 영향이나 결과를 설명하는 한국어 1~2문장")


class TopicStoredResponse(BaseModel):
    id: int
    title: str
    summary: Optional[str] = None
    whyImportant: Optional[str] = None
    category: Optional[NewsCategory] = None
    importanceScore: Optional[int] = None
    firstSeenAt: datetime
    lastUpdatedAt: datetime
    createdAt: Optional[datetime] = None
    updatedAt: Optional[datetime] = None


class TopicReanalysisResponse(BaseModel):
    topicId: int
    totalArticleCount: int
    analyzedArticleCount: int
    semanticImportanceScore: int
    officialBonus: int
    sourceBonus: int
    communityPenalty: int
    importanceScore: int
    title: str
    summary: str
    category: NewsCategory
    whyImportant: str


class ApiResponse(BaseModel):
    success: bool
    message: str
    data: Optional[Any] = None
