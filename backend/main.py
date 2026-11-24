import os
import time
from typing import List, Literal
from fastapi import FastAPI, HTTPException, status
from pydantic import BaseModel, Field
from dotenv import load_dotenv
from google import genai
from google.genai import types

load_dotenv()
API_KEY = os.getenv("GEMINI_API_KEY")

app = FastAPI(title="FocusStudy API", version="1.1.0")

DIFFICULTY_DESCRIPTIONS = {
    "easy": "легкий оглядовий темп (базові поняття, прості приклади, без перевантаження)",
    "medium": "збалансований стандартний темп (теорія + типові практичні задачі)",
    "hard": "інтенсивна поглиблена підготовка (складні кейси, активне тестування та самоперевірка)"
}

class PlanRequest(BaseModel):
    topic: str = Field(..., min_length=3, max_length=120)
    available_minutes: int = Field(..., ge=25, le=300)
    difficulty: Literal["easy", "medium", "hard"] = "medium"

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

    difficulty_hint = DIFFICULTY_DESCRIPTIONS[request.difficulty]

    client = genai.Client(
        api_key=API_KEY,
        http_options=types.HttpOptions(timeout=15000)
    )
    prompt = f"""
    Розбий навчальну тему на Pomodoro-інтервали по 25 хвилин.
    Тема: "{clean_topic}"
    Загальний час: {request.available_minutes} хвилин.
    Рівень інтенсивності: {request.difficulty} — {difficulty_hint}.
    Порахуй, скільки 25-хвилинних інтервалів уміщається (з урахуванням 5-хвилинних перерв між ними, мінімум 1 інтервал).
    Адаптуй складність завдань під обраний рівень інтенсивності.
    Поверни чіткий українськомовний план.
    """

    for attempt in range(1, 4):
        try:
            print(f"Спроба {attempt}: запит до gemini-3.8-flash (рівень: {request.difficulty})...")
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

    print(f"Активовано резервну генерацію плану (Graceful Fallback, рівень: {request.difficulty}).")
    count = max(1, request.available_minutes // 30)

    fallback_by_difficulty = {
        "easy": [
            ("Легке знайомство з темою", "Спокійне читання конспекту та виділення головних тез без зазубрювання."),
            ("Базові приклади", "Розгляд 2–3 найпростіших прикладів для загального розуміння."),
            ("Коротке підбиття підсумків", "Усне повторення основних термінів своїми словами."),
        ],
        "medium": [
            ("Огляд теорії та базових понять", "Систематизація ключових визначень, формул та правил по темі без відволікань."),
            ("Практичне закріплення матеріалу", "Розв'язання типових задач, прикладів або тестів для перевірки розуміння."),
            ("Повторення складних моментів", "Аналіз помилок, складання коротких тез та фінальна самоперевірка."),
        ],
        "hard": [
            ("Глибокий аналіз теорії та винятків", "Детальний розбір складних аспектів, доведень та граничних випадків теми."),
            ("Інтенсивний практикум підвищеної складності", "Розв'язання нестандартних задач у режимі обмеженого часу без підказок."),
            ("Стрес-тест та робота над помилками", "Повна самоперевірка за екзаменаційними питаннями та усунення прогалин."),
        ]
    }

    step_templates = fallback_by_difficulty[request.difficulty]
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

    break_tips = {
        "easy": "Зроби легку 5-хвилинну перерву: потягнися, випий чаю та відпочинь від екрана.",
        "medium": "Зроби 5-хвилинну перерву: відійди від монітора, потягнися та випий води.",
        "hard": "Обов'язково відійди від робочого місця на 5 хвилин: зроби розминку для очей та спини після інтенсивної роботи."
    }

    return PlanResponse(
        topic=clean_topic,
        total_pomodoros=count,
        intervals=fallback_intervals,
        break_tip=break_tips[request.difficulty]
    )