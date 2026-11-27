from typing import Literal
from pydantic import BaseModel, Field


class PlanRequestSchema(BaseModel):
    topic: str = Field(..., min_length=3, max_length=120)
    available_minutes: int = Field(..., ge=25, le=300)
    difficulty: Literal["easy", "medium", "hard"] = "medium"
    study_context: str | None = Field(default=None, max_length=500)


class PomodoroIntervalSchema(BaseModel):
    step_number: int
    title: str
    duration_minutes: int
    task_description: str


class PlanResponseSchema(BaseModel):
    topic: str
    total_pomodoros: int
    intervals: list[PomodoroIntervalSchema]
    break_tip: str
    is_suitable: bool = True
    rejection_reason: str | None = None
    context_applied: bool = False