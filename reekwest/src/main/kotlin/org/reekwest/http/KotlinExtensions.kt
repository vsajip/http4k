package org.reekwest.http

import java.nio.ByteBuffer

fun ByteBuffer.asString(): String = String(array())

fun String.asByteBuffer(): ByteBuffer = ByteBuffer.wrap(this.toByteArray())

fun String.quoted() = "\"${this.replace("\"", "\\\"")}\""

fun StringBuilder.appendIfNotBlank(valueToCheck: String, vararg toAppend: String): StringBuilder =
    appendIf({ valueToCheck.isNotBlank() }, *toAppend)

fun StringBuilder.appendIfNotEmpty(valueToCheck: List<Any>, vararg toAppend: String): StringBuilder =
    appendIf({ valueToCheck.isNotEmpty() }, *toAppend)

fun StringBuilder.appendIfPresent(valueToCheck: Any?, vararg toAppend: String): StringBuilder =
    appendIf({ valueToCheck != null }, *toAppend)

fun StringBuilder.appendIf(condition: () -> Boolean, vararg toAppend: String): StringBuilder {
    if (condition()) toAppend.forEach { append(it) }
    return this
}