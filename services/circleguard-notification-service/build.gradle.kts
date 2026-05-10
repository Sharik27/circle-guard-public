plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    kotlin("jvm")
    kotlin("plugin.spring")
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:3.2.4"))
    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:3.2.4"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("com.twilio.sdk:twilio:10.1.1")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-freemarker")
    implementation("org.springframework.retry:spring-retry")
    implementation("org.springframework:spring-aspects")
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")
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
