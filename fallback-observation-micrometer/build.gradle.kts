plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":fallback-observation-core"))
    implementation("io.micrometer:micrometer-core:1.15.0")

    testImplementation(kotlin("test"))
    testImplementation("io.micrometer:micrometer-core:1.15.0")
}

tasks.test {
    useJUnitPlatform()
}
