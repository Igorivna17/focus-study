from google import genai
from google.genai import types
from pydantic import BaseModel

from app.domain.errors import ProviderUnavailableError
from app.domain.models import PlanCriteria, PlanStep, StudyPlan


class _GeminiStepSchema(BaseModel):
    step_number: int
    title: str
    duration_minutes: int
    task_description: str


class _GeminiPlanSchema(BaseModel):
    topic: str
    total_pomodoros: int
    intervals: list[_GeminiStepSchema]
    break_tip: str


DIFFICULTY_PROMPTS = {
    "easy": "легкий оглядовий темп (базові поняття, прості приклади, без перевантаження)",
    "medium": "збалансований стандартний темп (теорія + типові практичні задачі)",
    "hard": "інтенсивна поглиблена підготовка (складні кейси, активне тестування та самоперевірка)",
}


class GeminiPlanProvider:
    """Адаптер для зовнішнього Google Gemini API."""

    def __init__(self, api_key: str | None, model_name: str) -> None:
        self._api_key = api_key
        self._model_name = model_name

    @property
    def name(self) -> str:
        return f"gemini:{self._model_name}"

    def generate_plan(self, criteria: PlanCriteria) -> StudyPlan:
        if not self._api_key:
            raise ProviderUnavailableError("GEMINI_API_KEY is not configured.")

        pomodoros_count = max(1, min(12, criteria.available_minutes // 25))
        difficulty_hint = DIFFICULTY_PROMPTS[criteria.difficulty]

        prompt = (
            f"Склади навчальний Pomodoro-план для теми: '{criteria.topic}'. "
            f"Доступний час: {criteria.available_minutes} хвилин ({pomodoros_count} сетів по 25 хвилин). "
            f"Рівень інтенсивності: {difficulty_hint}. "
            "Відповідай українською мовою у строго заданому JSON-форматі."
        )

        try:
            client = genai.Client(api_key=self._api_key)
            response = client.models.generate_content(
                model=self._model_name,
                contents=prompt,
                config=types.GenerateContentConfig(
                    response_mime_type="application/json",
                    response_schema=_GeminiPlanSchema,
                ),
            )
            if not response.parsed:
                raise ProviderUnavailableError("Gemini API returned an empty parsed response.")

            parsed: _GeminiPlanSchema = response.parsed
            steps = [
                PlanStep(
                    step_number=s.step_number,
                    title=s.title,
                    duration_minutes=s.duration_minutes,
                    task_description=s.task_description,
                )
                for s in parsed.intervals
            ]

            return StudyPlan(
                topic=parsed.topic,
                total_pomodoros=parsed.total_pomodoros,
                intervals=steps,
                break_tip=parsed.break_tip,
                provider_name=self.name,
            )
        except ProviderUnavailableError:
            raise
        except Exception as exc:
            raise ProviderUnavailableError(f"Gemini API error: {exc}") from exc