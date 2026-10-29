package io.github.octoclonius.libreauto.protocol

fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
