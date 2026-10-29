package io.github.octoclonius.libreauto.projection.datastore

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import java.io.InputStream
import java.io.OutputStream
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

fun <T> jsonDataStore(
    context: Context,
    name: String,
    serializer: KSerializer<T>,
    defaultValue: T,
): DataStore<T> =
    DataStoreFactory.create(
        serializer = JsonSerializer(serializer, defaultValue),
        corruptionHandler = ReplaceFileCorruptionHandler { defaultValue },
        produceFile = { context.dataStoreFile("$name.json") },
    )

internal class JsonSerializer<T>(
    private val serializer: KSerializer<T>,
    override val defaultValue: T,
) : Serializer<T> {

    override suspend fun readFrom(input: InputStream): T =
        try {
            Json.decodeFromString(serializer, input.readBytes().decodeToString())
        } catch (e: SerializationException) {
            throw CorruptionException("Unreadable saved data", e)
        }

    override suspend fun writeTo(t: T, output: OutputStream) {
        output.write(Json.encodeToString(serializer, t).encodeToByteArray())
    }
}
