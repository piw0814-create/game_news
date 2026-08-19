# /docker-compose.yml (컨테이너 실행용 실제 적용값)
# /insight-service/app/config/settings.py (기본값)
# /insight-service/.env (로컬 직접 실행용, Git/Docker 이미지 제외)

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    # 서버 설정
    app_port: int = 8085
    app_name: str = "insight-service"

    # Eureka 설정
    eureka_server_url: str = "http://localhost:8761/eureka"
    eureka_instance_host: str = "localhost"

    # 사용자용 Insight API를 추가할 경우 동일한 자체 JWT를 검증한다.
    jwt_issuer_uri: str = "game-news"
    jwk_set_uri: str = "http://localhost:8081/api/auth/jwks"

    # News Service
    news_service_url: str = "http://localhost:8082"
    news_service_timeout_seconds: float = 10.0

    # Kafka
    kafka_bootstrap_servers: str = "localhost:9092"
    kafka_consumer_group_id: str = "insight-service"
    kafka_topic_news_created: str = "news.created"
    kafka_retry_delay_seconds: float = 5.0

    # 재시작 시 미완료 기사 복구
    analysis_recovery_enabled: bool = True
    analysis_recovery_limit: int = 20
    analysis_recovery_processing_stale_minutes: int = 15
    analysis_recovery_startup_retry_count: int = 10
    analysis_recovery_startup_retry_delay_seconds: float = 3.0

    # OpenAI
    openai_api_key: str = ""
    openai_model: str = "gpt-5.6-luna"
    openai_max_content_chars: int = 12000
    openai_max_output_tokens: int = 1200
    openai_known_games_limit: int = 200
    openai_topic_match_max_output_tokens: int = 500
    openai_topic_analysis_max_output_tokens: int = 900

    # 미등록 게임 AI 자동 등록
    game_auto_create_confidence_threshold: float = 0.90
    game_review_create_confidence_threshold: float = 0.60

    # Topic 자동 통합
    topic_candidate_window_hours: int = 48
    topic_candidate_db_limit: int = 10
    topic_candidate_ai_limit: int = 3
    topic_match_confidence_threshold: float = 0.85

    # Topic 재분석
    topic_analysis_article_limit: int = 10

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )


settings = Settings()
