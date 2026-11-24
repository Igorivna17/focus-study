# FocusStudy — AI-планувальник навчальних сесій (Pomodoro)

**FocusStudy** — це мобільний застосунок для Android (Kotlin, Jetpack Compose) із серверною частиною на Python (FastAPI) та інтеграцією Google Gemini AI (`gemini-3.8-flash`), який автоматично розбиває будь-яку навчальну тему на структуровані 25-хвилинні Pomodoro-інтервали з урахуванням бажаної інтенсивності підготовки, зберігає історію планів локально на пристрої та надає рекомендації для перерв.

---

## Архітектура та структура репозиторію

    FocusStudy-MVP/
    ├── backend/
    │   ├── main.py              # FastAPI сервер, інтеграція Gemini AI та Graceful Fallback
    │   ├── test_main.py         # Автоматизовані тести (pytest)
    │   └── requirements.txt     # Залежності Python
    ├── mobile/
    │   └── app/src/main/java/com/example/focusstudy/
    │       ├── MainActivity.kt      # Android UI (Jetpack Compose) та Retrofit клієнт
    │       └── HistoryStorage.kt    # Локальне сховище історії запитів (SharedPreferences + Gson)
    ├── docs/                    # Knowledge Base проєкту (8 документів специфікації)
    │   ├── project-overview.md
    │   ├── requirements.md
    │   ├── architecture.md
    │   ├── api-contract.md
    │   ├── ai-component.md
    │   ├── testing.md
    │   ├── known-limitations.md
    │   └── decisions.md
    ├── ai-instructions.md       # Правила роботи coding agent із репозиторієм
    └── README.md                # Документація проєкту та інструкції запуску

---

## Стек технологій

* **Mobile:** Android (min SDK 24 / target SDK 35+), Kotlin, Jetpack Compose (Material 3), Retrofit, OkHttp, Gson.
* **Локальне збереження (Mobile):** `SharedPreferences` + JSON-серіалізація (`HistoryStorage.kt`).
* **Backend:** Python 3.10+, FastAPI, Pydantic, Uvicorn.
* **AI-компонент:** Google GenAI SDK (`gemini-3.8-flash`) зі структурованим виводом (`response_schema`) та резервним локальним генератором (*Graceful Fallback*).
* **Тестування:** `pytest`, `httpx`.

---

## Команди запуску та перевірки

### 1. Запуск Backend (Python FastAPI)
1. Перейдіть у директорію `backend/` та створіть і активуйте віртуальне середовище:
       cd backend
       python -m venv venv
       .\venv\Scripts\activate
2. Встановіть залежності:
       pip install -r requirements.txt
3. Створіть файл `backend/.env` (ігнорується Git) та вкажіть ключ:
       GEMINI_API_KEY=ваш_ключ_від_google_ai_studio
4. Запустіть сервер:
       uvicorn main:app --host 0.0.0.0 --port 8000
   Сервер працює на `http://localhost:8000`, Swagger UI — `http://localhost:8000/docs`.

### 2. Запуск автотестів (Backend)
У директорії `backend/` з активованим `venv`:

    pytest -v

### 3. Запуск Mobile (Android Studio)
1. Відкрийте папку `mobile/` в Android Studio та дочекайтеся синхронізації Gradle.
2. Запустіть Android Emulator та прокиньте локальний порт комп'ютера на емулятор:
       adb reverse tcp:8000 tcp:8000
   *(Якщо `adb` не додано до `PATH` у Windows: `& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" reverse tcp:8000 tcp:8000`)*
3. Натисніть **Run (`▶`)**.

---

## Опис API (`v1.1.0`)

Детальний контракт описано у [`docs/api-contract.md`](docs/api-contract.md).

* **`GET /health`** — перевірка стану сервера (`200 OK`, повертає `{"status": "ok", "service": "FocusStudy Backend"}`).
* **`POST /api/plan`** — приймає JSON:
      {
        "topic": "Комп'ютерні мережі",
        "available_minutes": 90,
        "difficulty": "hard"
      }
  * `topic` — рядок від 3 до 120 символів (обов'язково).
  * `available_minutes` — ціле число від 25 до 300 хвилин (обов'язково).
  * `difficulty` — рівень інтенсивності підготовки: `"easy"`, `"medium"` або `"hard"` (опціонально, за замовчуванням `"medium"`).
  * Повертає структурований Pomodoro-план (`200 OK`) або помилку валідації (`422 Unprocessable Entity`).

---

## Локальне збереження даних (Mobile)

* **Ізольований шар даних (`HistoryStorage.kt`):** відокремлений від UI клас, який керує збереженням, читанням, пошуком за ID та видаленням записів у `SharedPreferences` (`focus_study_history`).
* **Структура локального запису (`HistoryRecord`):**
  * `id` — локальний ідентифікатор (`timestamp`);
  * `created_at` — дата й час отримання результату (`yyyy-MM-dd HH:mm`);
  * `input_summary` — коротке зведення запиту (тема, час у хвилинах, рівень інтенсивності);
  * `result_summary` — короткий підсумок результату (кількість Pomodoro-сетів);
  * `status` — стан запису (`"готово"`);
  * `plan` — повний об'єкт `PlanResponse` для відображення деталей.
* **Поведінка та життєвий цикл:**
  * У локальну історію зберігаються **виключно успішні** відповіді сервера (`200 OK`); мережеві помилки та некоректні відповіді не записуються.
  * На вкладці **«Історія»** записи відображаються у зворотному хронологічному порядку (новіші зверху), передбачено стан порожньої історії, перегляд деталей плану та видалення запису чи очищення всієї історії з підтвердженням.
  * Усі збережені плани залишаються доступними після повного закриття й повторного запуску застосунку.

---

## Knowledge Base та правила розробки

* `docs/` — база знань проєкту (огляд, вимоги, архітектура, API-контракт, специфікація AI-компонента, тестування, відомі обмеження та прийняті рішення).
* `ai-instructions.md` — правила та обмеження для AI-агента при роботі з репозиторієм.

## Обмеження поточної версії
* Серверна частина працює у *stateless*-режимі (історія планів зберігається локально лише на пристрої користувача без хмарної синхронізації).
* Відсутня авторизація користувачів.