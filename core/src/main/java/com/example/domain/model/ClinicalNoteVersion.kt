package com.example.domain.model

/**
 * Represents the concurrency version of a clinical note retrieved from the server.
 */
data class ClinicalNoteVersion(
    val etag: String?
)
