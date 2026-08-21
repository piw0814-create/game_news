# Game Intelligence

**AI-powered Game News Intelligence Feed**

여러 출처의 게임 뉴스를 수집하고, 동일 사건을 하나의 `Topic`으로 묶은 뒤 AI가 요약·분류·중요도·의미를 분석하여 제공하는 게임 뉴스 인텔리전스 피드입니다.

## 핵심 개념

```text
NewsArticle = 외부에서 수집한 원본 기사
Topic       = 여러 기사가 가리키는 하나의 실제 사건
```

전체 처리 흐름:

```text
외부 뉴스
   ↓
Collector Service
   ↓ REST
News Service
   ↓
NewsArticle 저장
   ↓
Kafka: news.created
   ↓
Insight Service
   ↓
Article Analyzer 1회
   ↓
Game / Franchise Identity Resolution
   ├─ Local exact 우선
   ├─ 필요 시 IGDB Game / Franchise / Collection(Series) 확인
   ├─ 명확 → ArticleGame / ArticleFranchise 자동 연결
   └─ 애매 → EntityReview 관리자 검토 큐
   ↓
Topic 후보 검색
   ├─ 후보 없음 → Matcher 호출 없이 새 Topic 생성
   └─ 후보 있음 → Topic Matcher로 동일 사건 여부 판단
   ↓
기존 Topic 연결 / 새 Topic 생성
   ├─ 단일기사 새 Topic → Article Analyzer 결과 재사용 (TopicAnalyzer SKIPPED)
   └─ 기존 Topic에 새 기사 연결 → Topic Analyzer 재분석
   ↓
title / summary / category
importanceScore / whyImportant
TopicGame / TopicFranchise
   ↓
Vue Topic Feed
   ↓
관심 게임 기반 개인화 정렬
```

### 현재 AI 호출 최적화

- Article Analyzer는 신규 기사마다 기본 1회 실행합니다.
- Topic 후보가 없으면 Topic Matcher를 호출하지 않습니다.
- 단일기사로 새 Topic을 만들 때는 Article Analyzer의 초기 Topic 분석값을 재사용하여 Topic Analyzer를 추가 호출하지 않습니다.
- 기존 Topic에 새 기사가 합쳐지거나 관리자 EntityReview 처리로 Topic 관계가 실제 변경된 경우에만 Topic Analyzer 재분석을 수행합니다.
- Article Analyzer의 반복 프롬프트는 Prompt Cache를 사용하고, 실제 cache hit가 없었던 Topic Matcher/Topic Analyzer는 불필요한 cache write가 발생하지 않도록 구성했습니다.

## 서비스 구성

| 구성 | 포트 | 역할 |
|---|---:|---|
| Vue Frontend | 3000(dev) / 80(prod) | Topic Feed, 상세, 관심 게임 및 관리자 UI |
| API Gateway | 8080 | 외부 API 진입점 |
| User Service | 8081 | 사용자 정보, 로그인, RS256 JWT 발급, JWKS |
| News Service | 8082 | Game, NewsArticle, Topic 및 관계 관리 |
| Interest Service | 8083 | 사용자 관심 게임 관리 |
| Collector Service | 8084 | 외부 게임 뉴스 수집 |
| Insight Service | 8085 | AI 기사 분석, Topic 통합/재분석 |
| Eureka | 8761 | 서비스 디스커버리 |
| Kafka | 9092 | `news.created` 이벤트 전달 |
| MariaDB | 3379 → 3306 | 공용 개발 DB |

현재 DB 이름은 `game_news_db`, Docker 내부 네트워크는 `game-news-net`을 사용합니다.

## 외부 API 경계

외부 클라이언트는 API Gateway(`:8080`)를 통해 아래 사용자용 API만 접근합니다. Gateway의 서비스 자동 Discovery 라우팅은 비활성화되어 있으며 명시한 경로만 외부에 공개됩니다.

```text
POST   /api/auth/login
GET    /api/auth/jwks
POST   /api/users/register
GET    /api/users/me

GET    /api/games
GET    /api/games/{id}
GET    /api/games/{gameId}/franchises
GET    /api/games/franchise-ids?gameIds=1,2,3

GET    /api/topics
GET    /api/topics/{id}
GET    /api/topics/{topicId}/articles
GET    /api/topics/{topicId}/games
GET    /api/topics/{topicId}/franchises
GET    /api/topics/{topicId}/comments
POST   /api/topics/{topicId}/comments
DELETE /api/topics/{topicId}/comments/{commentId}
GET    /api/topics/{topicId}/likes
POST   /api/topics/{topicId}/likes
DELETE /api/topics/{topicId}/likes

GET    /api/interests/games
GET    /api/interests/game-ids
POST   /api/interests/games/{gameId}
DELETE /api/interests/games/{gameId}

# ADMIN 전용
GET    /api/admin/games
GET    /api/admin/games/{id}
PATCH  /api/admin/games/{id}
POST   /api/admin/games/{id}/merge
POST   /api/admin/games/{id}/enrichment/preview
POST   /api/admin/games/{id}/enrichment/apply

GET    /api/admin/franchises
GET    /api/admin/franchises/{id}
PATCH  /api/admin/franchises/{id}
POST   /api/admin/franchises/{id}/sync-igdb
POST   /api/admin/franchises/{id}/merge
POST   /api/admin/franchises/{id}/games
PATCH  /api/admin/franchises/{id}/games/{gameId}
DELETE /api/admin/franchises/{id}/games/{gameId}

GET    /api/admin/entity-reviews?status=PENDING
GET    /api/admin/entity-reviews/{id}
POST   /api/admin/entity-reviews/{id}/resolve
POST   /api/admin/entity-reviews/{id}/recheck
POST   /api/admin/entity-reviews/{id}/reopen
```

`/api/news/**`, `/api/collector/**`, 사용자 단건 조회, 일반 Game/Topic 쓰기 API는 Gateway에 노출하지 않습니다. Topic 생성과 Article/Game/Franchise 관계 반영은 Insight의 내부 Topic 통합 API가 담당하며 수동 Topic 쓰기 API는 두지 않습니다. Collector와 Insight는 Docker 내부 네트워크에서 News Service를 직접 호출합니다. 개발 중 운영용 API를 확인해야 할 때는 로컬 호스트의 개별 서비스 포트를 사용합니다. `/api/admin/**`는 `ADMIN` 역할만 접근할 수 있으며 Gateway와 News Service 양쪽에서 권한을 검사합니다.

### 중요도 / 개인화 점수

Topic의 기본 중요도는 AI 판단만으로 결정하지 않고 출처 신호를 함께 합산합니다.

```text
baseImportance
= AI 사건 중요도(0~50)
+ 공식 출처 보너스(0 또는 8)
+ 다중 출처 보너스(0 / 4 / 8 / 12)
- 커뮤니티 단독 패널티(0 또는 5)
```

사용자 반응 중 좋아요만 Topic 조회 시 약한 중요도 신호로 실시간 가산합니다. 댓글 수는 상호작용 지표로만 유지하며 중요도에는 반영하지 않습니다.

```text
likeBonus
= 좋아요 1점씩, 최대 20

importanceScore
= clamp(baseImportance + likeBonus, 0, 100)
```

개인화 Feed는 중요도 자체를 사용자별로 바꾸지 않고 별도 점수로 정렬합니다.

```text
personalizedScore
= importanceScore
+ 관심 게임 직접 보너스(30)
+ 관심 게임 소속 Franchise Topic 보너스(10)
+ 최신성 보너스(최대 10)
```

`personalizedScore`는 DB에 저장하지 않고 Vue에서 Feed 정렬에 사용합니다. 상단 `오늘 주요뉴스`는 개인화하지 않고 `importanceScore` 기준으로 유지합니다.

관심 Game의 Franchise ID는 `/api/games/franchise-ids` bulk 조회로 한 번에 계산해 관심 게임 수만큼 개별 API를 호출하지 않습니다.

`TopicGame`은 특정 작품 중심 Topic만, `TopicFranchise`는 IP/프랜차이즈 전체 중심 Topic만 표현합니다. 같은 프랜차이즈의 다른 특정 게임 Topic에는 Franchise 보너스를 전파하지 않습니다.

## 수집 / 자동 분석 운영

Collector는 현재 12개 RSS 소스를 지원합니다.

```text
MEDIA
PC Gamer / Destructoid / VGC / Kotaku / Gematsu / Game Informer
Game Developer / Nintendo Life / Push Square / Pure Xbox

OFFICIAL
Xbox Wire / PlayStation Blog
```

기본 자동 수집은 출처별 최대 10건씩 10분 간격으로 실행합니다. Collector 재기동 시에는 출처별 DB 최신 `publishedAt`을 기준선으로 삼아 RSS에서 최대 50건까지 확인하고, 기준선 이후 후보만 URL 중복 검사를 거쳐 저장합니다. 기준선이 없는 초기 수집은 과거 전체를 채우지 않고 일반 수집과 동일하게 최신 10건만 저장합니다.

Insight Service는 `news.created`를 기사 1건씩 처리하고, 시작 시 `PENDING`, `FAILED`, 오래된 `PROCESSING` 기사를 복구합니다. Topic 동일사건 판단 AI 응답을 파싱하지 못하는 경우에는 기사 전체를 실패시키지 않고 새 Topic 생성으로 fallback합니다.

## IGDB-first 엔티티 자동확정 / 관리자 검토

기사에서 Game/Franchise 후보를 찾는 것은 AI가 담당하고, 기준 엔티티 확정은 IGDB를 우선합니다. AI confidence 하나만으로 Game/Franchise를 본 테이블에 생성하지 않습니다.

```text
AI confidence >= 0.90 + IGDB identity가 안전하게 확정
→ 자동 확정 / metadata upsert / Article 관계 연결

동일 이름 IGDB Game이 여러 개여도 정확한 canonical name의 `Main Game`이 하나뿐이고
기사 문맥에 Remaster/Port/DLC/Expansion 같은 변형판 지시가 없으면 Main Game을 우선 자동 확정

0.60 <= confidence < 0.90
또는 동일 이름 후보 중 Main Game/변형판을 안전하게 구분할 수 없음
또는 Game/Franchise 범위가 관리자 판단을 필요로 함
→ EntityReview(PENDING)

confidence < 0.60
→ 자동 연결/검토 생성 없이 무시
```

`games`와 `franchises`는 확정된 카탈로그이고, `entity_reviews`는 아직 사람이 판단하지 않은 기사 엔티티만 저장합니다. 검토 후보에는 로컬 Game/Franchise와 IGDB 검색 후보를 함께 스냅샷으로 보존합니다. 관리자는 `/admin/reviews`에서 후보를 Game, Franchise 또는 관련 없음으로 결정할 수 있습니다.

관리자 확정 후에는 선택한 IGDB 엔티티를 upsert한 뒤 ArticleGame/ArticleFranchise를 생성하고, 이미 연결된 Topic의 TopicGame/TopicFranchise를 다시 동기화합니다. 트랜잭션 commit 뒤 Insight Service의 Topic 재분석 API를 호출하므로 title/summary/importance/whyImportant도 최신 관계를 기준으로 다시 계산합니다.

AI는 기사에서 `SPECIFIC_GAME`, `FRANCHISE`, `UNNAMED_ENTRY`, `MIXED`, `NONE`을 구분합니다. 특정 작품이 명확할 때만 Game resolver를 사용하고, 프랜차이즈 전체 뉴스나 정식 제목이 공개되지 않은 차기작은 Franchise resolver를 사용합니다. 검토 큐에는 반대 타입 후보도 함께 보여주므로 관리자가 Game ↔ Franchise 판정을 바로잡을 수 있습니다.

Game/Franchise 메타데이터는 IGDB를 기준 데이터로 보강합니다. 확정 Game은 IGDB metadata를 적용하고, 기사 문맥에서 실제로 확인된 Game/Franchise 관계만 Article/Topic에 연결합니다. Franchise가 확정되었다고 해서 해당 Franchise의 전체 Game catalog를 자동으로 동기화하지 않습니다.

Franchise identity는 IGDB의 서로 다른 두 namespace를 분리해 보존합니다.

```text
IGDB /franchises  → franchises.igdb_id
IGDB /collections → franchises.igdb_collection_id  # Series
```

`igdb_id`와 `igdb_collection_id`는 서로 다른 endpoint의 ID이므로 하나의 ID로 합치지 않습니다. Franchise resolver는 Local exact → IGDB Franchise exact → IGDB Collection exact 순으로 확인하고, 필요한 경우에만 fuzzy/Game 역참조 후보를 사용합니다. 특정 작품(`SPECIFIC_GAME`) 기사에서는 불필요한 Franchise/Collection 조회를 추가하지 않습니다.

전체 Franchise Game catalog가 실제로 필요할 때만 관리자 `/admin/franchises`의 `sync-igdb`를 수동 실행합니다. 이 동기화는 소속 Game을 일괄 조회해 `igdbId` 기준으로 기존 Game을 재사용하고 없는 Game은 `registrationSource=IGDB`, `reviewStatus=CONFIRMED`로 생성합니다. `GameFranchise`는 `MANUAL | IGDB` 관계 출처를 구분하며, 재동기화 시 IGDB가 만든 관계만 공식 목록과 비교해 stale relation을 제거하고 수동 관계는 보호합니다.

관리자 `/admin/franchises`는 Franchise별 소속 Game, 관련 Article/Topic, 유사 Franchise, IGDB 동기화 시각을 검토할 수 있고 수동 재동기화 및 Franchise 병합을 지원합니다. `/admin/games`, `/admin/franchises`, `/admin/reviews`에는 일반 텍스트 검색과 별도로 IGDB ID exact 검색을 제공합니다. `/admin/reviews`에서는 최신 후보 재조회(`recheck`), 검토 재개(`reopen`), Game/Franchise/관련 없음 수동 확정을 지원합니다.

AI가 부모 게임의 전체 부제와 Expansion/DLC명을 합쳐 추출해 IGDB 후보가 0건이 되는 경우에는 review-only 이름 축약 fallback을 사용합니다. 이 fallback 후보만으로 자동 연결하지 않고 관리자 검토 후보로만 노출합니다.

## 실행 전 준비

User Service의 JWT 서명용 RSA 키는 프로젝트 루트 `.env`에 주입합니다. 실제 private key는 Git에 커밋하지 않습니다.

```text
JWT_PRIVATE_KEY_BASE64=...
JWT_PUBLIC_KEY_BASE64=...
JWT_KEY_ID=game-news-key-1
JWT_ISSUER=game-news
JWT_ACCESS_TOKEN_TTL_SECONDS=3600
```

Insight Service는 OpenAI 설정이 필요합니다.

```bash
cp insight-service/.env.example insight-service/.env
```

`insight-service/.env`에 실제 API Key 등 필요한 값을 설정합니다. `.env`는 Git에 커밋하지 않습니다.

## 전체 백엔드 실행

프로젝트 루트에서:

```bash
docker compose build
docker compose up -d
```

로컬 개발은 기본 `docker-compose.yml`을 사용합니다. 운영형 Vue/Nginx까지 포함한 배포 직전 검증은 base compose에 `docker-compose.prod.yml`을 overlay합니다. 실제 서버 배포 절차와 필수 환경변수/체크리스트는 [`DEPLOYMENT.md`](./DEPLOYMENT.md)를 기준으로 합니다.

완전 재빌드가 필요할 때만:

```bash
docker compose build --no-cache
docker compose up -d
```

상태 확인:

```bash
docker compose ps
```

로그 확인:

```bash
docker compose logs -f
```

개별 서비스 예:

```bash
docker compose logs -f news-service
docker compose logs -f interest-service
docker compose logs -f collector-service
docker compose logs -f insight-service
```

Eureka:

```text
http://localhost:8761/
```

전체 종료:

```bash
docker compose down
```

DB 데이터까지 완전히 초기화해야 할 때만 다음을 사용합니다. 기존 데이터가 모두 삭제되므로 주의하세요.

```bash
docker compose down -v
```

기존 MariaDB 볼륨의 스키마/데이터 상태를 업그레이드할 때는 `scripts/migrate-*.sql`을 명시적으로 실행합니다. 일회성 데이터 migration/backfill을 애플리케이션 시작 코드에 남겨두지 않습니다.

## Vue 개발 서버

```bash
cd vue-frontend
npm install
npm run dev
```

브라우저:

```text
http://localhost:3000
```

프로덕션 빌드 확인:

```bash
npm run build
```

## 로컬 Spring 실행 시 DB

`user-service`, `news-service`의 기본 로컬 DB 주소는 다음과 같습니다.

```text
jdbc:mariadb://localhost:3379/game_news_db
```

Docker Compose에서는 `SPRING_DATASOURCE_URL` 환경변수로 `mariadb:3306/game_news_db`를 사용하도록 덮어씁니다.

## 인증 / 보안 참고

인증은 User Service가 직접 담당합니다. 로그인 시 BCrypt로 비밀번호를 검증하고 RS256으로 서명한 1시간 Access Token을 발급합니다. 공개키는 `GET /api/auth/jwks`로 제공하며 API Gateway가 issuer와 서명을 검증합니다.

```text
Vue → POST /api/auth/login → User Service → JWT
Vue → Authorization: Bearer <token> → API Gateway → 서명/만료 검증
API Gateway → X-User-Id / X-User-Email → 내부 서비스
```

사용자 역할은 `USER / ADMIN`으로 구분합니다. 회원가입 사용자는 항상 `USER`로 생성되며 JWT의 `role` claim에 역할이 포함됩니다. 관리자 API는 API Gateway에서 `ROLE_ADMIN`을 검사하고 News Service에서도 다시 `ADMIN` 권한을 검증합니다. 관리자 지정은 회원가입 요청으로 받을 수 없으며 운영자가 DB 등 관리 경로에서 별도로 지정합니다.

API Gateway를 외부 신뢰 경계로 사용합니다. `user-service`부터 `insight-service`까지의 개발용 포트 `8081~8085`는 Docker Compose에서 `127.0.0.1`에만 바인딩하여 같은 PC에서는 테스트할 수 있지만 외부 네트워크에서 직접 접근하지 못하도록 제한합니다. 운영 배포에서는 개별 서비스의 host port publish 자체를 제거하고 내부 네트워크에서만 접근하도록 구성하는 것이 권장됩니다.

비동기 파이프라인에서 Kafka Consumer 처리 실패는 재시도할 수 있도록 offset을 커밋하지 않지만, NewsArticle 저장 후 `news.created` Producer 발행이 최종 실패하면 해당 기사가 `PENDING`으로 남을 수 있습니다. MVP에서는 이 한계를 문서화하며 운영 수준에서는 Outbox Pattern 또는 별도 재발행 복구 작업을 추가하는 것을 권장합니다.

## 시간 정책

- Spring, FastAPI, MariaDB 컨테이너는 UTC를 기준으로 실행한다.
- JPA 감사 시각과 서버에서 생성하는 `collectedAt`, Topic 갱신 시각은 UTC로 생성한다.
- DB의 `DATETIME(6)`과 Entity의 `LocalDateTime`은 UTC 값으로 유지한다.
- 외부 API 응답에서는 UTC `LocalDateTime`을 `OffsetDateTime`으로 변환해 `Z` 또는 `+00:00` offset을 명시한다.
- Vue는 `new Date(...)`로 offset이 포함된 ISO-8601 값을 브라우저 로컬 시간(KST 등)으로 표시한다.
- `recencyBonus`와 Topic 후보 시간 비교는 모두 동일한 UTC 기준으로 계산한다.

## 후속 개선 항목

현재 MVP 이후에는 운영 수준의 안정성과 데이터 품질 개선을 우선합니다.

- Kafka 저장/발행 원자성 보강: Outbox Pattern 또는 별도 재발행 복구 작업
- 관측/운영: 메트릭, 중앙 로그, 알림, 백업·복구 정책
- Topic AI 재분석 실패에 대한 자동 retry/status 관리
- IGDB 대규모 카탈로그 동기화 시 작업 큐/진행률 표시

## Development Notes

이 프로젝트는 교육용 MSA 예제를 출발점으로 게임 뉴스 도메인에 맞게 확장·재구성했습니다. 현재는 개발·포트폴리오 목적의 MVP이며, 운영 환경에서는 보안·관측·백업·비밀값 관리 등의 추가 보완이 필요합니다.

현재 자체 서비스 패키지는 `com.gamenews.*`, DB는 `game_news_db`, Docker 네트워크는 `game-news-net`, 인증은 User Service 기반 자체 RS256 JWT 구조를 사용합니다.

### IGDB-first Game / Franchise identity

- `Game.igdbId`가 IGDB 카탈로그 Game의 고유 식별자다.
- `Game.name`은 표시/검색용이므로 서로 다른 IGDB ID가 같은 이름을 가져도 저장할 수 있다.
- 동일 이름의 IGDB Game이 여러 개여도 canonical exact match 중 `Main Game`이 하나뿐이면 일반 기사에는 Main Game을 우선한다.
- 기사 문맥이 Remaster/Remake/Port/Expansion/DLC/Pack을 명시하면 해당 IGDB `game_type`과 일치하는 후보만 자동 선택하며, 그래도 복수면 관리자 검토로 남긴다.
- `Franchise.igdbId`는 IGDB `/franchises`, `Franchise.igdbCollectionId`는 IGDB `/collections`(Series)의 식별자를 각각 보존한다.
- Franchise 전체 Game catalog는 자동 확장하지 않으며 관리자 수동 `sync-igdb`가 실행된 경우에만 `GameFranchise`를 동기화한다.
- ArticleGame/TopicGame 및 ArticleFranchise/TopicFranchise에는 실제 기사에서 판별된 엔티티만 연결하며 Franchise 카탈로그 전체를 전파하지 않는다.

