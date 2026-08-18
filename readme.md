# Game News AI Intelligence Feed

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
AI 기사 분석
   ↓
ArticleGame
   ↓
동일 사건 Topic 판단
   ↓
기존 Topic 연결 / 새 Topic 생성
   ↓
Topic 전체 재분석
   ↓
title / summary / category
importanceScore / whyImportant
   ↓
Vue Topic Feed
   ↓
관심 게임 기반 개인화 정렬
```

## 서비스 구성

| 구성 | 포트 | 역할 |
|---|---:|---|
| Vue Frontend | 3000 | Topic Feed, 상세, 관심 게임 UI |
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

GET    /api/topics
GET    /api/topics/{id}

GET    /api/interests/games
GET    /api/interests/game-ids
POST   /api/interests/games/{gameId}
DELETE /api/interests/games/{gameId}
```

`/api/news/**`, `/api/collector/**`, 사용자 단건 조회, Game/Topic 쓰기 API는 Gateway에 노출하지 않습니다. Collector와 Insight는 Docker 내부 네트워크에서 News Service를 직접 호출합니다. 개발 중 운영용 API를 확인해야 할 때는 로컬 호스트의 개별 서비스 포트를 사용합니다.

개인화 Feed는 Topic의 객관적 중요도와 사용자 관심 게임을 분리해서 계산합니다.

```text
personalizedScore
= importanceScore
+ interestBonus
+ recencyBonus
```

`personalizedScore`는 DB에 저장하지 않고 Vue에서 Feed 정렬에 사용합니다. 상단 `오늘 주요뉴스`는 개인화하지 않고 `importanceScore` 기준으로 유지합니다.

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

현재 게임뉴스 기능에는 별도 Role 권한 분기가 없으므로 사용자 Role 컬럼과 Role claim을 두지 않습니다. 관리자 기능이 실제로 필요해질 때 `USER / ADMIN` 같은 권한 모델을 별도로 설계합니다.

API Gateway를 외부 신뢰 경계로 사용합니다. `user-service`부터 `insight-service`까지의 개발용 포트 `8081~8085`는 Docker Compose에서 `127.0.0.1`에만 바인딩하여 같은 PC에서는 테스트할 수 있지만 외부 네트워크에서 직접 접근하지 못하도록 제한합니다. 운영 배포에서는 개별 서비스의 host port publish 자체를 제거하고 내부 네트워크에서만 접근하도록 구성하는 것이 권장됩니다.

비동기 파이프라인에서 Kafka Consumer 처리 실패는 재시도할 수 있도록 offset을 커밋하지 않지만, NewsArticle 저장 후 `news.created` Producer 발행이 최종 실패하면 해당 기사가 `PENDING`으로 남을 수 있습니다. MVP에서는 이 한계를 문서화하며 운영 수준에서는 Outbox Pattern 또는 별도 재발행 복구 작업을 추가하는 것을 권장합니다.

## 시간 정책

- Spring, FastAPI, MariaDB 컨테이너는 UTC를 기준으로 실행한다.
- JPA 감사 시각과 서버에서 생성하는 `collectedAt`, Topic 갱신 시각은 UTC로 생성한다.
- DB의 `DATETIME(6)`과 Entity의 `LocalDateTime`은 UTC 값으로 유지한다.
- 외부 API 응답에서는 UTC `LocalDateTime`을 `OffsetDateTime`으로 변환해 `Z` 또는 `+00:00` offset을 명시한다.
- Vue는 `new Date(...)`로 offset이 포함된 ISO-8601 값을 브라우저 로컬 시간(KST 등)으로 표시한다.
- `recencyBonus`와 Topic 후보 시간 비교는 모두 동일한 UTC 기준으로 계산한다.

## Development Notes

이 프로젝트는 교육용 MSA 예제를 출발점으로 게임 뉴스 도메인에 맞게 확장·재구성했습니다. 현재는 개발·포트폴리오 목적의 MVP이며, 운영 환경에서는 보안·관측·백업·비밀값 관리 등의 추가 보완이 필요합니다.

현재 자체 서비스 패키지는 `com.gamenews.*`, DB는 `game_news_db`, Docker 네트워크는 `game-news-net`, 인증은 User Service 기반 자체 RS256 JWT 구조를 사용합니다.
