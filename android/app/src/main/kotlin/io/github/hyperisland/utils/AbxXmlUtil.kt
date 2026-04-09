package io.github.hyperisland.utils

import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.ArrayDeque
import java.util.Base64

internal object AbxXmlDecoder {
    private val ABX_MAGIC = byteArrayOf(0x41, 0x42, 0x58, 0x00)

    private const val XML_START_DOCUMENT = 0
    private const val XML_END_DOCUMENT = 1
    private const val XML_START_TAG = 2
    private const val XML_END_TAG = 3
    private const val XML_TEXT = 4
    private const val XML_CDSECT = 5
    private const val XML_ENTITY_REF = 6
    private const val XML_WHITESPACE = 7
    private const val XML_INSTRUCTION = 8
    private const val XML_COMMENT = 9
    private const val XML_DOCDECL = 10
    private const val XML_ATTRIBUTE = 15

    private const val TYPE_NULL = 1
    private const val TYPE_STRING = 2
    private const val TYPE_STRING_INTERNED = 3
    private const val TYPE_BYTES_HEX = 4
    private const val TYPE_BYTES_BASE64 = 5
    private const val TYPE_INT = 6
    private const val TYPE_INT_HEX = 7
    private const val TYPE_LONG = 8
    private const val TYPE_LONG_HEX = 9
    private const val TYPE_FLOAT = 10
    private const val TYPE_DOUBLE = 11
    private const val TYPE_TRUE = 12
    private const val TYPE_FALSE = 13

    data class Options(
        val includeXmlDeclaration: Boolean = true,
        val newLine: String = "\n",
        val indent: String = "  ",
        val selfCloseEmptyTags: Boolean = true,
        val booleanFormat: BooleanFormat = BooleanFormat.LOWERCASE,
        val trimHexValues: Boolean = true,
        val invalidCodePointStrategy: InvalidCodePointStrategy = InvalidCodePointStrategy.UNICODE_ESCAPE,
    )

    enum class BooleanFormat(
        private val trueLiteral: String,
        private val falseLiteral: String,
    ) {
        LOWERCASE("true", "false"),
        TITLECASE("True", "False"),
        UPPERCASE("TRUE", "FALSE"),
        NUMERIC("1", "0");

        fun render(value: Boolean): String = if (value) trueLiteral else falseLiteral
    }

    enum class InvalidCodePointStrategy {
        UNICODE_ESCAPE,
        DROP,
        REPLACEMENT_CHARACTER,
    }

    class ParseException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

    fun isAbx(bytes: ByteArray): Boolean {
        if (bytes.size < ABX_MAGIC.size) return false
        return ABX_MAGIC.indices.all { index -> bytes[index] == ABX_MAGIC[index] }
    }

    @Throws(ParseException::class)
    fun decode(bytes: ByteArray, options: Options = Options()): String {
        return ByteArrayInputStream(bytes).use { decode(it, options) }
    }

    @Throws(ParseException::class)
    fun decode(input: InputStream, options: Options = Options()): String {
        val reader = Reader(input)
        reader.requireMagic()

        val internedStrings = mutableListOf<String>()
        val tagStack = ArrayDeque<String>()
        val out = StringBuilder()

        if (options.includeXmlDeclaration) {
            out.append("<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>")
            appendNewLine(out, options)
        }

        var insideStartTag = false
        var currentNodeHasContent = false
        var startedDocument = false
        var endedDocument = false

        while (true) {
            val token = reader.readToken() ?: break
            val type = token ushr 4
            val event = token and 0x0F

            val attributeName = if (event == XML_ATTRIBUTE) {
                if (!insideStartTag) {
                    throw ParseException("Attribute encountered outside a tag at byte ${reader.position}")
                }
                reader.readInternedString(internedStrings)
            } else {
                null
            }

            val value = readValue(reader, type, internedStrings, options)
            when (event) {
                XML_START_DOCUMENT -> {
                    if (startedDocument) {
                        throw ParseException("Encountered duplicate START_DOCUMENT at byte ${reader.position}")
                    }
                    startedDocument = true
                }

                XML_END_DOCUMENT -> {
                    endedDocument = true
                    break
                }

                XML_START_TAG -> {
                    val tagName = value.requireString("start tag")
                    if (insideStartTag) {
                        out.append('>')
                        appendNewLine(out, options)
                    }
                    appendIndent(out, tagStack.size, options)
                    tagStack.addLast(tagName)
                    insideStartTag = true
                    currentNodeHasContent = false
                    out.append('<').append(tagName)
                }

                XML_END_TAG -> {
                    if (tagStack.isEmpty()) {
                        throw ParseException("Encountered END_TAG without matching START_TAG at byte ${reader.position}")
                    }
                    val tagName = value.requireString("end tag")
                    val expectedTag = tagStack.removeLast()
                    if (expectedTag != tagName) {
                        throw ParseException("Mismatched end tag </$tagName>; expected </$expectedTag>")
                    }

                    if (insideStartTag) {
                        insideStartTag = false
                        if (options.selfCloseEmptyTags && !currentNodeHasContent) {
                            out.append(" />")
                            appendNewLine(out, options)
                            currentNodeHasContent = true
                            continue
                        }
                        out.append('>')
                        appendNewLine(out, options)
                    }

                    appendIndent(out, tagStack.size, options)
                    out.append("</").append(tagName).append('>')
                    appendNewLine(out, options)
                    currentNodeHasContent = true
                }

                XML_TEXT,
                XML_CDSECT,
                XML_ENTITY_REF -> {
                    if (insideStartTag) {
                        out.append('>')
                        appendNewLine(out, options)
                        insideStartTag = false
                    }
                    currentNodeHasContent = true
                    appendIndent(out, tagStack.size, options)
                    appendEscapedText(out, value.render(options), options)
                    appendNewLine(out, options)
                }

                XML_WHITESPACE -> {
                    if (insideStartTag) {
                        out.append('>')
                        insideStartTag = false
                    }
                    currentNodeHasContent = true
                    appendSanitizedRaw(out, value.render(options), options)
                }

                XML_INSTRUCTION -> {
                    if (insideStartTag) {
                        out.append('>')
                        appendNewLine(out, options)
                        insideStartTag = false
                    }
                    currentNodeHasContent = true
                    appendIndent(out, tagStack.size, options)
                    out.append("<?")
                    appendSanitizedRaw(out, value.render(options), options)
                    out.append("?>")
                    appendNewLine(out, options)
                }

                XML_COMMENT -> {
                    if (insideStartTag) {
                        out.append('>')
                        appendNewLine(out, options)
                        insideStartTag = false
                    }
                    currentNodeHasContent = true
                    appendIndent(out, tagStack.size, options)
                    out.append("<!-- ")
                    out.append(sanitizeComment(value.render(options), options))
                    out.append(" -->")
                    appendNewLine(out, options)
                }

                XML_DOCDECL -> {
                    if (insideStartTag) {
                        out.append('>')
                        appendNewLine(out, options)
                        insideStartTag = false
                    }
                    currentNodeHasContent = true
                    appendIndent(out, tagStack.size, options)
                    out.append("<!DOCTYPE ")
                    appendSanitizedRaw(out, value.render(options), options)
                    out.append('>')
                    appendNewLine(out, options)
                }

                XML_ATTRIBUTE -> {
                    out.append(' ').append(attributeName)
                    val attributeValue = value.render(options)
                    if (attributeValue.isNotEmpty() || value.value != null) {
                        out.append("=\"")
                        appendEscapedAttribute(out, attributeValue, options)
                        out.append('"')
                    }
                }

                else -> throw ParseException("Unknown XML event $event at byte ${reader.position}")
            }
        }

        if (!startedDocument) throw ParseException("ABX stream is missing START_DOCUMENT")
        if (!endedDocument) throw ParseException("ABX stream ended without END_DOCUMENT")
        if (insideStartTag) throw ParseException("ABX stream ended while a start tag was still open")
        if (tagStack.isNotEmpty()) {
            throw ParseException("ABX stream ended with unclosed tags: ${tagStack.joinToString(separator = "/")}")
        }
        if (reader.hasRemainingData()) {
            throw ParseException("ABX document ended with trailing data still present")
        }

        return out.toString()
    }

    private fun readValue(
        reader: Reader,
        type: Int,
        internedStrings: MutableList<String>,
        options: Options,
    ): TypedValue {
        return when (type) {
            TYPE_NULL -> TypedValue(type, null)
            TYPE_STRING -> TypedValue(type, reader.readUtf8String())
            TYPE_STRING_INTERNED -> TypedValue(type, reader.readInternedString(internedStrings))
            TYPE_BYTES_HEX,
            TYPE_BYTES_BASE64 -> TypedValue(type, reader.readLengthPrefixedBytes())
            TYPE_INT -> TypedValue(type, reader.readInt())
            TYPE_INT_HEX -> TypedValue(type, formatHex(reader.readBytes(4), options.trimHexValues))
            TYPE_LONG -> TypedValue(type, reader.readLong())
            TYPE_LONG_HEX -> TypedValue(type, formatHex(reader.readBytes(8), options.trimHexValues))
            TYPE_FLOAT -> TypedValue(type, Float.fromBits(reader.readInt()))
            TYPE_DOUBLE -> TypedValue(type, Double.fromBits(reader.readLong()))
            TYPE_TRUE -> TypedValue(type, true)
            TYPE_FALSE -> TypedValue(type, false)
            else -> throw ParseException("Unknown ABX type $type at byte ${reader.position}")
        }
    }

    private fun appendNewLine(out: StringBuilder, options: Options) {
        if (options.newLine.isNotEmpty()) out.append(options.newLine)
    }

    private fun appendIndent(out: StringBuilder, depth: Int, options: Options) {
        if (depth <= 0 || options.indent.isEmpty()) return
        repeat(depth) { out.append(options.indent) }
    }

    private fun appendEscapedText(out: StringBuilder, text: String, options: Options) {
        appendEscaped(out, text, options, isAttribute = false)
    }

    private fun appendEscapedAttribute(out: StringBuilder, text: String, options: Options) {
        appendEscaped(out, text, options, isAttribute = true)
    }

    private fun appendEscaped(
        out: StringBuilder,
        text: String,
        options: Options,
        isAttribute: Boolean,
    ) {
        var index = 0
        while (index < text.length) {
            val codePoint = Character.codePointAt(text, index)
            when {
                !isValidXmlCodePoint(codePoint) -> appendInvalidCodePoint(out, codePoint, options)
                codePoint == '&'.code -> out.append("&amp;")
                codePoint == '<'.code -> out.append("&lt;")
                codePoint == '>'.code -> out.append("&gt;")
                isAttribute && codePoint == '"'.code -> out.append("&quot;")
                isAttribute && codePoint == '\''.code -> out.append("&apos;")
                else -> out.append(String(Character.toChars(codePoint)))
            }
            index += Character.charCount(codePoint)
        }
    }

    private fun appendSanitizedRaw(out: StringBuilder, text: String, options: Options) {
        var index = 0
        while (index < text.length) {
            val codePoint = Character.codePointAt(text, index)
            if (isValidXmlCodePoint(codePoint)) {
                out.append(String(Character.toChars(codePoint)))
            } else {
                appendInvalidCodePoint(out, codePoint, options)
            }
            index += Character.charCount(codePoint)
        }
    }

    private fun appendInvalidCodePoint(out: StringBuilder, codePoint: Int, options: Options) {
        when (options.invalidCodePointStrategy) {
            InvalidCodePointStrategy.UNICODE_ESCAPE -> out.append(formatUnicodeEscape(codePoint))
            InvalidCodePointStrategy.DROP -> Unit
            InvalidCodePointStrategy.REPLACEMENT_CHARACTER -> out.append('\uFFFD')
        }
    }

    private fun sanitizeComment(comment: String, options: Options): String {
        val sanitized = buildString {
            appendSanitizedRaw(this, comment, options)
        }
        return sanitized
            .replace("--", "- -")
            .let { text -> if (text.endsWith('-')) "$text " else text }
    }

    private fun isValidXmlCodePoint(codePoint: Int): Boolean {
        return codePoint == 0x9 ||
            codePoint == 0xA ||
            codePoint == 0xD ||
            codePoint in 0x20..0xD7FF ||
            codePoint in 0xE000..0xFFFD ||
            codePoint in 0x10000..0x10FFFF
    }

    private fun formatUnicodeEscape(codePoint: Int): String {
        val hex = codePoint.toString(16).uppercase()
        return if (codePoint <= 0xFFFF) {
            "\\u${hex.padStart(4, '0')}"
        } else {
            "\\u{$hex}"
        }
    }

    private fun formatHex(bytes: ByteArray, trimLeadingZeroes: Boolean): String {
        val hex = toHexString(bytes)
        val normalized = if (trimLeadingZeroes) hex.trimStart('0').ifEmpty { "0" } else hex
        return "0x$normalized"
    }

    private fun toHexString(bytes: ByteArray): String {
        val out = StringBuilder(bytes.size * 2)
        for (byte in bytes) {
            val value = byte.toInt() and 0xFF
            out.append("0123456789abcdef"[value ushr 4])
            out.append("0123456789abcdef"[value and 0x0F])
        }
        return out.toString()
    }

    private data class TypedValue(
        val type: Int,
        val value: Any?,
    ) {
        fun requireString(context: String): String {
            return value as? String
                ?: throw ParseException("Expected $context to be backed by a string, but found $value")
        }

        fun render(options: Options): String {
            return when (val currentValue = value) {
                null -> ""
                is String -> currentValue
                is ByteArray -> when (type) {
                    TYPE_BYTES_HEX -> AbxXmlDecoder.toHexString(currentValue)
                    TYPE_BYTES_BASE64 -> Base64.getEncoder().encodeToString(currentValue)
                    else -> throw ParseException("Unexpected byte-array type $type")
                }

                is Int,
                is Long,
                is Float,
                is Double -> currentValue.toString()
                is Boolean -> options.booleanFormat.render(currentValue)
                else -> throw ParseException("Unsupported value type ${currentValue::class.java.name}")
            }
        }
    }

    private class Reader(input: InputStream) {
        private val bufferedInput = BufferedInputStream(input)
        private val dataInput = DataInputStream(bufferedInput)

        var position: Long = 0
            private set

        fun requireMagic() {
            val magic = readExact(ABX_MAGIC.size)
            if (!magic.contentEquals(ABX_MAGIC)) {
                throw ParseException("This is not an ABX file")
            }
        }

        fun readToken(): Int? {
            val value = bufferedInput.read()
            if (value < 0) return null
            position += 1
            return value
        }

        fun readInternedString(internedStrings: MutableList<String>): String {
            val index = readUnsignedShort()
            if (index != 0xFFFF) {
                return internedStrings.getOrNull(index)
                    ?: throw ParseException("Invalid string pool index $index at byte $position")
            }
            val value = readUtf8String()
            internedStrings += value
            return value
        }

        fun readUtf8String(): String {
            val length = readUnsignedShort()
            val bytes = readExact(length)
            return bytes.toString(StandardCharsets.UTF_8)
        }

        fun readLengthPrefixedBytes(expectedLength: Int? = null): ByteArray {
            val length = readUnsignedShort()
            if (expectedLength != null && length != expectedLength) {
                throw ParseException("Expected $expectedLength bytes, but ABX encoded $length bytes at byte $position")
            }
            return readExact(length)
        }

        fun readBytes(length: Int): ByteArray = readExact(length)

        fun readInt(): Int {
            return try {
                dataInput.readInt().also { position += 4 }
            } catch (e: EOFException) {
                throw ParseException("Unexpected end of ABX stream while reading Int", e)
            }
        }

        fun readLong(): Long {
            return try {
                dataInput.readLong().also { position += 8 }
            } catch (e: EOFException) {
                throw ParseException("Unexpected end of ABX stream while reading Long", e)
            }
        }

        fun hasRemainingData(): Boolean {
            bufferedInput.mark(1)
            val next = bufferedInput.read()
            if (next < 0) return false
            bufferedInput.reset()
            return true
        }

        private fun readUnsignedShort(): Int {
            return try {
                dataInput.readUnsignedShort().also { position += 2 }
            } catch (e: EOFException) {
                throw ParseException("Unexpected end of ABX stream while reading Short", e)
            }
        }

        private fun readExact(length: Int): ByteArray {
            val bytes = ByteArray(length)
            try {
                dataInput.readFully(bytes)
            } catch (e: EOFException) {
                throw ParseException("Unexpected end of ABX stream while reading $length bytes", e)
            }
            position += length
            return bytes
        }
    }
}
