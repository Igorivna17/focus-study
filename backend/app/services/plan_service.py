import logging
from app.domain.errors import InvalidPlanCriteriaError, ProviderUnavailableError
from app.domain.interfaces import PlanProvider
from app.domain.models import PlanCriteria, StudyPlan

logger = logging.getLogger("focusstudy.service")


class PlanService:
    """Прикладний сервіс побудови навчальних Pomodoro-планів."""

    def __init__(
        self,
        primary_provider: PlanProvider,
        fallback_provider: PlanProvider | None = None,
    ) -> None:
        self._primary_provider = primary_provider
        self._fallback_provider = fallback_provider

    def create_study_plan(self, criteria: PlanCriteria) -> StudyPlan:
        cleaned_topic = criteria.topic.strip()
        if len(cleaned_topic) < 3:
            raise InvalidPlanCriteriaError("Тема навчання повинна містити щонайменше 3 непробільні символи.")

        normalized_criteria = PlanCriteria(
            topic=cleaned_topic,
            available_minutes=criteria.available_minutes,
            difficulty=criteria.difficulty,
        )

        try:
            plan = self._primary_provider.generate_plan(normalized_criteria)
            logger.info("Plan generated via primary provider: %s", self._primary_provider.name)
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
            return self._fallback_provider.generate_plan(normalized_criteria)