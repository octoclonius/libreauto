package io.github.octoclonius.libreauto.session

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

inline fun <reified T> decodeSaved(encoded: String?): T? {
    if (encoded == null) return null
    return try {
        Json.decodeFromString<T>(encoded)
    } catch (e: SerializationException) {
        null
    }
}
