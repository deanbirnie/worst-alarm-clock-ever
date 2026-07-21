package com.worstalarm.clock.data.entity

import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmEntityTest {

    @Test
    fun `awakeCheckEnabled defaults on for alarms that don't set it explicitly`() {
        // Guards the v3-to-v4 migration's DEFAULT 1: existing alarms (and any code
        // constructing an AlarmEntity without naming this field) must keep awake checks
        // on rather than silently losing the feature.
        val alarm = AlarmEntity(label = "Test", hour = 6, minute = 30, daysMask = 0, enabled = true)
        assertTrue(alarm.awakeCheckEnabled)
    }
}
