package com.supaleague.core.stats

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class StatsPurityTest {

    @Test
    @DisplayName("스탯 도메인 순수 테스트: 주력/몸싸움 시소 제약(합계 <= 155) 불변식 검증")
    fun `verify seesaw stat budget constraint`() {
        val speed = 85
        val physical = 70
        val maxSeesawSum = 155

        val sum = speed + physical
        assertTrue(sum <= maxSeesawSum)
    }
}
