# Game Intelligence — Deployment Runbook

이 문서는 현재 저장소를 **실제 서버에 올리기 직전까지 준비하고 검증하는 기준 문서**입니다. 기능 개발 내용은 `README.md`를 기준으로 하고, 여기서는 환경·빌드·기동·검증·복구 절차만 다룹니다.

## 1. 현재 운영형 구조

```text
Internet
   ↓
HTTP :80 (현재 repo 기준)
   ↓
Frontend Nginx
   ├─ Vue static
   └─ /api → API Gateway:8080
                    ↓
      User / News / Interest / Collector / Insight
                    ↓
             MariaDB / Kafka / Eureka
```

- 브라우저 API 요청은 모두 `/api` 상대경로를 사용합니다.
- Nginx가 `/api`를 API Gateway로 reverse proxy하므로 frontend와 API는 same-origin으로 동작합니다.
- 기본 compose의 `8080~8085`, `8761`, `9092`, `3379` publish는 `127.0.0.1`에만 바인딩되어 외부 네트워크에 직접 공개되지 않습니다.
- `docker-compose.prod.yml`은 frontend(Nginx)를 추가하고 서비스 restart policy를 적용하는 overlay입니다.
- **현재 저장소는 TLS(HTTPS) 종료를 포함하지 않습니다.** 실제 인터넷 공개 시에는 서버 앞단 reverse proxy/load balancer 또는 별도 TLS 구성을 추가하고 외부 방화벽은 필요한 포트만 허용합니다.

## 2. 서버 준비

필수:

```text
Linux server/VM
Docker Engine
Docker Compose plugin (docker compose)
Git 또는 배포할 소스 파일
외부 통신: RSS / OpenAI / Twitch OAuth / IGDB API 접근 가능
```

권장:

```text
운영 계정 분리
80/443 외 불필요한 외부 인바운드 차단
충분한 디스크 여유(MariaDB/Kafka volume + Docker image)
정기 DB backup 위치
```

정확한 CPU/RAM 사양은 실제 배포 대상과 동시 사용량을 정한 뒤 결정합니다.

## 3. 운영 환경변수 준비

루트 환경파일:

```bash
cp .env.example .env
```

필수로 실제 값 설정:

```text
DB_PASSWORD
DB_ROOT_PASSWORD
IGDB_CLIENT_ID
IGDB_CLIENT_SECRET
JWT_PRIVATE_KEY_BASE64
JWT_PUBLIC_KEY_BASE64
JWT_KEY_ID
JWT_ISSUER
```

Insight 환경파일:

```bash
cp insight-service/.env.example insight-service/.env
```

최소 필수:

```text
OPENAI_API_KEY
OPENAI_MODEL
```

`.env`, `insight-service/.env`, private key 파일은 Git에 커밋하지 않습니다.

### RSA JWT key 생성 예시

키가 아직 없다면 **저장소 밖의 안전한 위치**에서 생성합니다.

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out jwt-private.pem
openssl rsa -pubout -in jwt-private.pem -out jwt-public.pem
chmod 600 jwt-private.pem
```

한 줄 Base64 값 생성:

```bash
base64 < jwt-private.pem | tr -d '\n'
base64 < jwt-public.pem  | tr -d '\n'
```

각 결과를 `.env`의 `JWT_PRIVATE_KEY_BASE64`, `JWT_PUBLIC_KEY_BASE64`에 넣습니다.

## 4. Production Compose 정적 검증

실제 build/up 전에 최종 compose를 먼저 확인합니다.

```bash
docker compose \
  -f docker-compose.yml \
  -f docker-compose.prod.yml \
  config > /tmp/game-intelligence-compose.yml
```

확인할 것:

```text
frontend가 :80(FRONTEND_HTTP_PORT)에 publish되는지
api-gateway 및 개별 서비스가 127.0.0.1에만 bind되는지
MariaDB/Kafka volume이 유지되는지
IGDB/JWT/OpenAI 필수 환경값이 비어 있지 않은지
```

비밀값이 포함될 수 있으므로 `/tmp/game-intelligence-compose.yml`을 공유하거나 Git에 추가하지 않습니다.

## 5. 배포 직전 빌드

```bash
docker compose \
  -f docker-compose.yml \
  -f docker-compose.prod.yml \
  build
```

완전 재빌드가 필요한 경우에만:

```bash
docker compose \
  -f docker-compose.yml \
  -f docker-compose.prod.yml \
  build --no-cache
```

이 단계까지 성공하면 코드/이미지 기준으로 **배포 직전 준비 완료**입니다.

## 6. 실제 서버 기동

실제 배포를 진행하기로 결정한 뒤 실행합니다.

```bash
docker compose \
  -f docker-compose.yml \
  -f docker-compose.prod.yml \
  up -d
```

상태:

```bash
docker compose \
  -f docker-compose.yml \
  -f docker-compose.prod.yml \
  ps
```

## 7. 기동 직후 확인

Frontend Nginx:

```bash
curl -fsS http://127.0.0.1:${FRONTEND_HTTP_PORT:-80}/health
```

API Gateway:

```bash
curl -fsS http://127.0.0.1:${GATEWAY_HOST_PORT:-8080}/actuator/health
```

Insight Service:

```bash
curl -fsS http://127.0.0.1:8085/health
```

News Service는 Actuator가 없으므로 실제 API로 확인합니다.

```bash
curl -fsS http://127.0.0.1:8082/api/news
```

## 8. 최종 E2E 체크리스트

브라우저에서 최소 다음 흐름을 확인합니다.

```text
회원가입 / 로그인
Feed 조회
Topic 상세
관심 게임 추가/삭제 및 개인화 정렬
ADMIN 계정의 Game / Franchise / EntityReview 화면
IGDB ID exact 검색
```

Collector/Insight 파이프라인:

```text
RSS 수집
→ NewsArticle 저장
→ Kafka news.created
→ Article Analyzer
→ Game/Franchise Identity Resolution
→ Topic 생성/연결
→ Feed 노출
```

새 단일기사 Topic은 로그에서 다음과 같이 추가 Topic Analyzer 호출이 생략되는지 확인할 수 있습니다.

```text
TopicAnalyzer=SKIPPED
```

## 9. 로그 확인

전체:

```bash
docker compose \
  -f docker-compose.yml \
  -f docker-compose.prod.yml \
  logs -f
```

주요 서비스:

```bash
docker compose logs -f collector-service
docker compose logs -f insight-service
docker compose logs -f news-service
docker compose logs -f api-gateway
```

OpenAI 사용량 로그:

```bash
docker compose logs --no-color insight-service | grep '\[OpenAIUsage\]'
```

현재 구조에서는 Article Analyzer에 Prompt Cache를 사용하며 Topic Matcher/Topic Analyzer는 불필요한 implicit cache write를 사용하지 않습니다.

## 10. 재기동 / 중지

재기동:

```bash
docker compose \
  -f docker-compose.yml \
  -f docker-compose.prod.yml \
  restart
```

중지(데이터 volume 유지):

```bash
docker compose \
  -f docker-compose.yml \
  -f docker-compose.prod.yml \
  down
```

**운영 데이터가 필요하면 `down -v`를 사용하지 않습니다.** `-v`는 MariaDB/Kafka volume을 삭제합니다.

## 11. 업데이트 배포 기본 절차

```text
1. DB backup
2. 새 코드 반영
3. migration 필요 여부 확인
4. compose config 검증
5. build
6. up -d
7. health/E2E 확인
8. 로그 확인
```

기존 DB schema/data를 변경하는 SQL은 `scripts/migrate-*.sql`을 명시적으로 실행하며 애플리케이션 startup 코드에 일회성 migration/backfill을 남기지 않습니다.

## 12. 배포 전 최종 체크

- [ ] Git working tree가 의도한 상태다.
- [ ] 실제 secret이 Git diff/history에 없다.
- [ ] `.env`, `insight-service/.env`가 서버에만 존재한다.
- [ ] DB/JWT/IGDB/OpenAI 필수 값이 설정됐다.
- [ ] production compose `config`가 성공한다.
- [ ] production image build가 성공한다.
- [ ] MariaDB/Kafka volume 삭제 명령을 사용하지 않았다.
- [ ] 서버 방화벽에서 불필요한 외부 포트를 닫았다.
- [ ] 인터넷 공개 전 HTTPS 종료 방식을 결정했다.
- [ ] 실제 배포 전 DB backup/복구 위치를 결정했다.

여기까지 완료한 뒤 배포 대상(AWS EC2/VM 등), 서버 사양, 도메인/TLS 방식을 확정하면 실제 운영 배포 단계로 넘어갑니다.
