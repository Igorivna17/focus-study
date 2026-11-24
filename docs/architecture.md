# Архітектура системи

## Компоненти системи
1. **Mobile Client (`mobile/`):**
   - Платформа: Android (Kotlin, Jetpack Compose, Material 3).
   - Файл реалізації: `mobile/app/src/main/java/com/example/focusstudy/MainActivity.kt`.
   - Мережевий шар: `RetrofitClient` на базі Retrofit 2, OkHttp (таймаути 60 с) та Gson.

### Локальне сховище (Mobile)
* `HistoryStorage.kt` — ізольований від UI клас для збереження історії успішних Pomodoro-планів у `SharedPreferences` (`focus_study_history`) у форматі JSON через `Gson`.
* Дані зберігаються виключно після валідної відповіді `200 OK` від сервера та відображаються на екранах «Історія» й «Деталі плану».

2. **Backend Server (`backend/`):**
   - Платформа: Python 3, FastAPI, Uvicorn.
   - Файл реалізації: `backend/main.py`.
   - Відповідає за валідацію запитів, формування промпту, виклик Google Gemini API та резервну генерацію.
3. **AI Service (зовнішній):**
   - Google Gemini API (`gemini-3.8-flash`) через офіційний SDK `google-genai`.
   
### Модульна архітектура Backend (Варіант Б, v2.0.0)
Серверну частину (`backend/`) реорганізовано з монолітного `main.py` у багатошарову структуру з чітким розділенням відповідальностей:
* **`app/core/config.py`** — централізована конфігурація (`Settings`: `GEMINI_API_KEY`, модель, таймаути, прапорець `USE_MOCK_PROVIDER`).
* **`app/domain/`** — моделі предметної області (`PlanCriteria`, `StudyPlan`, `PlanStep`), доменні винятки (`InvalidPlanCriteriaError`, `ProviderUnavailableError`) та інтерфейс постачальника планів `PlanProvider` (`Protocol`).
* **`app/infrastructure/`** — дві взаємозамінні реалізації `PlanProvider`:
  * `GeminiPlanProvider` — адаптер зовнішнього Google GenAI SDK (`gemini-3.8-flash`);
  * `MockPlanProvider` — детермінована локальна реалізація для тестування без мережі та резервного режиму (*fallback*).
* **`app/services/plan_service.py`** — прикладна бізнес-логіка побудови плану та перемикання на `fallback_provider` у разі недоступності основного AI-провайдера.
* **`app/api/` та `main.py`** — тонкий HTTP-роутер, Pydantic-схеми запиту/відповіді, впровадження залежностей через `FastAPI Depends` (`dependencies.py`), централізований мапінг доменних помилок у HTTP-статуси (`error_handlers.py`) та middleware наскрізного трасування заголовка `X-Request-ID`.



## Потік даних (Data Flow)
1. Користувач заповнює форму в `FocusStudyScreen` (`MainActivity.kt`).
2. `RetrofitClient` виконує асинхронний `POST http://localhost:8000/api/plan` (через USB/ADB тунель `adb reverse tcp:8000 tcp:8000`).
3. FastAPI валідує JSON-тіло через модель `PlanRequest`.
4. Функція `generate_study_plan` звертається до `gemini-3.8-flash` із жорсткою схемою `PlanResponse`.
5. У разі помилки зовнішнього API (`503 UNAVAILABLE`) активується локальний *Graceful Fallback*.
6. Відповідь JSON повертається в Android-клієнт, десеріалізується у `PlanResponse` та рендериться у `LazyColumn`.