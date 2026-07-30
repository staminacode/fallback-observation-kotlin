import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.tasks.compile.JavaCompile

plugins {
    kotlin("jvm") version "2.1.21" apply false
}

allprojects {
    group = "io.github.staminacode"
    version = "0.1.0-SNAPSHOT"
    description = "Observability primitives for fallback execution."

    repositories {
        mavenCentral()
    }
}

subprojects {
    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<KotlinJvmProjectExtension> {
            jvmToolchain(26)
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_21)
            }
        }

        tasks.withType<JavaCompile>().configureEach {
            options.release.set(21)
        }
    }
}
