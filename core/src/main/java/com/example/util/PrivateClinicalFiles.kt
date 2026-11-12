package com.example.util

import android.content.Context
import androidx.core.content.FileProvider
import android.content.Intent
import java.io.File

/** Only the temporary document cache is cleared; account-scoped encrypted drafts are preserved. */
internal object PrivateClinicalFiles {
    fun clear(context: Context) {
        val directory = File(context.cacheDir, "medical-documents")
        directory.listFiles()?.forEach { file ->
            runCatching {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.documents", file)
                context.revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        directory.deleteRecursively()
    }
}
