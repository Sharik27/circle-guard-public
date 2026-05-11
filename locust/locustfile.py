import os
from locust import HttpUser, task, between

ADMIN_USER = os.getenv("LOCUST_ADMIN_USER", "super_admin")
ADMIN_PASS = os.getenv("LOCUST_ADMIN_PASS", "password")
# Hosts separados porque el gateway no enruta estos endpoints
AUTH_HOST       = os.getenv("LOCUST_AUTH_HOST", "")
PROMOTION_HOST  = os.getenv("LOCUST_PROMOTION_HOST", "")


def _login(client, auth_host):
    """Login contra auth-service. Usa URL absoluta cuando AUTH_HOST está definido."""
    url = f"{auth_host}/api/v1/auth/login" if auth_host else "/api/v1/auth/login"
    return client.post(
        url,
        name="/api/v1/auth/login",
        json={"username": ADMIN_USER, "password": ADMIN_PASS},
    )


class AuthUser(HttpUser):
    """Simula pico de autenticación al inicio del día en el campus."""
    wait_time = between(1, 3)
    weight = 3

    @task
    def login_and_get_token(self):
        url = f"{AUTH_HOST}/api/v1/auth/login" if AUTH_HOST else "/api/v1/auth/login"
        with self.client.post(
            url,
            name="/api/v1/auth/login",
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
        resp = _login(self.client, AUTH_HOST)
        if resp.status_code == 200:
            self.token = resp.json().get("token")

    @task
    def get_health_stats(self):
        if self.token:
            url = f"{PROMOTION_HOST}/api/v1/health-status/stats" if PROMOTION_HOST else "/api/v1/health-status/stats"
            self.client.get(
                url,
                name="/api/v1/health-status/stats",
                headers={"Authorization": f"Bearer {self.token}"},
            )


class QRContactUser(HttpUser):
    """Simula escaneo de QR en puntos de acceso del campus."""
    wait_time = between(1, 2)
    weight = 2
    token = None

    def on_start(self):
        resp = _login(self.client, AUTH_HOST)
        if resp.status_code == 200:
            self.token = resp.json().get("token")

    @task(3)
    def validate_qr(self):
        if self.token:
            self.client.post(
                "/api/v1/gate/validate",
                json={"qrToken": "test-qr-token"},
                headers={"Authorization": f"Bearer {self.token}"},
            )

    @task(1)
    def generate_qr(self):
        if self.token:
            url = f"{AUTH_HOST}/api/v1/auth/qr/generate" if AUTH_HOST else "/api/v1/auth/qr/generate"
            self.client.get(
                url,
                name="/api/v1/auth/qr/generate",
                headers={"Authorization": f"Bearer {self.token}"},
            )


class EscalationUser(HttpUser):
    """Simula reportes de síntomas y monitoreo de escalada de estado."""
    wait_time = between(5, 10)
    weight = 1
    token = None

    def on_start(self):
        resp = _login(self.client, AUTH_HOST)
        if resp.status_code == 200:
            self.token = resp.json().get("token")

    @task
    def report_symptoms(self):
        if self.token:
            url = f"{PROMOTION_HOST}/api/v1/health/report" if PROMOTION_HOST else "/api/v1/health/report"
            self.client.post(
                url,
                name="/api/v1/health/report",
                json={"symptoms": ["fever", "cough"], "severity": "mild"},
                headers={"Authorization": f"Bearer {self.token}"},
            )
