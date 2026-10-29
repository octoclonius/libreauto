package io.github.octoclonius.libreauto.protocol

object TestCipher {
    private const val MARKER: Byte = 0x17

    fun encrypt(plaintext: ByteArray): ByteArray =
        byteArrayOf(MARKER) + ByteArray(plaintext.size) { plaintext[it].toInt().inv().toByte() }

    fun decrypt(record: ByteArray): ByteArray? =
        if (record.isEmpty() || record[0] != MARKER) null
        else ByteArray(record.size - 1) { record[it + 1].toInt().inv().toByte() }
}
