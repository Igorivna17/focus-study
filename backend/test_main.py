import pytest
from fastapi.testclient import TestClient

from app.api.dependencies import get_plan_service
from app.infrastructure.mock_provider import MockPlanProvider
from app.services.plan_service import PlanService
from main import app

client = TestClient(app)


@pytest.fixture(autouse=True)
def reset_dependency_overrides():
    yield
    app.dependency_overrides.clear()


def test_health_check_returns_v2():
    response = client.get("/health")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "ok"
    assert data["version"] == "2.0.0"


def test_create_plan_with_mock_provider_success():
    app.dependency_overrides[get_plan_service] = lambda: PlanService(
        primary_provider=MockPlanProvider(should_fail=False)
    )

    response = client.post(
        "/api/plan",
        json={"topic": "Алгоритми та структури даних", "available_minutes": 75, "difficulty": "hard"},
    )
    assert response.status_code == 200
    data = response.json()
    assert data["topic"] == "Алгоритми та структури даних"
    assert data["total_pomodoros"] == 3
    assert len(data["intervals"]) == 3
    assert data["is_suitable"] is True
    assert data["context_applied"] is False


def test_create_plan_backward_compatibility_default_difficulty():
    app.dependency_overrides[get_plan_service] = lambda: PlanService(
        primary_provider=MockPlanProvider(should_fail=False)
    )

    response = client.post(
        "/api/plan",
        json={"topic": "Філософія", "available_minutes": 50},
    )
    assert response.status_code == 200
    data = response.json()
    assert data["total_pomodoros"] == 2
    assert "Поєднання теорії та практики" in data["intervals"][0]["title"]


def test_provider_outage_without_fallback_returns_502():
    app.dependency_overrides[get_plan_service] = lambda: PlanService(
        primary_provider=MockPlanProvider(should_fail=True),
        fallback_provider=None,
    )

    response = client.post(
        "/api/plan",
        json={"topic": "Комп'ютерні мережі", "available_minutes": 60, "difficulty": "medium"},
    )
    assert response.status_code == 502
    assert "outage" in response.json()["detail"].lower()


def test_primary_outage_switches_to_fallback_provider():
    app.dependency_overrides[get_plan_service] = lambda: PlanService(
        primary_provider=MockPlanProvider(should_fail=True, provider_label="broken-gemini"),
        fallback_provider=MockPlanProvider(should_fail=False, provider_label="fallback-local"),
    )

    response = client.post(
        "/api/plan",
        json={"topic": "Бази даних", "available_minutes": 50, "difficulty": "easy"},
    )
    assert response.status_code == 200
    data = response.json()
    assert data["total_pomodoros"] == 2
    assert "Оглядове ознайомлення" in data["intervals"][0]["title"]


def test_whitespace_only_topic_raises_domain_422():
    app.dependency_overrides[get_plan_service] = lambda: PlanService(
        primary_provider=MockPlanProvider(should_fail=False)
    )

    response = client.post(
        "/api/plan",
        json={"topic": "     ", "available_minutes": 60, "difficulty": "medium"},
    )
    assert response.status_code == 422


def test_request_id_header_is_propagated_and_generated():
    app.dependency_overrides[get_plan_service] = lambda: PlanService(
        primary_provider=MockPlanProvider(should_fail=False)
    )

    custom_response = client.get("/health", headers={"X-Request-ID": "lab5-trace-001"})
    assert custom_response.headers.get("X-Request-ID") == "lab5-trace-001"

    auto_response = client.get("/health")
    assert auto_response.headers.get("X-Request-ID")


def test_create_plan_with_study_context_personalizes_steps():
    app.dependency_overrides[get_plan_service] = lambda: PlanService(
        primary_provider=MockPlanProvider(should_fail=False)
    )

    response = client.post(
        "/api/plan",
        json={
            "topic": "Комп'ютерні мережі",
            "available_minutes": 50,
            "difficulty": "hard",
            "study_context": "Підготовка до лаби: TCP/UDP сокети та маршрутизація",
        },
    )
    assert response.status_code == 200
    data = response.json()
    assert data["is_suitable"] is True
    assert data["context_applied"] is True
    assert "TCP/UDP сокети" in data["intervals"][0]["task_description"]


def test_unsuitable_keyboard_gibberish_returns_not_suitable_state():
    app.dependency_overrides[get_plan_service] = lambda: PlanService(
        primary_provider=MockPlanProvider(should_fail=False)
    )

    response = client.post(
        "/api/plan",
        json={"topic": "asdfghjkl", "available_minutes": 50, "difficulty": "medium"},
    )
    assert response.status_code == 200
    data = response.json()
    assert data["is_suitable"] is False
    assert data["total_pomodoros"] == 0
    assert data["intervals"] == []
    assert data["rejection_reason"] is not None


def test_unsuitable_household_task_returns_not_suitable_state():
    app.dependency_overrides[get_plan_service] = lambda: PlanService(
        primary_provider=MockPlanProvider(should_fail=False)
    )

    response = client.post(
        "/api/plan",
        json={"topic": "купити хліб і молоко", "available_minutes": 25, "difficulty": "easy"},
    )
    assert response.status_code == 200
    data = response.json()
    assert data["is_suitable"] is False
    assert "побутове" in data["rejection_reason"].lower()