plugins {
    kotlin("jvm")
}

dependencies {
    implementation("org.slf4j:slf4j-api:2.0.18")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
