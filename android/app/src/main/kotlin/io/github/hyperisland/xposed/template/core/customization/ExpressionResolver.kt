package io.github.hyperisland.xposed.template.core.customization

object ExpressionResolver {
    private const val MAX_EXPR_LEN = 320

    fun resolve(expr: String, vars: Map<String, String>): String {
        val safeExpr = expr.take(MAX_EXPR_LEN)
        val tokenRegex = Regex("\\$\\{([^}{]*(?:\\{[^}{]*\\}[^}{]*)*)\\}")
        return tokenRegex.replace(safeExpr) { m ->
            evaluateToken(m.groupValues[1], vars)
        }
    }

    fun functionDocs(): List<Map<String, String>> = listOf(
        mapOf(
            "name" to "replace",
            "example" to "replace(subtitle_or_title, \"^\\\\[\\\\d+条消息]\\\\s*[^:：]+[:：]\\\\s*\", \"\")",
        ),
        mapOf("name" to "regex", "example" to "regex(subtitle, \"(id\\\\d+)\", 1)"),
        mapOf("name" to "trim", "example" to "trim(subtitle_or_title)"),
    )

    private fun evaluateToken(rawToken: String, vars: Map<String, String>): String {
        val token = rawToken.trim()
        if (token.isEmpty()) return ""
        if (!token.contains('(') || !token.endsWith(')')) {
            return vars[token] ?: ""
        }
        val fnName = token.substringBefore('(').trim()
        val argsBody = token.substringAfter('(', "").removeSuffix(")")
        val args = splitArgs(argsBody)
        return when (fnName) {
            "replace" -> {
                if (args.size < 3) return ""
                val source = evalArg(args[0], vars)
                val pattern = evalArg(args[1], vars)
                val replacement = evalArg(args[2], vars)
                try {
                    Regex(pattern).replace(source, replacement)
                } catch (_: Exception) {
                    source
                }
            }
            "regex" -> {
                if (args.size < 2) return ""
                val source = evalArg(args[0], vars)
                val pattern = evalArg(args[1], vars)
                val group = args.getOrNull(2)?.let { evalArg(it, vars).toIntOrNull() } ?: 0
                try {
                    val mr = Regex(pattern).find(source) ?: return ""
                    if (group in 0 until mr.groupValues.size) mr.groupValues[group] else ""
                } catch (_: Exception) {
                    ""
                }
            }
            "trim" -> {
                if (args.isEmpty()) return ""
                evalArg(args[0], vars).trim()
            }
            else -> vars[token] ?: ""
        }
    }

    private fun evalArg(arg: String, vars: Map<String, String>): String {
        val trimmed = arg.trim()
        if (trimmed.isEmpty()) return ""
        if ((trimmed.startsWith('"') && trimmed.endsWith('"')) ||
            (trimmed.startsWith('\'') && trimmed.endsWith('\''))
        ) {
            return trimmed.substring(1, trimmed.length - 1)
        }
        return if (trimmed.contains("${'$'}{")) resolve(trimmed, vars) else (vars[trimmed] ?: trimmed)
    }

    private fun splitArgs(raw: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuote = false
        var quoteChar = '"'
        var depth = 0
        var i = 0
        while (i < raw.length) {
            val ch = raw[i]
            if (inQuote) {
                sb.append(ch)
                if (ch == quoteChar && (i == 0 || raw[i - 1] != '\\')) {
                    inQuote = false
                }
                i++
                continue
            }
            when (ch) {
                '\'', '"' -> {
                    inQuote = true
                    quoteChar = ch
                    sb.append(ch)
                }
                '(' -> {
                    depth++
                    sb.append(ch)
                }
                ')' -> {
                    depth = maxOf(0, depth - 1)
                    sb.append(ch)
                }
                ',' -> {
                    if (depth == 0) {
                        result += sb.toString().trim()
                        sb.setLength(0)
                    } else {
                        sb.append(ch)
                    }
                }
                else -> sb.append(ch)
            }
            i++
        }
        val tail = sb.toString().trim()
        if (tail.isNotEmpty()) result += tail
        return result
    }
}
