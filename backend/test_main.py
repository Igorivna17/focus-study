from fastapi.testclient import TestClient
from main import app

client = TestClient(app)


def test_health_check():
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json() == {"status": "ok", "service": "FocusStudy Backend"}


def test_plan_backward_compatibility_default_difficulty():
    response = client.post(
        "/api/plan",
        json={"topic": "Бази даних та SQL", "available_minutes": 60}
    )
    assert response.status_code == 200
    data = response.json()
    assert data["topic"] == "Бази даних та SQL"
    assert data["total_pomodoros"] >= 1
    assert len(data["intervals"]) >= 1
    assert "break_tip" in data


def test_plan_with_hard_difficulty():
    response = client.post(
        "/api/plan",
        json={
            "topic": "Комп'ютерні мережі",
            "available_minutes": 90,
            "difficulty": "hard"
        }
    )
    assert response.status_code == 200
    data = response.json()
    assert data["topic"] == "Комп'ютерні мережі"
    assert len(data["intervals"]) >= 1


def test_plan_invalid_difficulty_returns_422():
    response = client.post(
        "/api/plan",
        json={
            "topic": "Алгоритми",
            "available_minutes": 60,
            "difficulty": "extreme"
        }
    )
    assert response.status_code == 422


def test_plan_invalid_minutes_returns_422():
    response = client.post(
        "/api/plan",
        json={
            "topic": "Алгоритми",
            "available_minutes": 10,
            "difficulty": "easy"
        }
    )
    assert response.status_code == 422