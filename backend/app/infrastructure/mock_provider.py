from app.domain.errors import ProviderUnavailableError
from app.domain.models import PlanCriteria, PlanStep, StudyPlan


class MockPlanProvider:
    """Локальний провайдер без зовнішньої мережі для автотестів та резервного режиму."""

    def __init__(self, should_fail: bool = False, provider_label: str = "mock") -> None:
        self._should_fail = should_fail
        self._label = provider_label

    @property
    def name(self) -> str:
        return self._label

    def generate_plan(self, criteria: PlanCriteria) -> StudyPlan:
        if self._should_fail:
            raise ProviderUnavailableError("Mock AI provider simulated an outage.")

        pomodoros_count = max(1, min(12, criteria.available_minutes // 25))
        has_context = bool(criteria.study_context and criteria.study_context.strip())

        step_templates = {
            "easy": (
                "Оглядове ознайомлення",
                "Спокійне читання базових понять та розбір простих прикладів без перевантаження.",
            ),
            "medium": (
                "Поєднання теорії та практики",
                "Систематизація ключових правил і розв'язання типових практичних завдань.",
            ),
            "hard": (
                "Інтенсивне поглиблене опрацювання",
                "Детальний аналіз складних кейсів, граничних випадків та самоперевірка на час.",
            ),
        }

        title_prefix, base_description = step_templates[criteria.difficulty]

        steps: list[PlanStep] = []
        for i in range(1, pomodoros_count + 1):
            if has_context:
                step_title = f"{title_prefix} (з урахуванням контексту): {criteria.topic} (етап {i})"
                step_desc = f"{base_description} Фокус за матеріалами користувача: «{criteria.study_context}»."
            else:
                step_title = f"{title_prefix}: {criteria.topic} (етап {i})"
                step_desc = base_description

            steps.append(
                PlanStep(
                    step_number=i,
                    title=step_title,
                    duration_minutes=25,
                    task_description=step_desc,
                )
            )

        break_tips = {
            "easy": "Зроби легку 5-хвилинну перерву, випий води та трохи пройдися.",
            "medium": "Відійди від екрана на 5 хвилин і зроби коротку розминку для очей.",
            "hard": "Обов'язково зміни обстановку на 5 хвилин і відпочинь від інтенсивного навантаження.",
        }

        return StudyPlan(
            topic=criteria.topic,
            total_pomodoros=pomodoros_count,
            intervals=steps,
            break_tip=break_tips[criteria.difficulty],
            provider_name=self.name,
            is_suitable=True,
            rejection_reason=None,
            context_applied=has_context,
        )