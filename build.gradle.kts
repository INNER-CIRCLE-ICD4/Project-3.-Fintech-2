plugins {
	kotlin("jvm") version "2.0.21"
	kotlin("plugin.spring") version "2.0.21"
	id("org.springframework.boot") version "3.5.3"
	id("io.spring.dependency-management") version "1.1.7"
	kotlin("plugin.jpa") version "2.0.21"
	kotlin("plugin.allopen") version "2.0.21"
	id("io.gitlab.arturbosch.detekt") version("1.23.8")
}

group = "fintech2"
version = "0.0.1-SNAPSHOT"

detekt {
	toolVersion = "1.23.8"
	config.setFrom(file("config/detekt/detekt.yml"))
	buildUponDefaultConfig = true
}

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

configurations {
	compileOnly {
		extendsFrom(configurations.annotationProcessor.get())
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.flywaydb:flyway-core")
	compileOnly("org.projectlombok:lombok")
	developmentOnly("org.springframework.boot:spring-boot-devtools")
	annotationProcessor("org.projectlombok:lombok")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	// detekt ktlint
	detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.8")
}

tasks.withType<Test> {
	useJUnitPlatform()
}
