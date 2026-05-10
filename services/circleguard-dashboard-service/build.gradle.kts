plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:3.2.4"))
    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:3.2.4"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.flywaydb:flyway-core")
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.register<Test>("unitTest") {
    description = "Runs unit tests only (@Tag(\"unit\"))"
    group = "verification"
    useJUnitPlatform { includeTags("unit") }
}

tasks.register<Test>("integrationTest") {
    description = "Runs integration tests only (@Tag(\"integration\"))"
    group = "verification"
    useJUnitPlatform { includeTags("integration") }
}
