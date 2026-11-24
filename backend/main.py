import os
import time
from typing import List
from fastapi import FastAPI, HTTPException, status
from pydantic import BaseModel, Field
from dotenv import load_dotenv
from google import genai
from google.genai import types

load_dotenv()
API_KEY = os.getenv("GEMINI_API_KEY")

app = FastAPI(title="FocusStudy API", version="1.0.0")

class PlanRequest(BaseModel):
    topic: str = Field(..., min_length=3, max_length=120)
    available_minutes: int = Field(..., ge=25, le=300)

class PomodoroInterval(BaseModel):
    step_number: int
    title: str
    duration_minutes: int
    task_description: str

class PlanResponse(BaseModel):
    topic: str
    total_pomodoros: int
    intervals: List[PomodoroInterval]
    break_tip: str

@app.get("/health", status_code=status.HTTP_200_OK)
def health_check():
    return {"status": "ok", "service": "FocusStudy Backend"}

@app.post("/api/plan", response_model=PlanResponse, status_code=status.HTTP_200_OK)
def generate_study_plan(request: PlanRequest):
    clean_topic = request.topic.strip()
    if not clean_topic:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail="Назва теми не може складатися лише з пробілів"
        )

    if not API_KEY:
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail="API ключ не знайдено у .env"
        )

    client = genai.Client(
        api_key=API_KEY,
        http_options=types.HttpOptions(timeout=15000)
    )
    prompt = f"""
    Розбий навчальну тему на Pomodoro-інтервали по 25 хвилин.
    Тема: "{clean_topic}"
    Загальний час: {request.available_minutes} хвилин.
    Порахуй, скільки 25-хвилинних інтервалів уміщається (з урахуванням 5-хвилинних перерв між ними, мінімум 1 інтервал).
    Поверни чіткий українськомовний план.
    """

    # 3 швидкі спроби звернутися до актуальної моделі gemini-3.8-flash
    for attempt in range(1, 4):
        try:
            print(f"Спроба {attempt}: запит до gemini-3.8-flash...")
            response = client.models.generate_content(
                model="gemini-3.8-flash",
                contents=prompt,
                config=types.GenerateContentConfig(
                    response_mime_type="application/json",
                    response_schema=PlanResponse,
                    thinking_config=types.ThinkingConfig(thinking_budget=0),
                ),
            )
            print("Відповідь успішно отримано від Gemini AI!")
            return PlanResponse.model_validate_json(response.text)
        except Exception as e:
            print(f"Спроба {attempt} не вдалася (перевантаження API):", repr(e))
            time.sleep(1)

    # Graceful fallback: якщо Google AI тимчасово перевантажений (503), повертаємо структурований план локально
    print("Активовано резервну генерацію плану (Graceful Fallback).")
    count = max(1, request.available_minutes // 30)
    step_templates = [
        ("Огляд теорії та базових понять", "Систематизація ключових визначень, формул та правил по темі без відволікань."),
        ("Практичне закріплення матеріалу", "Розв'язання типових задач, прикладів або тестів для перевірки розуміння."),
        ("Повторення складних моментів", "Аналіз помилок, складання коротких тез та фінальна самоперевірка."),
    ]
    fallback_intervals = []
    for i in range(count):
        title_base, desc = step_templates[i % len(step_templates)]
        fallback_intervals.append(
            PomodoroInterval(
                step_number=i + 1,
                title=f"{title_base} ({clean_topic})",
                duration_minutes=25,
                task_description=desc
            )
        )

    return PlanResponse(
        topic=clean_topic,
        total_pomodoros=count,
        intervals=fallback_intervals,
        break_tip="Зроби 5-хвилинну перерву: відійди від монітора, потягнися та випий води."
    )