import logging
import uuid
from fastapi import Depends, FastAPI, Request, Response

from app.api.dependencies import get_plan_service
from app.api.error_handlers import register_error_handlers
from app.api.schemas import (
    PlanRequestSchema,
    PlanResponseSchema,
    PomodoroIntervalSchema,
)
from app.core.config import settings
from app.domain.models import PlanCriteria
from app.services.plan_service import PlanService

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("focusstudy.api")

app = FastAPI(title=settings.app_title, version=settings.app_version)
register_error_handlers(app)


@app.middleware("http")
async def request_id_middleware(request: Request, call_next) -> Response:
    request_id = request.headers.get("X-Request-ID") or str(uuid.uuid4())
    request.state.request_id = request_id
    response = await call_next(request)
    response.headers["X-Request-ID"] = request_id
    logger.info("[%s] %s %s -> %s", request_id, request.method, request.url.path, response.status_code)
    return response


@app.get("/health")
def health_check() -> dict[str, str]:
    return {
        "status": "ok",
        "service": "FocusStudy Backend",
        "version": settings.app_version,
    }


@app.post("/api/plan", response_model=PlanResponseSchema)
def create_plan(
    payload: PlanRequestSchema,
    service: PlanService = Depends(get_plan_service),
) -> PlanResponseSchema:
    criteria = PlanCriteria(
        topic=payload.topic,
        available_minutes=payload.available_minutes,
        difficulty=payload.difficulty,
        study_context=payload.study_context,
    )
    study_plan = service.create_study_plan(criteria)

    return PlanResponseSchema(
        topic=study_plan.topic,
        total_pomodoros=study_plan.total_pomodoros,
        intervals=[
            PomodoroIntervalSchema(
                step_number=step.step_number,
                title=step.title,
                duration_minutes=step.duration_minutes,
                task_description=step.task_description,
            )
            for step in study_plan.intervals
        ],
        break_tip=study_plan.break_tip,
        is_suitable=study_plan.is_suitable,
        rejection_reason=study_plan.rejection_reason,
        context_applied=study_plan.context_applied,
    )