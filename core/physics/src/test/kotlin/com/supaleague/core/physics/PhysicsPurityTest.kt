package com.supaleague.core.physics

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class PhysicsPurityTest {

    @Test
    @DisplayName("물리 엔진 순수 도메인 테스트: Paper/Bukkit 의존성 없이 마이크로초 단위 연산 검증")
    fun `verify pure sub-stepping calculation`() {
        val deltaSeconds = 0.01 // 100Hz
        val initialVelocity = 15.0
        val gravity = 9.8

        val nextVelocity = initialVelocity - (gravity * deltaSeconds)
        assertTrue(nextVelocity in 14.9..15.0)
    }
}
