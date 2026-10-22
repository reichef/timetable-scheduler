package timetablescheduler.adapters.web

import timetablescheduler.adapters.web.BrowserScriptLayout.BLANK_LINE_BETWEEN_STEPS
import timetablescheduler.adapters.web.BrowserScriptLayout.CONTROL_FLOW_BODY
import timetablescheduler.adapters.web.BrowserScriptLayout.Violation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The browser script follows the two project layout rules that ktlint checks for Kotlin (spec 003 FR-001, research R4). */
class BrowserScriptLayoutTest {
    @Test
    fun `the language switch script follows the layout rules`() {
        val script = checkNotNull(javaClass.getResource("/static/language-switch.js")).readText()

        val violations = BrowserScriptLayout.violations(script)

        assertTrue(violations.isEmpty(), "language-switch.js: ${violations.joinToString()}")
    }

    @Test
    fun `a body of if, else, for or while on the line of its keyword is reported`() {
        val script = "if (a) return;\nif (b) {\n  x();\n} else y();\nfor (;;) z();\nwhile (c) w();"

        assertEquals(listOf(1, 4, 5, 6).map { Violation(it, CONTROL_FLOW_BODY) }, BrowserScriptLayout.violations(script))
    }

    @Test
    fun `braced bodies on their own line and conditions over several lines are not reported`() {
        val script = "if (a &&\n    b(\")\")) {\n  x();\n} else if (c) {\n  y();\n} else {\n  z();\n}"

        assertEquals(emptyList(), BrowserScriptLayout.violations(script))
    }

    @Test
    fun `a function body with more than 5 statements in a row is reported at the sixth`() {
        val six = (1..6).joinToString("\n") { "  step($it);" }

        assertEquals(listOf(Violation(7, BLANK_LINE_BETWEEN_STEPS)), BrowserScriptLayout.violations("function work() {\n$six\n}"))
    }

    @Test
    fun `runs end at a blank line, while comments and nested callbacks count per body`() {
        val three = (1..3).joinToString("\n") { "  step($it);" }
        val callback = "  items.forEach(function (item) {\n    use(item);\n    use(item);\n  });"
        val script = "function work() {\n$three\n\n$three\n  // the last step\n$callback\n}"

        assertEquals(emptyList(), BrowserScriptLayout.violations(script))
    }
}

/**
 * A line-based check of a small browser script without a build tool, for the two project layout rules:
 * - [CONTROL_FLOW_BODY]: the body of `if`/`else`/`for`/`while` is a block in braces starting on its own line,
 * - [BLANK_LINE_BETWEEN_STEPS]: a function body has no run of more than 5 statements without a blank line.
 *
 * Strings and `//` comments are masked before brackets are counted; regular expression literals and block comments
 * are not supported, the checked script uses neither.
 */
internal object BrowserScriptLayout {
    const val CONTROL_FLOW_BODY = "layout:control-flow-body"
    const val BLANK_LINE_BETWEEN_STEPS = "layout:blank-line-between-steps"
    private const val MAXIMUM_STEPS_WITHOUT_BLANK_LINE = 5

    private val CONTROL_FLOW_WITH_CONDITION = Regex("""^(}\s*)?(else\s+if|if|for|while)\s*\(""")
    private val ELSE_WITHOUT_IF = Regex("""^(}\s*)?else\b(?!\s+if\b)""")
    private val FUNCTION_START = Regex("""\bfunction\b[^{]*\{$""")

    data class Violation(
        val line: Int,
        val ruleId: String,
    ) {
        override fun toString() = "line $line ($ruleId)"
    }

    fun violations(script: String): List<Violation> {
        val lines = script.lines()
        val code = lines.map(::withoutStringsAndComments)

        return (controlFlowViolations(code) + stepViolations(lines, code)).sortedBy { it.line }
    }

    private fun controlFlowViolations(code: List<String>): List<Violation> =
        code.indices.mapNotNull { index ->
            val line = code[index].trim()
            val afterHeader =
                when {
                    CONTROL_FLOW_WITH_CONDITION.containsMatchIn(line) -> textAfterCondition(code, index)
                    ELSE_WITHOUT_IF.containsMatchIn(line) -> line.substringAfter("else").trim()
                    else -> null
                }

            Violation(index + 1, CONTROL_FLOW_BODY).takeIf { afterHeader != null && afterHeader != "{" }
        }

    /** The rest of the line on which the condition starting at line [start] closes. */
    private fun textAfterCondition(
        code: List<String>,
        start: Int,
    ): String {
        var depth = 0

        for (index in start until code.size) {
            val line = code[index]
            val from = if (index == start) line.indexOf('(') else 0

            for (position in from until line.length) {
                when (line[position]) {
                    '(' -> {
                        depth++
                    }

                    ')' -> {
                        depth--
                        if (depth == 0) {
                            return line.substring(position + 1).trim()
                        }
                    }
                }
            }
        }

        return ""
    }

    /**
     * Counts the statements of each function body: lines starting at the bracket depth of the body. A blank line ends
     * the run; comment lines, continuation lines and lines closing a nested block neither end nor extend it.
     */
    private fun stepViolations(
        lines: List<String>,
        code: List<String>,
    ): List<Violation> {
        val violations = mutableListOf<Violation>()
        val bodies = ArrayDeque<FunctionBody>()
        var depth = 0

        lines.indices.forEach { index ->
            val text = code[index].trim()
            val body = bodies.lastOrNull()

            if (body != null && depth == body.depth) {
                when {
                    lines[index].isBlank() -> {
                        body.run = 0
                    }

                    text.isNotEmpty() && !text.startsWith("}") -> {
                        body.run++
                        if (body.run == MAXIMUM_STEPS_WITHOUT_BLANK_LINE + 1) {
                            violations += Violation(index + 1, BLANK_LINE_BETWEEN_STEPS)
                        }
                    }
                }
            }

            depth += text.count { it in "({[" } - text.count { it in ")}]" }
            if (FUNCTION_START.containsMatchIn(text)) {
                bodies.addLast(FunctionBody(depth))
            }
            while (bodies.isNotEmpty() && depth < bodies.last().depth) {
                bodies.removeLast()
            }
        }

        return violations
    }

    private class FunctionBody(
        val depth: Int,
    ) {
        var run = 0
    }

    /** The line with the content of string literals and a trailing `//` comment removed, so brackets in them do not count. */
    private fun withoutStringsAndComments(line: String): String {
        val code = StringBuilder()
        var quote: Char? = null
        var position = 0

        while (position < line.length) {
            val char = line[position]

            when {
                quote == null && line.startsWith("//", position) -> {
                    break
                }

                quote == null -> {
                    code.append(char)
                    if (char == '"' || char == '\'') {
                        quote = char
                    }
                }

                char == '\\' -> {
                    position++
                }

                char == quote -> {
                    code.append(char)
                    quote = null
                }
            }
            position++
        }

        return code.toString()
    }
}
