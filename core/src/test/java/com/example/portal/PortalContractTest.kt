package com.example.portal

import com.example.data.api.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class PortalContractTest {
    private val server = MockWebServer()
    private lateinit var api: PortalApi
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    @Before fun setup() {
        server.start()
        api = Retrofit.Builder().baseUrl(server.url("/api/v1/"))
            .addConverterFactory(MoshiConverterFactory.create(moshi).withNullSerialization()).build().create(PortalApi::class.java)
    }
    @After fun close() { server.shutdown() }
    @Test fun `clinical records retain notes prescriptions and lab results`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"patient":{"id":"patient-1","mrn":"MRN-1","name":"Synthetic patient","phone":"+919100000001","dob":"1990-01-01","gender":"Female","__v":1},"notes":[{"id":"note-1","encounter_id":"encounter-1","status":"FINALIZED","__v":2,"draft_content":{"history":"History kept","diagnoses":[{"id":"dx-1","name":"Test diagnosis"}]}}],"prescriptions":[{"id":"rx-1","encounter_id":"encounter-1","status":"AUTHORIZED","created_at":"2026-10-15T00:00:00Z","rx_content":{"medications":[{"drug_id":"drug-1","name":"Synthetic medicine","dose":"As prescribed","frequency":"Daily","duration":"3 days"}]}}],"labs":[{"id":"lab-1","encounter_id":"encounter-1","test_id":"test-1","status":"AVAILABLE","__v":3,"result":{"value":"112","unit":"mg/dL","reference_range":"lab reference","flag":"NORMAL"}}]}"""))
        val bundle = api.record("patient-1")
        assertEquals("History kept", bundle.notes.single().draft_content?.history)
        assertEquals("Synthetic medicine", bundle.prescriptions.single().rx_content?.medications?.single()?.name)
        assertEquals("112", bundle.labs.single().result?.value)
        assertEquals("/api/v1/opd/patients/patient-1/record", server.takeRequest().path)
    }
    @Test fun `self records use backend numeric keys and preserve patient provenance`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"id":"self-1","record_type":"GLUCOSE","numeric_value":"112.5","observed_at":"2026-10-15T00:00:00Z"}"""))
        val result = api.addSelfRecord("patient-1", mapOf("record_type" to "GLUCOSE", "numeric_value" to 112.5, "timing_context" to "FASTING", "unit" to "mg/dL", "observed_at" to "2026-10-15T00:00:00Z"))
        assertEquals(112.5, result.numeric_value!!, 0.0)
        val request = server.takeRequest()
        assertTrue(request.body.readUtf8().contains("numeric_value"))
        assertEquals("/api/v1/opd/patient-self-records/patient-1", request.path)
    }
    @Test fun `document detachment sends explicit null and uses patient scoped path`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"id":"doc-1","title":"Synthetic report","document_type":"LAB_REPORT","original_filename":"report.pdf","mime_type":"application/pdf","clinical_date":"2026-10-15","size_bytes":40,"appointment_id":null}"""))
        api.linkDocument("patient-1", "doc-1", mapOf("appointment_id" to null))
        val request = server.takeRequest()
        assertEquals("PATCH", request.method)
        assertEquals("{\"appointment_id\":null}", request.body.readUtf8())
    }
    @Test fun `queue carries distinct patient encounter and version`() = runBlocking {
        server.enqueue(MockResponse().setBody("""[{"encounter_id":"enc-1","patient_id":"pat-1","patient_name":"Synthetic","mrn":"MRN-1","status":"TRIAGE","__v":4}]"""))
        val queue = api.queue().single()
        assertNotEquals(queue.patient_id, queue.encounter_id)
        server.takeRequest()
        server.enqueue(MockResponse().setBody("{\"success\":true}"))
        api.encounterAction(queue.encounter_id, "triage", mapOf("__v" to queue.__v, "data" to mapOf("complaint" to "Synthetic")))
        val request = server.takeRequest()
        assertEquals("/api/v1/opd/encounters/enc-1/triage", request.path)
        assertTrue(request.body.readUtf8().contains("\"__v\":4"))
    }
    @Test fun `clinical safety messages keep the medicine name visible`() {
        val body = """{"error":{"code":"ALLERGY_WARNING","message":"Allergy warning: review Synthetic medicine."}}"""
        val error = retrofit2.HttpException(retrofit2.Response.error<Any>(409,
            body.toResponseBody("application/json".toMediaType())))
        assertTrue(errorMessage(error).contains("Synthetic medicine"))
    }
    @Test fun `weekly task edits preserve the prescribed recurrence and response window`() {
        val existing = CareTask("task", "Medication", "Original instruction", "MEDICATION", "ACTIVE", 2, "08:00", "2026-10-15", frequency_type = "SELECTED_DAYS", weekdays_mask = 42, window_before_minutes = 10, window_after_minutes = 30, expected_duration_minutes = 20, reminder_enabled = false)
        val payload = CareSchedule.from(existing).payload(existing.start_date)
        assertEquals("SELECTED_DAYS", payload["frequency_type"])
        assertEquals(42, payload["weekdays_mask"])
        assertEquals(30, payload["window_after_minutes"])
        assertEquals(20, payload["expected_duration_minutes"]); assertEquals(false, payload["reminder_enabled"])
    }
    @Test fun `one-off task edits retain the occurrence date`() {
        val existing = CareTask("task", "Task", "Instruction", "ACTIVITY", "ACTIVE", 1, "08:00", "2026-10-15", frequency_type = "ONCE", one_time_date = "2026-10-20")
        val payload = CareSchedule.from(existing).payload(existing.start_date)
        assertEquals("ONCE", payload["frequency_type"]); assertEquals("2026-10-20", payload["one_time_date"])
        assertFalse(payload.containsKey("weekdays_mask"))
    }
    @Test fun `switching recurrence removes incompatible fields and validates date windows`() {
        val weekly = CareSchedule(frequency = "SELECTED_DAYS", weekdays = 1 or (1 shl 6))
        assertEquals(65, weekly.payload("2026-10-16")["weekdays_mask"])
        val interval = weekly.copy(frequency = "EVERY_N_DAYS", interval = "3")
        assertFalse(interval.payload("2026-10-16").containsKey("weekdays_mask"))
        assertEquals(3, interval.payload("2026-10-16")["interval_days"])
        assertNotNull(interval.copy(interval = "0").validationError("2026-10-16"))
        assertNotNull(weekly.copy(weekdays = 0).validationError("2026-10-16"))
        assertNotNull(CareSchedule(frequency = "ONCE", onceDate = "2026-10-15").validationError("2026-10-16"))
        assertNotNull(CareSchedule(frequency = "ONCE", onceDate = "2026-10-20", endDate = "2026-10-19").validationError("2026-10-16"))
        assertNotNull(CareSchedule(before = "1441").validationError("2026-10-16"))
        assertNotNull(CareSchedule(duration = "0").validationError("2026-10-16"))
    }
    @Test fun `open care forms cannot silently adopt a newer plan or task revision`() {
        val task = CareTask("task", "Task", "Reviewed instruction", "ACTIVITY", "ACTIVE", 2, "08:00", "2026-10-15")
        val care = CarePlanBundle(CarePlan("plan", "ACTIVE", 7, "2026-10-15"), listOf(task))
        val review = CareTaskReview.capture(care, task)
        assertTrue(review.matches(care))
        assertFalse(review.matches(care.copy(plan = care.plan!!.copy(__v = 8))))
        assertFalse(review.matches(care.copy(tasks = listOf(task.copy(revision = 3)))))
        assertFalse(review.matches(care.copy(tasks = listOf(task.copy(status = "SUPERSEDED")))))
        assertFalse(CareTaskReview.capture(CarePlanBundle(), null).matches(care))
    }
    @Test fun `amendment sends independent note and patient versions and an explicit reason`() = runBlocking {
        server.enqueue(MockResponse().setBody("{\"success\":true}"))
        api.amend("encounter-1", AmendmentWrite(2, ConsultationData(history = "Clarified history").forWrite(), "Reviewed with patient", 7))
        val request = server.takeRequest()
        assertEquals("/api/v1/opd/encounters/encounter-1/amend", request.path)
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"patient__v\":7")); assertTrue(body.contains("\"__v\":2"))
        assertTrue(body.contains("Reviewed with patient")); assertTrue(body.contains("Clarified history"))
    }
    @Test fun `local reminders honor doctor task policy and cancel when policy cannot be refreshed`() {
        val task = CareTask("task", "Task", "Instruction", "ACTIVITY", "ACTIVE", 1, "10:00", "2026-10-16", reminder_enabled = 0.0)
        val occurrence = CareOccurrence("task", "task", "Task", "ACTIVITY", "UPCOMING", occurrence_date = "2026-10-16", due_time = "10:00", scheduled_for = "2026-10-16T04:30:00Z", __v = 0)
        val state = PortalState(care = CarePlanBundle(CarePlan("plan", "ACTIVE", 2, "2026-10-16"), listOf(task)), today = listOf(occurrence))
        assertTrue(reminderTasks(state).isEmpty())
        val enabled = state.copy(care = state.care.copy(tasks = listOf(task.copy(reminder_enabled = 1.0))))
        assertEquals(listOf(occurrence), reminderTasks(enabled))
        assertTrue(reminderTasks(enabled.copy(errors = mapOf("care plan" to "Unable to refresh"))).isEmpty())
        assertTrue(reminderTasks(enabled.copy(care = CarePlanBundle())).isEmpty())
        assertTrue(reminderTasks(enabled.copy(care = enabled.care.copy(tasks = listOf(task.copy(status = "SUPERSEDED", reminder_enabled = true))))).isEmpty())
    }
    @Test fun `date validation rejects impossible calendar dates`() {
        assertTrue(validDate("2024-02-29")); assertFalse(validDate("2026-02-29")); assertFalse(validDate("2026-2-01"))
    }
    @Test fun `consultation signing carries both independent concurrency versions`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"id":"note-1","__v":3}"""))
        api.complete("encounter-1", ConsultationWrite(2, ConsultationData(), patient__v = 7))
        val request = server.takeRequest()
        assertEquals("/api/v1/opd/encounters/encounter-1/complete", request.path)
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"patient__v\":7")); assertTrue(body.contains("\"__v\":2"))
    }
    @Test fun `profile edits carry the patient version and only chosen demographic fields`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"success":true}"""))
        api.updateProfile("patient-1", mapOf("__v" to 7, "data" to mapOf("email" to "synthetic@example.com", "city" to "Chennai")))
        val request = server.takeRequest()
        assertEquals("PATCH", request.method)
        assertEquals("/api/v1/opd/patients/patient-1", request.path)
        assertEquals("""{"__v":7,"data":{"email":"synthetic@example.com","city":"Chennai"}}""", request.body.readUtf8())
    }
    @Test fun `password validation matches the hospital character and length policy`() {
        assertTrue(validStaffPassword("Synthetic2026!"))
        assertFalse(validStaffPassword("Short1!")); assertFalse(validStaffPassword("synthetic2026!"))
        assertFalse(validStaffPassword("SyntheticOnly!")); assertFalse(validStaffPassword("Synthetic2026"))
        assertFalse(validStaffPassword("A".repeat(73) + "a1!"))
    }
    @Test fun `signed prescriptions retain the historical patient identity`() {
        val content = moshi.adapter(PrescriptionContent::class.java).fromJson("""{"patient_snapshot":{"id":"patient-1","mrn":"SIGNED-MRN","name":"Name when signed","dob":"1990-01-01","gender":"Female","__v":7}}""")!!
        assertEquals("Name when signed", content.patient_snapshot?.name)
        assertEquals("SIGNED-MRN", content.patient_snapshot?.mrn)
        assertEquals(7, content.patient_snapshot?.__v)
    }
    @Test fun `blank consultation fields and recovered legacy drafts send valid optional text`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"id":"note-1","__v":1}"""))
        val draft = ConsultationData(history = "History kept", medications = listOf(Medication("drug-1", "One", "Daily", "3 days")))
        api.saveNote("encounter-1", ConsultationWrite(0, draft.forWrite(), 7))
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"previous_history\":\"\"")); assertTrue(body.contains("\"treatment\":\"\""))
        assertTrue(body.contains("\"instructions\":\"\"")); assertTrue(body.contains("\"history\":\"History kept\""))
        assertFalse(body.contains("\"complaint\":null")); assertTrue(body.contains("\"follow_up\":null"))
    }
    @Test fun `legacy sessions with missing device metadata remain revocable`() = runBlocking {
        server.enqueue(MockResponse().setBody("""[{"id":"session-1","device_name":null,"created_at":null,"expires_at":"2026-10-16T00:00:00Z","current":false}]"""))
        val session = api.sessions().single()
        assertEquals("session-1", session.id); assertNull(session.device_name)
        server.takeRequest()
        server.enqueue(MockResponse().setBody("""{"success":true}"""))
        api.revokeSession(session.id)
        val request = server.takeRequest()
        assertEquals("DELETE", request.method); assertEquals("/api/v1/auth/opd/sessions/session-1", request.path)
    }
}
