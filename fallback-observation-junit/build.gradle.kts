plugins {
    kotlin("jvm")
    `java-library`
}

dependencies {
    api(project(":fallback-observation-core"))
    api("org.junit.jupiter:junit-jupiter-api:6.1.1")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.platform:junit-platform-testkit:6.1.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:6.1.1")
}

tasks.test {
    useJUnitPlatform {
        excludeTags("engine-testkit-fixture")
    }
}
