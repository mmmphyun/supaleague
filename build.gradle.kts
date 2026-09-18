plugins {
    kotlin("jvm") version "1.9.22" apply false
}

allprojects {
    group = "com.supaleague"
    version = "0.1.0-SNAPSHOT"
}

subprojects {
    if (childProjects.isEmpty()) {
        apply(plugin = "org.jetbrains.kotlin.jvm")

        configure<JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(21))
            }
        }

        tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
            kotlinOptions {
                jvmTarget = "21"
                freeCompilerArgs = listOf("-Xjsr305=strict")
            }
        }

        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
        }

        dependencies {
            "testImplementation"(platform("org.junit:junit-bom:5.10.2"))
            "testImplementation"("org.junit.jupiter:junit-jupiter")
            "testImplementation"("org.assertj:assertj-core:3.25.3")
        }
    }
}

tasks.register("installGitHooks") {
    group = "help"
    description = "Git hooks 경로를 .githooks 디렉토리로 동기화합니다."
    doLast {
        exec {
            commandLine("git", "config", "core.hooksPath", ".githooks")
        }
        println("Git hooks configuration updated: core.hooksPath -> .githooks")
    }
}
