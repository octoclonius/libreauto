package io.github.octoclonius.libreauto.session

import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking

internal fun <T> readBack(serializer: JsonSerializer<T>, value: T): T {
    val written = ByteArrayOutputStream()
    runBlocking { serializer.writeTo(value, written) }
    return read(serializer, written.toString())
}

internal fun <T> read(serializer: JsonSerializer<T>, saved: String): T = runBlocking {
    serializer.readFrom(saved.byteInputStream())
}
