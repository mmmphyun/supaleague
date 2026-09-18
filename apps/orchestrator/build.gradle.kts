// Docker Engine API 기반 인스턴스 라이프사이클 오케스트레이터 데몬
plugins {
    application
}

application {
    mainClass.set("com.supaleague.orchestrator.OrchestratorApplicationKt")
}

dependencies {
    implementation(kotlin("stdlib"))
}
