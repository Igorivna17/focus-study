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

        title_prefix, description = step_templates[criteria.difficulty]

        steps = [
            PlanStep(
                step_number=i,
                title=f"{title_prefix}: {criteria.topic} (етап {i})",
                duration_minutes=25,
                task_description=description,
            )
            for i in range(1, pomodoros_count + 1)
        ]

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
        )