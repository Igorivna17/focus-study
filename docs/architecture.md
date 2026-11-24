# Архітектура системи

## Компоненти системи
1. **Mobile Client (`mobile/`):**
   - Платформа: Android (Kotlin, Jetpack Compose, Material 3).
   - Файл реалізації: `mobile/app/src/main/java/com/example/focusstudy/MainActivity.kt`.
   - Мережевий шар: `RetrofitClient` на базі Retrofit 2, OkHttp (таймаути 60 с) та Gson.
2. **Backend Server (`backend/`):**
   - Платформа: Python 3, FastAPI, Uvicorn.
   - Файл реалізації: `backend/main.py`.
   - Відповідає за валідацію запитів, формування промпту, виклик Google Gemini API та резервну генерацію.
3. **AI Service (зовнішній):**
   - Google Gemini API (`gemini-3.8-flash`) через офіційний SDK `google-genai`.

## Потік даних (Data Flow)
1. Користувач заповнює форму в `FocusStudyScreen` (`MainActivity.kt`).
2. `RetrofitClient` виконує асинхронний `POST http://localhost:8000/api/plan` (через USB/ADB тунель `adb reverse tcp:8000 tcp:8000`).
3. FastAPI валідує JSON-тіло через модель `PlanRequest`.
4. Функція `generate_study_plan` звертається до `gemini-3.8-flash` із жорсткою схемою `PlanResponse`.
5. У разі помилки зовнішнього API (`503 UNAVAILABLE`) активується локальний *Graceful Fallback*.
6. Відповідь JSON повертається в Android-клієнт, десеріалізується у `PlanResponse` та рендериться у `LazyColumn`.