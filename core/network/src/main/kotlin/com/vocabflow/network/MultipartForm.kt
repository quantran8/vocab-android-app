package com.vocabflow.network

import java.io.ByteArrayOutputStream
import java.util.UUID

/** `multipart/form-data` body for the recording upload (iOS `MultipartForm`). Built by hand so it is byte-stable. */
class MultipartForm {
    val boundary: String = "VocabFlow-${UUID.randomUUID().toString().uppercase()}"
    private val body = ByteArrayOutputStream()

    val contentType: String get() = "multipart/form-data; boundary=$boundary"

    fun addField(name: String, value: String) {
        append("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n")
    }

    fun addFile(name: String, filename: String, contentType: String, data: ByteArray) {
        append("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"; filename=\"$filename\"\r\n")
        append("Content-Type: $contentType\r\n\r\n")
        body.write(data)
        append("\r\n")
    }

    fun finished(): ByteArray = body.toByteArray() + "--$boundary--\r\n".toByteArray(Charsets.UTF_8)

    private fun append(text: String) {
        body.write(text.toByteArray(Charsets.UTF_8))
    }
}
