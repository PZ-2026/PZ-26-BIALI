import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
        java
        id("org.springframework.boot") version "3.1.7"
        id("io.spring.dependency-management") version "1.1.7"
}

group = "biali.fitmanager"
version = "0.0.1-SNAPSHOT"

java {
        toolchain {
                languageVersion = JavaLanguageVersion.of(17)
        }
}

repositories {
        mavenCentral()
}

dependencies {
        implementation("org.springframework.boot:spring-boot-starter-security")
        implementation("org.springframework.boot:spring-boot-starter-web")
        implementation("org.springframework.boot:spring-boot-starter-data-jpa")
        runtimeOnly("org.postgresql:postgresql")
        runtimeOnly("com.h2database:h2")
        testImplementation("org.springframework.boot:spring-boot-starter-test")
        testImplementation("org.springframework.security:spring-security-test")
        testRuntimeOnly("com.h2database:h2")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")
        implementation("io.jsonwebtoken:jjwt-api:0.11.5")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.11.5")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.11.5")
        implementation("com.github.librepdf:openpdf:1.3.30")
}

tasks.withType<Test> {
        useJUnitPlatform()
        testLogging {
                events(
                        org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED,
                        org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED,
                        org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED,
                        org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_OUT,
                        org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_ERROR
                )
                showStandardStreams = true
                exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
}

// Workaround: disable bootJar task to avoid Gradle/Spring Boot plugin incompatibility
// Disable building the fat bootJar to avoid runtime Gradle API mismatch in this environment.
// The app can still be run with `bootRun` during development.
tasks.withType<BootJar> {
        enabled = false
}

tasks.withType<org.gradle.jvm.tasks.Jar> {
        enabled = true
}

// Ensure bootJar task is disabled by name as an extra safeguard
tasks.named("bootJar") {
        enabled = false
}
