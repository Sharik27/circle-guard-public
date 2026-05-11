# Punto 4 - Pipeline Stage Environment

## Objetivo

El entorno **stage** (`circleguard-stage`) replica las condiciones de producción en un namespace Kubernetes aislado, separado del entorno `circleguard-dev`. Su propósito es validar que el código que pasó todas las pruebas en dev también funciona correctamente con la configuración, puertos y recursos que tendría en producción, antes de hacer el deploy definitivo.

La diferencia clave con el entorno dev es que aquí las pruebas se ejecutan **contra los servicios reales desplegados en stage**, con sus propios puertos NodePort (`32xxx`), su propio namespace y su propia imagen Docker etiquetada como `:stage`.

---

## Configuración del Job Jenkins

El pipeline se configura en Jenkins como un segundo job tipo **Multibranch Pipeline**, apuntando al mismo repositorio pero usando `Jenkinsfile.stage` como script path.

### Diferencias de configuración vs entorno Dev

| Parámetro | Dev (`Jenkinsfile.dev`) | Stage (`Jenkinsfile.stage`) |
|---|---|---|
| Namespace K8s | `circleguard-dev` | `circleguard-stage` |
| Tag de imagen Docker | `:dev` | `:stage` |
| Prefijo NodePort | `31xxx` | `32xxx` |
| Manifest K8s | `k8s/namespace-dev.yaml` | `k8s/namespace-stage.yaml` |

Los NodePorts del entorno stage usan el prefijo `32` para evitar conflictos con los puertos del entorno dev que ya están corriendo en el mismo clúster.

---

## Estructura del pipeline

```
Checkout → Infraestructura K8s Base → Build → Unit Tests → Integration Tests
         → Docker Build → Deploy & Health Check → E2E Tests → Load Tests
```

El pipeline tiene 8 stages. Los stages de Build, Unit Tests, Integration Tests y Docker Build ejecutan sus sub-stages en paralelo por servicio para reducir el tiempo total de ejecución.

---

## Stage: Build

Compila los 6 JARs en paralelo usando Gradle, sin ejecutar tests. Esto garantiza que el código compila antes de invertir tiempo en pruebas.

```groovy
./gradlew :services:circleguard-<servicio>:bootJar -x test --no-daemon
```

Servicios compilados en paralelo: `auth-service`, `identity-service`, `gateway-service`, `promotion-service`, `notification-service`, `dashboard-service`.

---

## Stage: Unit Tests

Ejecuta las pruebas unitarias (`@Tag("unit")`) de cada servicio en paralelo. Si algún servicio falla, el pipeline continúa en estado `UNSTABLE` y publica los reportes JUnit de todos los servicios.

```groovy
./gradlew :services:circleguard-<servicio>:unitTest --no-daemon
```

Los resultados se publican con el plugin JUnit de Jenkins.

---

## Stage: Integration Tests

Ejecuta las pruebas de integración (`@Tag("integration")`) de cada servicio en paralelo. Testcontainers levanta instancias efímeras de PostgreSQL dentro del agente Jenkins, por lo que no depende de la infraestructura K8s.

```groovy
./gradlew :services:circleguard-<servicio>:integrationTest --no-daemon
```
---

## Stage: Docker Build

Construye una imagen Docker por servicio, directamente con el tag `:stage`. Cada ejecución del pipeline sobreescribe la imagen anterior, evitando la acumulación de imágenes con número de build.

```groovy
docker build -t circleguard-<servicio>:stage -f Dockerfile.ci-<servicio> .
```

El Dockerfile es generado en tiempo de ejecución por Jenkins (`writeFile`): copia el JAR compilado en el stage anterior sobre una imagen base `eclipse-temurin:21-jre-jammy`.

---

## Stage: Deploy & Health Check

Despliega los 6 servicios en el namespace `circleguard-stage` en paralelo. Para adaptar los manifests de K8s al entorno stage, el pipeline aplica dos transformaciones `sed` en tiempo de ejecución:

1. Reemplaza `namespace: circleguard` → `namespace: circleguard-stage`
2. Reemplaza la imagen `:latest` → `:stage`
3. Reemplaza el prefijo de NodePort `30` → `32`

```bash
sed 's/namespace: circleguard/namespace: circleguard-stage/g' k8s/auth-service/deployment.yaml \
    | sed 's|:latest|:stage|g' \
    | kubectl apply -f -

sed 's/namespace: circleguard/namespace: circleguard-stage/g' k8s/auth-service/service.yaml \
    | sed 's/nodePort: 30/nodePort: 32/g' \
    | kubectl apply -f -

kubectl rollout restart deployment/circleguard-auth-service -n circleguard-stage
kubectl rollout status deployment/circleguard-auth-service -n circleguard-stage --timeout=300s
```

El `rollout status` espera hasta 5 minutos a que cada deployment esté listo antes de continuar. Si algún pod no levanta en ese tiempo, el stage falla.

### Puertos NodePort en stage

| Servicio | Puerto NodePort (stage) |
|---|---|
| gateway-service | 32087 |
| auth-service | 32180 |
| promotion-service | 32088 |
| dashboard-service | 32085 |
| identity-service | 32095 |
| notification-service | 32090 |

---

## Stage: E2E Tests

Una vez que todos los servicios están corriendo en `circleguard-stage`, se abren túneles `kubectl port-forward` hacia el gateway (`:8887`) y el auth-service (`:8180`), y se ejecutan los tests E2E con RestAssured contra esos túneles.

```bash
kubectl port-forward svc/circleguard-gateway-service 8887:8087 -n circleguard-stage &
GW_PID=$!
kubectl port-forward svc/circleguard-auth-service 8180:8180 -n circleguard-stage &
AUTH_PID=$!
trap 'kill $GW_PID $AUTH_PID 2>/dev/null || true' EXIT
sleep 8
./gradlew :tests:e2e:test \
    -Dgateway.url=http://localhost:8887 \
    -Dauth.url=http://localhost:8180 \
    -Dtest.admin.user=super_admin \
    -Dtest.admin.password=password \
    --no-daemon
```

Los port-forwards y la ejecución de tests están en el mismo bloque `sh` para que los procesos background no mueran entre steps. El `trap EXIT` garantiza que los túneles se cierren aunque el pipeline falle.

---

## Stage: Load Tests

Ejecuta Locust en modo headless apuntando a los NodePorts del entorno stage (`32xxx`). El contenedor se nombra `circleguard-locust-run` para poder extraer el reporte HTML con `docker cp` una vez que termina.

```bash
docker rm circleguard-locust-run 2>/dev/null || true

docker run --name circleguard-locust-run --user root \
  -e LOCUST_ADMIN_USER=super_admin \
  -e LOCUST_ADMIN_PASS=password \
  -e LOCUST_AUTH_HOST=http://<nodeIp>:32180 \
  -e LOCUST_PROMOTION_HOST=http://<nodeIp>:32088 \
  circleguard-locust:latest \
  --host http://<nodeIp>:32087

docker cp circleguard-locust-run:/tmp/report.html locust/reports/report.html
docker rm circleguard-locust-run
```

El reporte HTML se archiva como artefacto del build en Jenkins.

![Stage Load Tests completado y reporte HTML archivado](../screenshots/jenkins-stage-load-tests.png)

![Reporte HTML de Locust - resumen de métricas en stage](../screenshots/locust-stage-report-summary.png)

---

## Resultados de ejecución del pipeline

### Resumen de stages

![Pipeline stage ejecutado exitosamente - vista completa](../screenshots/jenkins-stage-pipeline-success.png)

| Stage | Resultado | Observaciones |
|---|---|---|
| Checkout | Exitoso | - |
| Infraestructura K8s Base | Exitoso | Namespace y ConfigMap aplicados |
| Build | Exitoso | 6 JARs compilados en paralelo |
| Unit Tests | Exitoso | 32 tests, 0 fallos |
| Integration Tests | Exitoso | 19 tests, 0 fallos |
| Docker Build | Exitoso | 6 imágenes `:stage` generadas |
| Deploy & Health Check | Exitoso | 6 deployments Ready en `circleguard-stage` |
| E2E Tests | Exitoso | 20 tests, 0 fallos |
| Load Tests | Exitoso | Reporte HTML archivado |

### Reportes JUnit en Jenkins

![Reporte JUnit consolidado del pipeline stage](../screenshots/jenkins-stage-junit-report.png)

---

## Análisis de resultados

### Pruebas unitarias e integración

Los 32 tests unitarios y 19 de integración se ejecutan antes del deploy, sobre el código compilado. Al correr en el mismo agente Jenkins sin dependencia de K8s, sus resultados son equivalentes a los del entorno dev - su función es hacer **fail-fast**: si el código tiene un error lógico o de configuración, el pipeline falla antes de construir la imagen Docker y ahorrar 5+ minutos de deploy.

### Pruebas E2E en stage

Los 20 tests E2E se ejecutan contra los servicios reales en `circleguard-stage`. A diferencia del entorno dev (NodePorts `31xxx`), aquí se usa el namespace y puertos `32xxx`, lo que valida que los manifests de Kubernetes están correctamente configurados para el entorno stage y que el routing del gateway funciona con la configuración de stage.

### Pruebas de carga en stage

Las pruebas de carga con Locust apuntan a los NodePorts `32xxx` del entorno stage. Los resultados esperados son equivalentes a los del entorno dev ya que ambos despliegan en el mismo clúster con los mismos recursos. Las métricas clave a observar son las mismas: latencia de login (p95 < 700ms), latencia de gate/validate (p95 < 25ms) y tasa de errores (objetivo: 0%).