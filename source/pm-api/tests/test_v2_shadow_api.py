from __future__ import annotations

from datetime import datetime, timedelta

from fastapi.testclient import TestClient

from app.main import app


client = TestClient(app)


def make_readings(equipment_id: str, count: int = 24, *, gap_at: int | None = None):
    start = datetime(2026, 1, 10)
    rows = []
    for index in range(count):
        offset = index + (2 if gap_at is not None and index >= gap_at else 0)
        rows.append(
            {
                "equipment_id": equipment_id,
                "collected_at": (start + timedelta(hours=offset)).isoformat(),
                "traffic_load": 0.5,
                "temperature_c": 25.0,
                "voltage_v": 12.0,
                "signal_strength_dbm": -65.0,
                "success_rate": 98.0,
                "response_time_ms": 80.0 + index,
                "retry_count": index % 2,
                "disconnect_count": 0,
                "packet_loss_rate": 0.2,
                "error_count": 0,
                "days_since_maintenance": 20,
            }
        )
    return rows


def test_v2_shadow_scores_known_device_with_24_contiguous_rows():
    response = client.post(
        "/api/predictive-maintenance/predict-v2-shadow",
        json={"equipment_id": "ANT-001", "readings": make_readings("ANT-001")},
    )

    assert response.status_code == 200
    result = response.json()
    assert result["status"] == "SCORED"
    assert result["shadow_only"] is True
    assert result["feature_count"] == 13
    assert 0 <= result["calibrated_probability"] <= 1
    assert result["model_version"] == "logistic-v2-active"


def test_v2_active_scores_with_primary_decision_marker():
    response = client.post(
        "/api/predictive-maintenance/predict-v2-active",
        json={"equipment_id": "ANT-001", "readings": make_readings("ANT-001")},
    )

    assert response.status_code == 200
    result = response.json()
    assert result["status"] == "SCORED"
    assert result["shadow_only"] is False
    assert result["model_version"] == "logistic-v2-active"


def test_v2_shadow_waits_for_contiguous_history():
    response = client.post(
        "/api/predictive-maintenance/predict-v2-shadow",
        json={
            "equipment_id": "ANT-001",
            "readings": make_readings("ANT-001", gap_at=20),
        },
    )

    assert response.status_code == 200
    assert response.json()["status"] == "WARMING_UP"
    assert response.json()["raw_probability"] is None


def test_v2_shadow_rejects_unknown_device_baseline():
    response = client.post(
        "/api/predictive-maintenance/predict-v2-shadow",
        json={"equipment_id": "NEW-001", "readings": make_readings("NEW-001")},
    )

    assert response.status_code == 200
    assert response.json()["status"] == "UNKNOWN_DEVICE"
