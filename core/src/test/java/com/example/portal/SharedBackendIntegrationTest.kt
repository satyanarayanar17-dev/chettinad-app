package com.example.portal

import com.example.data.api.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/** Real HTTP regression against tests/start-clinical.cjs, never a live hospital. */
class SharedBackendIntegrationTest {
    @Test fun `patient and clinical clients share records versions and permissions`() = runBlocking {
        val base = System.getenv("CC_SHARED_FIXTURE_BASE")
        assumeTrue("Requires disposable clinical fixture", base == "http://127.0.0.1:3005/api/v1/")
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        fun client(token: String? = null): Retrofit = Retrofit.Builder().baseUrl(base!!)
            .client(OkHttpClient.Builder().addInterceptor { chain ->
                val request = chain.request().newBuilder()
                token?.let { request.header("Authorization", "Bearer $it") }
                chain.proceed(request.build())
            }.build())
            .addConverterFactory(MoshiConverterFactory.create(moshi).withNullSerialization()).build()
        val auth = client().create(ChettinadApiService::class.java)
        suspend fun login(username: String): PortalApi {
            val session = auth.loginStaff(mapOf("username" to username, "password" to "ChettinadDemo2026!"))
            return client(session.access_token).create(PortalApi::class.java)
        }
        suspend fun denied(status: Int, operation: suspend () -> Unit) {
            try { operation(); fail("Expected HTTP $status") } catch (error: HttpException) { assertEquals(status, error.code()) }
        }
        val doctor = login("demo_doctor"); val nurse = login("demo_nurse")
        val active = doctor.queue().first { it.status == "CONSULTATION" }
        val record = doctor.record(active.patient_id)
        val catalogues = doctor.catalogues()
        val note = record.notes.first { it.encounter_id == active.encounter_id }
        val draft = ConsultationData(history = "Android shared handoff history", examination = "Synthetic examination",
            assessment = "Synthetic assessment", advice = "Synthetic advice", diagnosis_ids = listOf(catalogues.diagnoses.first().id),
            medications = listOf(Medication(catalogues.drugs.first().id, "1 tablet", "Daily", "3 days")))
        val write = ConsultationWrite(note.__v, draft.forWrite(), record.patient.__v)
        val saved = doctor.saveNote(active.encounter_id, write)
        val otherClient = login("demo_doctor")
        assertEquals("Android shared handoff history", otherClient.record(active.patient_id).notes.first { it.id == saved.id }.draft_content?.history)
        denied(409) { otherClient.saveNote(active.encounter_id, write) }
        denied(403) { nurse.complete(active.encounter_id, write.copy(__v = saved.__v)) }
        doctor.complete(active.encounter_id, write.copy(__v = saved.__v))
        val signed = otherClient.record(active.patient_id)
        assertEquals("AUTHORIZED", signed.prescriptions.first { it.encounter_id == active.encounter_id }.status)
        assertEquals(record.patient.name, signed.prescriptions.first { it.encounter_id == active.encounter_id }.rx_content?.patient_snapshot?.name)

        val otp = auth.requestOtp(mapOf("phone" to "+919000000001"))
        val session = auth.verifyOtp(mapOf("phone" to "+919000000001", "code" to requireNotNull(otp.development_code), "challenge_id" to requireNotNull(otp.challenge_id)))
        val patient = client(session.access_token).create(PortalApi::class.java)
        val profile = patient.profile()
        assertEquals("Ananya Raman (Demo)", profile.name)
        patient.updateProfile(profile.id, mapOf("__v" to profile.__v, "data" to mapOf("city" to "Synthetic cross-client city")))
        assertEquals("Synthetic cross-client city", nurse.record(profile.id).patient.city)
        assertEquals(profile.__v + 1, patient.profile().__v)
        denied(409) { patient.updateProfile(profile.id, mapOf("__v" to profile.__v, "data" to mapOf("city" to "Stale edit"))) }
        denied(404) { patient.record(active.patient_id) }
        denied(403) { patient.staff() }
        val changed = patient.addSelfRecord(profile.id, mapOf("record_type" to "GLUCOSE", "numeric_value" to 112.5,
            "timing_context" to "FASTING", "unit" to "mg/dL", "observed_at" to "${java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata"))}T04:30:00Z"))
        assertEquals(changed.id, nurse.selfRecords(profile.id).first().id)
        assertEquals(112.5, nurse.selfRecords(profile.id).first().numeric_value!!, 0.0)
    }
}
