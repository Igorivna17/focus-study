# API Контракт (`FocusStudy API v1.0.0`)

Базова адреса (локальна розробка): `http://localhost:8000`

## 1. `GET /health`
Перевірка доступності бекенду.

**Response `200 OK`:**
    {
      "status": "ok",
      "service": "FocusStudy Backend"
    }

## 2. `POST /api/plan`
Генерація навчального Pomodoro-плану.

### Request Body (`application/json`)
| Поле | Тип | Обов'язкове | Обмеження |
|---|---|---|---|
| `topic` | `string` | Так | `min_length=3`, `max_length=120`, не лише пробіли |
| `available_minutes` | `integer` | Так | `ge=25`, `le=300` |
| `difficulty` | `string` | Ні (за замовч. `"medium"`) | Допустимі значення: `"easy"`, `"medium"`, `"hard"` |

### Response `200 OK` (`application/json`)
    {
      "topic": "екзамен з математики",
      "total_pomodoros": 2,
      "intervals": [
        {
          "step_number": 1,
          "title": "Огляд теорії та базових понять",
          "duration_minutes": 25,
          "task_description": "Систематизація ключових визначень, формул та правил."
        }
      ],
      "break_tip": "Зроби 5-хвилинну перерву: відійди від монітора, потягнися та випий води."
    }

### Коди помилок
- `422 Unprocessable Entity` — порушення обмежень полів `topic` або `available_minutes`.
- `502 Bad Gateway` — відсутній `GEMINI_API_KEY` на сервері або критична помилка зовнішнього шлюзу.