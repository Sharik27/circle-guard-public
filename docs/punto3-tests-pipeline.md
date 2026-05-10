# Punto 3 — Tests Multinivel y Pipeline CI/CD Secuencial

## Introducción

El objetivo de este punto es incorporar una estrategia de testing completa al proyecto **CircleGuard**, con pruebas en tres niveles (unitarias, integración y E2E), pruebas de carga con Locust, y la reestructuración del `Jenkinsfile.dev` para ejecutar cada nivel de forma secuencial antes de avanzar al siguiente.

### Niveles de test implementados

| Nivel | Herramienta | Tag JUnit | Cantidad |
|---|---|---|---|
| Unitario | JUnit 5 + Mockito | `@Tag("unit")` | 6 clases, 32 tests |
| Integración | JUnit 5 + Spring Test + TestContainers | `@Tag("integration")` | 5 clases, 19 tests |
| E2E | RestAssured | *(sin tag)* | 5 clases, 25 tests |
| Carga | Locust (Docker) | N/A | 4 escenarios |

### Estrategia de separación por tags

Se utiliza el sistema de etiquetas de JUnit 5 (`@Tag`) para ejecutar cada nivel de forma independiente. Gradle expone una tarea personalizada por nivel que filtra por tag:

```
./gradlew :services:<servicio>:unitTest         # solo @Tag("unit")
./gradlew :services:<servicio>:integrationTest  # solo @Tag("integration")
./gradlew :tests:e2e:test                       # módulo E2E completo
```

Esto permite que el pipeline CI/CD ejecute los niveles de forma secuencial y falle rápido: si los tests unitarios fallan, los de integración no se ejecutan.

---

## Estructura de archivos creados

```
circle-guard-public/
├── Jenkinsfile.dev                                        ← reestructurado (7 stages)
├── tests/
│   └── e2e/
│       ├── build.gradle.kts
│       └── src/test/java/co/circlegoard/e2e/
│           ├── config/E2ETestConfig.java
│           ├── auth/AuthE2ETest.java
│           ├── health/HealthStatusE2ETest.java
│           ├── contact/ContactRegistrationE2ETest.java
│           ├── escalation/StatusEscalationE2ETest.java
│           └── dashboard/DashboardE2ETest.java
├── locust/
│   ├── Dockerfile
│   ├── locustfile.py
│   └── locust.conf
└── services/
    ├── circleguard-auth-service/src/test/java/.../
    │   ├── service/JwtTokenServiceTest.java               ← unit
    │   ├── service/CustomUserDetailsServiceTest.java      ← unit
    │   └── controller/AuthControllerIntegrationTest.java  ← integration
    ├── circleguard-promotion-service/src/test/java/.../
    │   ├── service/BuildingServiceTest.java               ← unit
    │   ├── service/CircleServiceTest.java                 ← unit
    │   └── integration/BuildingJpaIntegrationTest.java    ← integration
    ├── circleguard-notification-service/src/test/java/.../
    │   ├── service/TemplateServiceUnitTest.java            ← unit
    │   └── service/NotificationKafkaIntegrationTest.java  ← integration
    ├── circleguard-dashboard-service/src/test/java/.../
    │   ├── service/KAnonymityFilterTest.java              ← unit
    │   └── controller/DashboardControllerIntegrationTest.java ← integration
    └── circleguard-identity-service/src/test/java/.../
        └── service/IdentityVaultServiceIntegrationTest.java ← integration
```

---

## Paso 1 — Configuración de Gradle

### Tareas `unitTest` e `integrationTest`

Se agrega al final del `build.gradle.kts` de cada servicio la definición de dos tareas personalizadas que filtran los tests por tag:

```kotlin
tasks.register<Test>("unitTest") {
    description = "Runs unit tests only (@Tag(\"unit\"))"
    group = "verification"
    useJUnitPlatform { includeTags("unit") }
    testResultsDir.set(layout.buildDirectory.dir("test-results/unitTest"))
}

tasks.register<Test>("integrationTest") {
    description = "Runs integration tests only (@Tag(\"integration\"))"
    group = "verification"
    useJUnitPlatform { includeTags("integration") }
    testResultsDir.set(layout.buildDirectory.dir("test-results/integrationTest"))
}
```

El parámetro `testResultsDir` escribe los XML de resultados en directorios separados (`unitTest/` e `integrationTest/`), lo que permite publicarlos de forma independiente en Jenkins con la directiva `junit`.

### Módulo E2E en `settings.gradle.kts`

```kotlin
include(":tests:e2e")
```

Se registra el módulo E2E como subproyecto de Gradle. Su `build.gradle.kts` no usa el plugin `spring-boot`; solo declara dependencias de RestAssured y JUnit:

```kotlin
plugins { java }

dependencies {
    testImplementation("io.rest-assured:rest-assured:5.4.0")
    testImplementation("io.rest-assured:json-path:5.4.0")
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.25.3")
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")
}

tasks.test {
    useJUnitPlatform()
    systemProperty("gateway.url", System.getProperty("gateway.url", "http://localhost:8080"))
    systemProperty("test.admin.user", System.getProperty("test.admin.user", "admin"))
    systemProperty("test.admin.password", System.getProperty("test.admin.password", "password"))
}
```

Los parámetros `gateway.url`, `test.admin.user` y `test.admin.password` se inyectan como system properties desde el pipeline con `-D`, permitiendo apuntar a cualquier entorno sin modificar el código.

---

## Paso 2 — Tests Unitarios (`@Tag("unit")`)

Los tests unitarios validan componentes individuales en aislamiento total. No levantan contexto de Spring ni acceden a bases de datos. Se instancian las clases directamente o con `@ExtendWith(MockitoExtension.class)`.

### auth-service — `JwtTokenServiceTest`

Valida la generación de tokens JWT sin contexto de Spring, instanciando directamente `JwtTokenService` con un secreto de 32+ caracteres (mínimo para HMAC-SHA256):

```java
@Tag("unit")
class JwtTokenServiceTest {

    private final JwtTokenService jwtService = new JwtTokenService(
        "test-secret-key-for-unit-tests-only-1234567890ab",
        3600L
    );

    @Test
    void generateToken_withValidInput_returnsNonNullToken() { ... }

    @Test
    void generateToken_producesThreePartJwt() {
        // Verifica estructura header.payload.signature
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    void generateToken_containsAnonymousIdAsSubject() { ... }

    @Test
    void generateToken_containsPermissionsInClaim() { ... }

    @Test
    void generateToken_differentCallsProduceDifferentTokens() { ... }
}
```

### auth-service — `CustomUserDetailsServiceTest`

Valida la carga de usuarios desde el repositorio local, incluyendo el manejo de usuarios inactivos y la correcta generación de authorities con el prefijo `ROLE_`:

```java
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock private LocalUserRepository localUserRepository;
    @InjectMocks private CustomUserDetailsService service;

    @Test void existingActiveUser_returnsUserDetails() { ... }
    @Test void activeUserWithRole_hasRolePrefixedAuthority() { ... }
    @Test void activeUserWithPermission_hasGranularAuthority() { ... }
    @Test void unknownUser_throwsUsernameNotFoundException() { ... }
    @Test void inactiveUser_throwsDisabledException() { ... }
}
```

### promotion-service — `BuildingServiceTest`

Valida la lógica de negocio del servicio de edificios, incluyendo la restricción de no eliminar un edificio que tenga pisos asociados:

```java
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class BuildingServiceTest {

    @Mock private BuildingRepository buildingRepository;
    @Mock private FloorRepository floorRepository;
    @InjectMocks private BuildingService buildingService;

    @Test void createBuilding_withValidData_persistsBuildingWithCorrectFields() { ... }
    @Test void deleteBuilding_withNoFloors_deletesSuccessfully() { ... }
    @Test void deleteBuilding_withExistingFloors_throwsRuntimeException() { ... }
    @Test void updateBuilding_withUnknownId_throwsRuntimeException() { ... }
    @Test void updateBuilding_withExistingId_updatesAllFields() { ... }
}
```

### promotion-service — `CircleServiceTest`

Valida la lógica de círculos: generación de códigos de invitación con prefijo `MESH-`, unión por código y cerrado de círculos con escalada de miembros activos:

```java
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class CircleServiceTest {

    @Test void createCircle_generatesInviteCodeWithMeshPrefix() { ... }
    @Test void createCircle_persistsCorrectNameAndLocation() { ... }
    @Test void joinCircle_withInvalidCode_throwsRuntimeException() { ... }
    @Test void forceFenceCircle_promotesActiveMembers() { ... }
    @Test void getUserCircles_withUnknownUser_returnsEmptyList() { ... }
}
```

### notification-service — `TemplateServiceUnitTest`

Valida la generación de contenido para notificaciones push, SMS y email según el status de exposición. Se instancia `TemplateService` con un mock de la `Configuration` de FreeMarker y se inyectan los campos `@Value` mediante `ReflectionTestUtils`:

```java
@Tag("unit")
class TemplateServiceUnitTest {

    private final TemplateService templateService;

    TemplateServiceUnitTest() throws Exception {
        Configuration freemarkerConfig = mock(Configuration.class);
        templateService = new TemplateService(freemarkerConfig);
        ReflectionTestUtils.setField(templateService, "appName", "CircleGuard");
        ReflectionTestUtils.setField(templateService, "deepLinkBase", "circleguard://");
    }

    @Test void buildPushContent_forSuspectStatus_containsWarningText() { ... }
    @Test void buildPushContent_forProbableStatus_containsAlertText() { ... }
    @Test void buildSmsContent_containsStatusAndAppName() { ... }
    @Test void buildPushMetadata_withDeepLink_includesLink() { ... }
    @Test void buildPushMetadata_withoutDeepLink_excludesLink() { ... }
    @Test void buildEmailFallback_returnsNonNullSubjectAndBody() { ... }
}
```

### dashboard-service — `KAnonymityFilterTest`

Valida el filtro de k-anonimidad que suprime resultados cuando el total de usuarios o un conteo individual no alcanza el umbral mínimo, protegiendo la privacidad individual:

```java
@Tag("unit")
class KAnonymityFilterTest {

    private final KAnonymityFilter filter = new KAnonymityFilter();

    @Test void apply_withNullStats_returnsEmptyMap() { ... }
    @Test void apply_withSufficientTotalUsers_doesNotMaskResult() { ... }
    @Test void apply_withTotalUsersBelowThreshold_masksEntireResult() { ... }
    @Test void apply_withCountFieldBelowThreshold_masksIndividualCount() { ... }
    @Test void apply_withCustomKThreshold_masksBasedOnCustomValue() { ... }
    @Test void apply_withEmptyStats_returnsEmptyResult() { ... }
}
```

---

## Paso 3 — Tests de Integración (`@Tag("integration")`)

Los tests de integración validan la interacción entre capas del mismo servicio (controlador→servicio, servicio→repositorio, listener→dispatcher). Usan el contexto parcial o completo de Spring, sin levantar la infraestructura externa real salvo donde se usa TestContainers.

### auth-service — `AuthControllerIntegrationTest`

Usa `@WebMvcTest` para levantar únicamente la capa web del controlador de login, con todos los servicios dependientes como `@MockBean`:

```java
@Tag("integration")
@WebMvcTest(LoginController.class)
@Import(SecurityConfig.class)
class AuthControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private AuthenticationManager authManager;
    @MockBean private JwtTokenService jwtService;
    @MockBean private IdentityClient identityClient;
    @MockBean private CustomUserDetailsService customUserDetailsService;

    @Test
    void login_withInvalidCredentials_returns401() throws Exception {
        when(authManager.authenticate(any()))
            .thenThrow(new BadCredentialsException("Bad credentials"));

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"unknown\",\"password\":\"wrong\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test void visitorHandoff_withValidAnonymousId_returnsTokenAndHandoffPayload() { ... }
    @Test void visitorHandoff_withMissingAnonymousId_returns400() { ... }
    @Test void login_withValidCredentials_returnsJwtAndAnonymousId() { ... }
}
```

### identity-service — `IdentityVaultServiceIntegrationTest`

Levanta el contexto completo de Spring pero excluye la auto-configuración de Kafka (el servicio produce eventos pero en tests no hay broker disponible). Valida que el vault de identidades genera UUIDs deterministas por identidad y que la resolución inversa funciona:

```java
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration")
@ActiveProfiles("test")
class IdentityVaultServiceIntegrationTest {

    @Autowired private IdentityVaultService vaultService;
    @MockBean @SuppressWarnings("rawtypes") private KafkaTemplate kafkaTemplate;

    @Test void getOrCreateAnonymousId_sameIdentity_returnsSameUuid() { ... }
    @Test void getOrCreateAnonymousId_differentIdentities_returnDifferentUuids() { ... }
    @Test void getOrCreateAnonymousId_returnsValidUuid() { ... }
    @Test void resolveRealIdentity_afterCreate_returnsOriginalIdentity() { ... }
}
```

### promotion-service — `BuildingJpaIntegrationTest`

Usa `@DataJpaTest` con un contenedor PostgreSQL real (TestContainers) en lugar de H2. Flyway se deshabilita y se usa `ddl-auto=create-drop` para que Hibernate genere el esquema desde las entidades:

```java
@Tag("integration")
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class BuildingJpaIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Test void save_building_persistsToRealPostgres() { ... }
    @Test void findByCode_returnsCorrectBuilding() { ... }
    @Test void findAll_returnsAllPersisted() { ... }
    @Test void findFloorsByBuilding_withNoFloors_returnsEmptyList() { ... }
}
```

### dashboard-service — `DashboardControllerIntegrationTest`

Valida los cinco endpoints del `AnalyticsController` con `@WebMvcTest`, verificando tanto el status HTTP como la estructura del JSON de respuesta:

```java
@Tag("integration")
@WebMvcTest(AnalyticsController.class)
class DashboardControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private AnalyticsService analyticsService;

    @Test void getSummary_returns200WithSummaryData() { ... }
    @Test void getDepartmentStats_withValidDepartment_returns200() { ... }
    @Test void getTimeSeries_withDefaultParams_returns200() { ... }
    @Test void getTimeSeries_withDailyPeriod_passesParamToService() { ... }
    @Test void getHealthBoard_returns200() { ... }
}
```

### notification-service — `NotificationKafkaIntegrationTest`

Levanta el contexto completo sin servidor web (`WebEnvironment.NONE`) con mocks para todas las dependencias externas. Llama directamente al método del listener para simular la recepción de un mensaje Kafka:

```java
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class NotificationKafkaIntegrationTest {

    @Autowired private ExposureNotificationListener listener;
    @MockBean private NotificationDispatcher dispatcher;
    @MockBean private LmsService lmsService;
    @MockBean private JavaMailSender mailSender;
    @MockBean @SuppressWarnings("rawtypes") private KafkaTemplate kafkaTemplate;
    @MockBean private WebClient.Builder webClientBuilder;

    @Test
    void handleStatusChange_withSuspectStatus_callsDispatcher() {
        when(lmsService.syncRemoteAttendance(anyString(), anyString()))
            .thenReturn(CompletableFuture.completedFuture(null));

        listener.handleStatusChange(
            "{\"anonymousId\":\"user-001\",\"status\":\"SUSPECT\"}");

        verify(dispatcher).dispatch("user-001", "SUSPECT");
        verify(lmsService).syncRemoteAttendance("user-001", "SUSPECT");
    }

    @Test void handleStatusChange_withActiveStatus_skipsDispatch() { ... }
    @Test void handleStatusChange_withConfirmedStatus_callsDispatcher() { ... }
    @Test void handleStatusChange_withMalformedJson_doesNotThrowException() { ... }
}
```

---

## Paso 4 — Tests E2E (módulo `tests/e2e/`)

Los tests E2E validan flujos completos de usuario contra el Gateway real desplegado en Kubernetes. No usan mocks; cada test realiza peticiones HTTP reales y verifica respuestas.

### Clase base `E2ETestConfig`

Configura `RestAssured.baseURI` desde la system property `gateway.url` y expone un método utilitario `adminToken()` reutilizable en todos los tests:

```java
public abstract class E2ETestConfig {

    @BeforeAll
    static void configure() {
        RestAssured.baseURI = System.getProperty("gateway.url", "http://localhost:8080");
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    protected static String obtainToken(String username, String password) {
        Response response = given()
            .contentType(ContentType.JSON)
            .body("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}")
            .post("/api/v1/auth/login");
        return response.statusCode() == 200
            ? response.jsonPath().getString("token")
            : null;
    }

    protected static String adminToken() {
        return obtainToken(
            System.getProperty("test.admin.user", "admin"),
            System.getProperty("test.admin.password", "password")
        );
    }
}
```

### `AuthE2ETest` — Flujo de autenticación

Valida el ciclo completo de login: credenciales válidas retornan JWT + tipo Bearer + anonymousId, credenciales inválidas retornan 401, y el token obtenido es funcional para endpoints protegidos.

### `HealthStatusE2ETest` — Estado de salud

Valida que el endpoint de estado de salud requiere autenticación (401 sin token, 401 con token malformado) y que con token válido retorna JSON con el campo `status`.

### `ContactRegistrationE2ETest` — Registro de contacto

Valida el flujo de validación de QR: sin token retorna 401, QR inválido retorna 400/404/422, y genera el código QR del usuario con token válido.

### `StatusEscalationE2ETest` — Escalada de estado

Valida el flujo de reporte de síntomas: sin token retorna 401, con token válido acepta el reporte (200/202) y permite consultar el historial de estado. Valida el flujo completo de reporte + consulta de estado.

### `DashboardE2ETest` — Panel de analíticas

Valida los cinco endpoints del dashboard: summary, time-series, health-board y department stats requieren autenticación y retornan JSON estructurado con los campos esperados cuando el usuario tiene permisos.

---

## Paso 5 — Pruebas de Carga con Locust

Locust se ejecuta como un contenedor Docker usando la imagen oficial `locustio/locust`, en modo headless, contra el Gateway desplegado en Kubernetes.

### Estructura

```
locust/
├── Dockerfile       ← FROM locustio/locust; copia archivos de configuración
├── locust.conf      ← parámetros de carga: 50 usuarios, ramp 5/s, 2 min
└── locustfile.py    ← 4 clases de usuario con escenarios de carga
```

### `locust.conf` — Configuración de carga

```ini
headless = true
users = 50
spawn-rate = 5
run-time = 2m
html = /reports/report.html
loglevel = INFO
```

| Parámetro | Valor | Descripción |
|---|---|---|
| `users` | 50 | Usuarios virtuales concurrentes en estado estable |
| `spawn-rate` | 5 | Usuarios nuevos por segundo durante el ramp-up (10s para llegar a 50) |
| `run-time` | 2m | Duración de la prueba una vez alcanzado el estado estable |
| `html` | `/reports/report.html` | Ruta del reporte HTML dentro del contenedor (montado como volumen) |

### `locustfile.py` — Escenarios de carga

Se definen 4 clases de usuario con pesos que reproducen la distribución de carga típica del campus:

| Clase | Escenario | Peso | `wait_time` |
|---|---|---|---|
| `AuthUser` | Login repetido (pico matutino de autenticación) | 3 | 1–3 s |
| `HealthStatusUser` | Consulta continua de estado de salud | 4 | 2–5 s |
| `QRContactUser` | Escaneo de QR en puntos de acceso (3:1 validar vs. generar) | 2 | 1–2 s |
| `EscalationUser` | Reporte de síntomas y monitoreo de escalada | 1 | 5–10 s |

```python
class HealthStatusUser(HttpUser):
    wait_time = between(2, 5)
    weight = 4
    token = None

    def on_start(self):
        resp = self.client.post("/api/v1/auth/login",
            json={"username": ADMIN_USER, "password": ADMIN_PASS})
        if resp.status_code == 200:
            self.token = resp.json().get("token")

    @task
    def get_health_status(self):
        if self.token:
            self.client.get("/api/v1/health/status",
                headers={"Authorization": f"Bearer {self.token}"})
```

### Ejecución local

```bash
# Construir imagen
docker build -t circleguard-locust:latest locust/

# Ejecutar contra el gateway local
docker run --rm \
  -v $(pwd)/locust/reports:/reports \
  -e LOCUST_ADMIN_USER=admin \
  -e LOCUST_ADMIN_PASS=password \
  circleguard-locust:latest \
  --host http://localhost:8080
```

El reporte HTML queda en `locust/reports/report.html` con métricas de RPS, latencia percentil (p50, p95, p99) y tasa de errores por endpoint.

![Reporte HTML de Locust con métricas de carga por endpoint](../screenshots/locust-report.png)

---

## Paso 6 — Pipeline CI/CD Reestructurado (7 stages)

El `Jenkinsfile.dev` pasa de un diseño "paralelo por servicio" (donde cada servicio hacía build + test + docker + deploy en un solo bloque paralelo) a un diseño **secuencial por nivel**, donde todos los servicios avanzan juntos de un nivel al siguiente.

### Comparación de estructura

| Diseño anterior | Diseño nuevo |
|---|---|
| 1 stage paralelo: `Servicios` (auth, identity, ...) | 7 stages secuenciales |
| Cada servicio: build → test → docker → deploy (sin separación) | Build → Unit → Integration → Docker → Deploy → E2E → Load |
| Tests sin separación por tipo | Tests separados por nivel con tags |
| Sin E2E ni carga | E2E + Locust en stages dedicados |

### Visión general del pipeline

```
Checkout
    │
Infraestructura K8s Base
    │
Build (paralelo por servicio)
    │
Unit Tests (paralelo por servicio)   ← @Tag("unit"), falla rápido
    │
Integration Tests (paralelo)          ← @Tag("integration"), TestContainers
    │
Docker Build (paralelo por servicio)
    │
Deploy & Health Check (paralelo)      ← kubectl apply + rollout status
    │
E2E Tests                             ← RestAssured contra Gateway K8s
    │
Load Tests                            ← Locust Docker, 50u / 2min
```

### Stage: Build

Todos los servicios compilan su JAR en paralelo sin ejecutar tests (`-x test`):

```groovy
stage('Build') {
    parallel {
        stage('Build auth-service') {
            steps {
                sh './gradlew :services:circleguard-auth-service:bootJar -x test --no-daemon'
            }
        }
        // ... demás servicios
    }
}
```

### Stage: Unit Tests

Ejecuta la tarea `unitTest` (filtra `@Tag("unit")`) en paralelo para cada servicio. Un fallo marca el build como `UNSTABLE` pero no detiene el pipeline:

```groovy
stage('Unit Tests') {
    parallel {
        stage('Unit: auth-service') {
            steps {
                catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                    sh './gradlew :services:circleguard-auth-service:unitTest --no-daemon'
                }
            }
            post {
                always {
                    junit allowEmptyResults: true, skipPublishingChecks: true,
                          testResults: 'services/circleguard-auth-service/build/test-results/unitTest/*.xml'
                }
            }
        }
        // ... demás servicios
    }
}
```

### Stage: Integration Tests

Ejecuta la tarea `integrationTest` (filtra `@Tag("integration")`). Los tests que usan TestContainers levantan contenedores Docker efímeros (PostgreSQL `postgres:16-alpine`) directamente desde el agente Jenkins:

```groovy
stage('Integration Tests') {
    parallel {
        stage('Integration: promotion-service') {
            steps {
                catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                    sh './gradlew :services:circleguard-promotion-service:integrationTest --no-daemon'
                }
            }
            post {
                always {
                    junit allowEmptyResults: true, skipPublishingChecks: true,
                          testResults: 'services/circleguard-promotion-service/build/test-results/integrationTest/*.xml'
                }
            }
        }
        // ... demás servicios
    }
}
```

### Stage: Docker Build

Igual al diseño anterior: dos tags por servicio (`:BUILD_NUMBER` inmutable y `:dev` estable).

### Stage: Deploy & Health Check

Consolida el deploy y el rollout status en un solo stage paralelo (antes eran sub-stages separados por servicio):

```groovy
stage('Deploy: auth-service') {
    steps {
        sh '''
            sed 's/namespace: circleguard/namespace: circleguard-dev/g' k8s/auth-service/deployment.yaml \
                | sed 's|:latest|:dev|g' | kubectl apply -f -
            sed 's/namespace: circleguard/namespace: circleguard-dev/g' k8s/auth-service/service.yaml \
                | sed 's/nodePort: 30/nodePort: 31/g' | kubectl apply -f -
            kubectl rollout restart deployment/circleguard-auth-service -n ${KUBE_NAMESPACE}
            kubectl rollout status deployment/circleguard-auth-service -n ${KUBE_NAMESPACE} --timeout=300s
        '''
    }
}
```

### Stage: E2E Tests

Obtiene la IP del nodo Kubernetes en tiempo de ejecución y ejecuta el módulo E2E apuntando al NodePort del Gateway (`30087`):

```groovy
stage('E2E Tests') {
    steps {
        script {
            def nodeIp = sh(
                script: "kubectl get nodes -o jsonpath='{.items[0].status.addresses[0].address}'",
                returnStdout: true
            ).trim()
            catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                sh "./gradlew :tests:e2e:test -Dgateway.url=http://${nodeIp}:30087 --no-daemon"
            }
        }
    }
    post {
        always {
            junit allowEmptyResults: true, skipPublishingChecks: true,
                  testResults: 'tests/e2e/build/test-results/test/*.xml'
        }
    }
}
```

### Stage: Load Tests

Construye la imagen de Locust, monta el directorio `locust/reports/` como volumen y publica el reporte HTML en Jenkins con el plugin HTML Publisher:

```groovy
stage('Load Tests') {
    steps {
        script {
            def nodeIp = sh(
                script: "kubectl get nodes -o jsonpath='{.items[0].status.addresses[0].address}'",
                returnStdout: true
            ).trim()
            sh 'mkdir -p locust/reports'
            sh 'docker build -t circleguard-locust:latest locust/'
            catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                sh """
                    docker run --rm \
                      -v \${WORKSPACE}/locust/reports:/reports \
                      -e LOCUST_ADMIN_USER=admin \
                      -e LOCUST_ADMIN_PASS=password \
                      circleguard-locust:latest \
                      --host http://${nodeIp}:30087
                """
            }
        }
    }
    post {
        always {
            publishHTML([
                allowMissing: true,
                alwaysLinkToLastBuild: true,
                keepAll: true,
                reportDir: 'locust/reports',
                reportFiles: 'report.html',
                reportName: 'Locust Load Test Report'
            ])
        }
    }
}
```

### Bloque `post` global

```groovy
post {
    always {
        junit allowEmptyResults: true, skipPublishingChecks: true,
              testResults: 'services/**/build/test-results/**/*.xml, tests/e2e/build/test-results/**/*.xml'
    }
    success  { echo 'Todos los niveles de test pasaron. Servicios desplegados en circleguard-dev.' }
    unstable { echo 'Algunos tests fallaron. Revisar los reportes JUnit y Locust para detalles.' }
    failure  { echo 'Pipeline fallido — revisar los logs del stage correspondiente.' }
}
```

El patrón glob `**/build/test-results/**/*.xml` agrega todos los XML de unit, integration y E2E en el reporte consolidado de Jenkins.

---

## Problemas encontrados y soluciones

### `WeakKeyException` al levantar el contexto de Spring (auth-service)

**Síntoma:** `io.jsonwebtoken.security.WeakKeyException: The specified key byte array is 0 bits` al ejecutar tests con contexto de Spring.

**Causa:** El `application-test.yml` no tenía la propiedad `jwt.secret`. Al crear la clave HMAC con una cadena vacía, JJWT rechazaba el valor por no alcanzar los 256 bits mínimos.

**Solución:** Agregar un secreto de al menos 32 caracteres en el perfil de test:

```yaml
jwt:
  secret: "test-secret-key-for-testing-only-1234567890ab"
```

---

### `@SpringBootTest` fallando por Kafka (notification-service e identity-service)

**Síntoma:** El contexto de Spring no levantaba con `Connection refused` al intentar conectar con un broker Kafka en `localhost:9092`.

**Causa:** Los servicios tienen `@EnableKafka` y producen eventos, pero en el entorno de test no hay broker disponible.

**Solución A (identity-service):** Excluir la auto-configuración de Kafka en la anotación del test:

```java
@SpringBootTest(properties =
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration")
```

**Solución B (notification-service):** Declarar `@MockBean KafkaTemplate` para que Spring lo registre sin intentar conectar al broker real.

---

### TestContainers necesita `@AutoConfigureTestDatabase(replace=NONE)` (promotion-service)

**Síntoma:** `@DataJpaTest` reemplazaba automáticamente el datasource con H2 in-memory, ignorando el `PostgreSQLContainer`.

**Causa:** Por defecto `@DataJpaTest` sustituye cualquier datasource configurado por una base de datos embebida.

**Solución:** Agregar `@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)` para que Spring use el datasource configurado por `@DynamicPropertySource`, que apunta al contenedor PostgreSQL de TestContainers.

---

### `LmsService` sin implementación concreta (notification-service)

**Síntoma:** `NoSuchBeanDefinitionException: No qualifying bean of type 'LmsService'` al levantar el contexto.

**Causa:** `LmsService` es una interfaz con implementación condicional que no se registra en el perfil de test.

**Solución:** Declarar `@MockBean private LmsService lmsService` en la clase de test para que Spring registre un mock en su lugar.

---

### `@WebMvcTest` requiere importar `SecurityConfig` (auth-service)

**Síntoma:** Los endpoints retornaban `403 Forbidden` sin importar las credenciales mockeadas.

**Causa:** `@WebMvcTest` no carga la configuración de seguridad por defecto. Spring Security aplicaba la configuración por defecto que exige autenticación básica para todos los endpoints.

**Solución:** Agregar `@Import(SecurityConfig.class)` para que la configuración de seguridad personalizada (que permite `/api/v1/auth/**` sin autenticación) se aplique durante el test.

---

## Verificación

### Ejecutar tests por nivel

```bash
# Solo tests unitarios de un servicio
./gradlew :services:circleguard-auth-service:unitTest

# Solo tests de integración de un servicio
./gradlew :services:circleguard-promotion-service:integrationTest

# Tests E2E contra el entorno local (servicios corriendo)
./gradlew :tests:e2e:test -Dgateway.url=http://localhost:8080

# Prueba de carga Locust local (2 minutos)
docker build -t circleguard-locust:latest locust/
docker run --rm \
  -v $(pwd)/locust/reports:/reports \
  -e LOCUST_ADMIN_USER=admin \
  -e LOCUST_ADMIN_PASS=password \
  circleguard-locust:latest \
  --host http://localhost:8080
```

### Reportes en Jenkins

Tras una ejecución completa del pipeline, Jenkins expone:

| Reporte | Ubicación en Jenkins |
|---|---|
| Tests unitarios por servicio | Pestaña "Test Results" de cada stage Unit |
| Tests de integración por servicio | Pestaña "Test Results" de cada stage Integration |
| Tests E2E | Pestaña "Test Results" del stage E2E |
| Reporte Locust | Menú lateral "Locust Load Test Report" (HTML Publisher) |
| Resumen global | Pestaña "Test Results" del build (XML de todos los niveles) |

![Pipeline Jenkins con los 7 stages secuenciales de test](../screenshots/jenkins-pipeline-7-stages.png)

![Reportes JUnit unitarios e integración en Jenkins](../screenshots/jenkins-unit-integration-test-results.png)

![Reporte HTML de Locust con métricas de rendimiento](../screenshots/locust-load-test-report.png)
