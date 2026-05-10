import os
import json
from locust import HttpUser, task, between, events

ADMIN_USER = os.getenv("LOCUST_ADMIN_USER", "admin")
ADMIN_PASS = os.getenv("LOCUST_ADMIN_PASS", "password")


class AuthUser(HttpUser):
    """Simula pico de autenticación al inicio del día en el campus."""
    wait_time = between(1, 3)
    weight = 3

    @task
    def login_and_get_token(self):
        with self.client.post(
            "/api/v1/auth/login",
            json={"username": ADMIN_USER, "password": ADMIN_PASS},
            catch_response=True,
        ) as resp:
            if resp.status_code == 200:
                resp.success()
            else:
                resp.failure(f"Login failed: {resp.status_code}")


class HealthStatusUser(HttpUser):
    """Consulta continua de estado de salud durante el día."""
    wait_time = between(2, 5)
    weight = 4
    token = None

    def on_start(self):
        resp = self.client.post(
            "/api/v1/auth/login",
            json={"username": ADMIN_USER, "password": ADMIN_PASS},
        )
        if resp.status_code == 200:
            self.token = resp.json().get("token")

    @task
    def get_health_status(self):
        if self.token:
            self.client.get(
                "/api/v1/health/status",
                headers={"Authorization": f"Bearer {self.token}"},
            )


class QRContactUser(HttpUser):
    """Simula escaneo de QR en puntos de acceso del campus."""
    wait_time = between(1, 2)
    weight = 2
    token = None

    def on_start(self):
        resp = self.client.post(
            "/api/v1/auth/login",
            json={"username": ADMIN_USER, "password": ADMIN_PASS},
        )
        if resp.status_code == 200:
            self.token = resp.json().get("token")

    @task(3)
    def validate_qr(self):
        if self.token:
            self.client.post(
                "/api/v1/qr/validate",
                json={"qrToken": "test-qr-token"},
                headers={"Authorization": f"Bearer {self.token}"},
            )

    @task(1)
    def generate_qr(self):
        if self.token:
            self.client.get(
                "/api/v1/auth/qr",
                headers={"Authorization": f"Bearer {self.token}"},
            )


class EscalationUser(HttpUser):
    """Simula reportes de síntomas y monitoreo de escalada de estado."""
    wait_time = between(5, 10)
    weight = 1
    token = None

    def on_start(self):
        resp = self.client.post(
            "/api/v1/auth/login",
            json={"username": ADMIN_USER, "password": ADMIN_PASS},
        )
        if resp.status_code == 200:
            self.token = resp.json().get("token")

    @task
    def report_symptoms(self):
        if self.token:
            self.client.post(
                "/api/v1/health/report",
                json={"symptoms": ["fever", "cough"], "severity": "mild"},
                headers={"Authorization": f"Bearer {self.token}"},
            )
