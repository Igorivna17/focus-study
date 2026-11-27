from dataclasses import dataclass
from typing import Literal

DifficultyLevel = Literal["easy", "medium", "hard"]


@dataclass(frozen=True)
class PlanCriteria:
    topic: str
    available_minutes: int
    difficulty: DifficultyLevel = "medium"
    study_context: str | None = None


@dataclass(frozen=True)
class PlanStep:
    step_number: int
    title: str
    duration_minutes: int
    task_description: str


@dataclass(frozen=True)
class StudyPlan:
    topic: str
    total_pomodoros: int
    intervals: list[PlanStep]
    break_tip: str
    provider_name: str
    is_suitable: bool = True
    rejection_reason: str | None = None
    context_applied: bool = False