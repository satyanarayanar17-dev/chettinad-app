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
    @Test fun `date validation rejects impossible calendar dates`() {
        assertTrue(validDate("2024-02-29")); assertFalse(validDate("2026-02-29")); assertFalse(validDate("2026-2-01"))
    }
}
