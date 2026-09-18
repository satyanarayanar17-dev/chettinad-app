package com.example.domain.model

import java.util.UUID

/**
 * Represents the scope of a logical write operation.
 * Used to ensure idempotency across network retries.
 */
data class WriteOperationContext(
    val idempotencyKey: String
) {
    companion object {
        fun create(): WriteOperationContext = WriteOperationContext(UUID.randomUUID().toString())
    }
}
