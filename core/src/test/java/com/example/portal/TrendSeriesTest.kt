package com.example.portal

import com.example.data.api.*
import org.junit.Assert.*
import org.junit.Test

class TrendSeriesTest {
    @Test fun incompatibleGlucoseUnitsRemainSeparate() {
        val records = listOf(SelfRecord("self", "GLUCOSE", "2026-10-15T00:00:00Z", 108.0, unit = "mg/dL"))
        val timeline = listOf(AdherenceEntry("care", "RECORDED", "2026-10-15", recorded_at = "2026-10-15T01:00:00Z",
            observation = Observation("GLUCOSE", "2026-10-15T01:00:00Z", listOf(ObservationComponent("GLUCOSE", 6.0, "mmol/L")))))
        val series = trendSeries("GLUCOSE", records, timeline)
        assertEquals(2, series.size)
        assertEquals(listOf(108.0), series.single { it.unit == "mg/dL" }.points.map { it.second })
        assertEquals(listOf(6.0), series.single { it.unit == "mmol/L" }.points.map { it.second })
    }
    @Test fun bloodPressureShowsBothComponentsAsSeparateSeries() {
        val records = listOf(SelfRecord("self", "BLOOD_PRESSURE", "2026-10-15T00:00:00Z", 120.0, 80.0, "mmHg"))
        val series = trendSeries("BLOOD_PRESSURE", records, emptyList())
        assertEquals(120.0, series.single { it.component == "SYSTOLIC" }.points.single().second, 0.0)
        assertEquals(80.0, series.single { it.component == "DIASTOLIC" }.points.single().second, 0.0)
    }
    @Test fun unrelatedAndNonFiniteReadingsAreExcluded() {
        val records = listOf(SelfRecord("glucose", "GLUCOSE", "2026-10-15T00:00:00Z", 110.0, unit = "mg/dL"),
            SelfRecord("bad", "GLUCOSE", "2026-10-15T00:00:00Z", Double.NaN, unit = "mg/dL"),
            SelfRecord("weight", "WEIGHT", "2026-10-15T00:00:00Z", 60.0, unit = "kg"))
        val series = trendSeries("GLUCOSE", records, emptyList()).single()
        assertEquals("mg/dL", series.unit); assertEquals(1, series.points.size)
    }
}
