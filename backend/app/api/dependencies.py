from fastapi import Depends
from app.core.config import settings
from app.domain.interfaces import PlanProvider
from app.infrastructure.gemini_provider import GeminiPlanProvider
from app.infrastructure.mock_provider import MockPlanProvider
from app.services.plan_service import PlanService


def get_primary_provider() -> PlanProvider:
    if settings.use_mock_provider:
        return MockPlanProvider(provider_label="mock-primary")
    return GeminiPlanProvider(
        api_key=settings.gemini_api_key,
        model_name=settings.gemini_model,
    )


def get_fallback_provider() -> PlanProvider | None:
    return MockPlanProvider(provider_label="fallback-local")


def get_plan_service(
    primary_provider: PlanProvider = Depends(get_primary_provider),
    fallback_provider: PlanProvider | None = Depends(get_fallback_provider),
) -> PlanService:
    return PlanService(
        primary_provider=primary_provider,
        fallback_provider=fallback_provider,
    )