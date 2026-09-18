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

val checkDomainPurity by tasks.registering {
    group = "verification"
    description = "코어 모듈(:core:*)의 Bukkit/Spigot/Paper/Velocity 의존성 유입을 검증하는 아키텍처 하드 가드"
    doLast {
        val forbiddenKeywords = listOf("paper", "spigot", "bukkit", "velocity")
        subprojects.filter { it.path.startsWith(":core:") }.forEach { coreProj ->
            coreProj.configurations.forEach { config ->
                config.dependencies.forEach { dep ->
                    val depIdentifier = "${dep.group}:${dep.name}".lowercase()
                    for (keyword in forbiddenKeywords) {
                        if (depIdentifier.contains(keyword)) {
                            throw GradleException(
                                "Architecture Invariant Violation in '${coreProj.path}': " +
                                "Configuration '${config.name}' contains forbidden dependency '$depIdentifier'. " +
                                "Core domain modules must remain 100% pure Kotlin/JVM!"
                            )
                        }
                    }
                }
            }
        }
        println("[Hard Guard] checkDomainPurity: 모든 :core:* 모듈의 도메인 순수성 검증 완료.")
    }
}

subprojects {
    tasks.matching { it.name == "check" }.configureEach {
        dependsOn(checkDomainPurity)
    }
}
