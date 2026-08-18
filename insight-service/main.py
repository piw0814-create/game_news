import logging
from contextlib import asynccontextmanager

import py_eureka_client.eureka_client as eureka_client
from fastapi import FastAPI

from app.config.settings import settings
from app.kafka.consumer import news_created_consumer
from app.router.topic_analysis_router import router as topic_analysis_router

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s - %(message)s"
)
logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info("[%s] 서비스 시작", settings.app_name)

    try:
        await eureka_client.init_async(
            eureka_server=settings.eureka_server_url,
            app_name=settings.app_name,
            instance_port=settings.app_port,
            instance_host=settings.eureka_instance_host,
        )
        logger.info("[Eureka] 서비스 등록 완료")
    except Exception as exc:
        logger.warning("[Eureka] 등록 실패 (개발 환경에서 무시 가능): %s", exc)

    try:
        news_created_consumer.start()
        logger.info("[Kafka] Consumer 시작 완료")
    except Exception as exc:
        logger.warning("[Kafka] Consumer 시작 실패: %s", exc)

    yield

    logger.info("[%s] 서비스 종료", settings.app_name)
    news_created_consumer.stop()
    await eureka_client.stop_async()


app = FastAPI(
    title="Game News Insight Service",
    description="게임 뉴스 기사 AI 분석 서비스",
    version="0.1.0",
    lifespan=lifespan,
)

app.include_router(topic_analysis_router)


@app.get("/health")
async def health():
    return {
        "status": "UP",
        "service": settings.app_name,
        "model": settings.openai_model,
        "openaiConfigured": bool(settings.openai_api_key.strip()),
    }


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(
        "main:app",
        host="0.0.0.0",
        port=settings.app_port,
        reload=True,
    )
