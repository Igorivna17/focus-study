import logging
from app.domain.errors import ProviderUnavailableError
from app.domain.interfaces import PlanProvider
from app.domain.models import PlanCriteria, StudyPlan
from app.services.input_preparer import prepare_plan_input

logger = logging.getLogger("focusstudy.service")


class PlanService:
    """Прикладний сервіс побудови навчальних Pomodoro-планів з підготовкою даних та fallback."""

    def __init__(
        self,
        primary_provider: PlanProvider,
        fallback_provider: PlanProvider | None = None,
    ) -> None:
        self._primary_provider = primary_provider
        self._fallback_provider = fallback_provider

    def create_study_plan(self, criteria: PlanCriteria) -> StudyPlan:
        prepared = prepare_plan_input(criteria)

        if not prepared.is_suitable_for_study:
            logger.info("Query rejected at input preparation stage: %s", prepared.unsuitable_reason)
            return StudyPlan(
                topic=prepared.criteria.topic,
                total_pomodoros=0,
                intervals=[],
                break_tip="",
                provider_name="rule-filter",
                is_suitable=False,
                rejection_reason=prepared.unsuitable_reason,
                context_applied=False,
            )

        try:
            plan = self._primary_provider.generate_plan(prepared.criteria)
            logger.info(
                "Plan generated via primary provider: %s (context_applied=%s, suitable=%s)",
                self._primary_provider.name,
                plan.context_applied,
                plan.is_suitable,
            )
            return plan
        except ProviderUnavailableError as primary_err:
            logger.warning(
                "Primary provider '%s' unavailable: %s",
                self._primary_provider.name,
                primary_err,
            )
            if self._fallback_provider is None:
                raise

            logger.info("Switching to fallback provider: %s", self._fallback_provider.name)
            return self._fallback_provider.generate_plan(prepared.criteria)