plugins {
        java
        id("org.springframework.boot") version "3.2.4"
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
        testImplementation("org.springframework.boot:spring-boot-starter-test")
        testImplementation("org.springframework.security:spring-security-test")
        testRuntimeOnly("com.h2database:h2")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")
        implementation("io.jsonwebtoken:jjwt-api:0.11.5")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.11.5")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.11.5")
}

tasks.withType<Test> {
        useJUnitPlatform()
}
