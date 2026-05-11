plugins {
    java
}

repositories {
    mavenCentral()
}

// El módulo E2E no usa Lombok; se excluye para evitar que la versión heredada
// del bloque subprojects{} raíz quede sin resolver (no hay dependency-management aquí).
configurations.all {
    exclude(group = "org.projectlombok", module = "lombok")
}

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
