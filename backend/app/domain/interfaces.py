from typing import Protocol
from app.domain.models import PlanCriteria, StudyPlan

class PlanProvider(Protocol):
    """Інтерфейс постачальника навчальних Pomodoro-планів."""

    @property
    def name(self) -> str: ...

    def generate_plan(self, criteria: PlanCriteria) -> StudyPlan: ...