package com.example.portal
import org.junit.Assert.*
import org.junit.Test
class CareCompletionTest {
    @Test fun emptyOrIncompletePlansHaveNoOverallCheck() {
        assertFalse(careCompletion(emptyList()).allCompleted)
        for (status in listOf("DUE","UPCOMING","MISSED","SKIPPED")) assertFalse(careCompletion(listOf("COMPLETED",status)).allCompleted)
    }
    @Test fun allCompletedPlanHasOverallCheckAndCount() {
        val progress = careCompletion(listOf("COMPLETED","COMPLETED","COMPLETED"))
        assertTrue(progress.allCompleted); assertEquals(3,progress.completed); assertEquals(3,progress.total)
    }
}
