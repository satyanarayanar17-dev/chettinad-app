package com.example.util

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PrivateClinicalFilesTest {
    @Test fun accountCleanupErasesOnlyTemporaryMedicalDocuments() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val medical = File(context.cacheDir, "medical-documents").apply { mkdirs() }
        File(medical, "report.pdf").writeText("synthetic private report")
        val other = File(context.cacheDir, "unrelated-cache.txt").apply { writeText("keep") }
        PrivateClinicalFiles.clear(context)
        assertFalse(medical.exists())
        assertEquals("keep", other.readText())
        other.delete()
    }
}
