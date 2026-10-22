package timetablescheduler.tooling.layout

import com.pinterest.ktlint.ruleset.standard.rules.IfElseBracingRule
import com.pinterest.ktlint.ruleset.standard.rules.IndentationRule
import com.pinterest.ktlint.test.KtLintAssertThat.Companion.assertThatRule
import com.pinterest.ktlint.test.KtLintAssertThat.Companion.assertThatRuleBuilder
import com.pinterest.ktlint.test.LintViolation
import kotlin.test.Test

/** `layout:control-flow-body`: bodies of conditionals and loops are blocks starting on their own line (FR-001, FR-003). */
class ControlFlowBodyRuleTest {

    private val assertThatCode = assertThatRule { ControlFlowBodyRule() }

    private fun message(keyword: String) = ControlFlowBodyRule.messageFor(keyword)

    @Test
    fun `an if body on the same line as the condition is moved into braces on its own line`() {
        val code =
            """
            fun update(rows: Int): Boolean {
                if (rows != 1) return false
                return true
            }
            """.trimIndent()
        val formatted =
            """
            fun update(rows: Int): Boolean {
                if (rows != 1) {
                    return false
                }
                return true
            }
            """.trimIndent()

        assertThatCode(code)
            .hasLintViolation(2, 20, message("if"))
            .isFormattedAs(formatted)
    }

    @Test
    fun `an else body on the same line is moved into braces on its own line`() {
        val code =
            """
            fun choose(flag: Boolean) {
                if (flag) {
                    doIt()
                } else doOther()
            }
            """.trimIndent()
        val formatted =
            """
            fun choose(flag: Boolean) {
                if (flag) {
                    doIt()
                } else {
                    doOther()
                }
            }
            """.trimIndent()

        assertThatCode(code)
            .hasLintViolation(4, 12, message("else"))
            .isFormattedAs(formatted)
    }

    @Test
    fun `bodies of for, while and do loops are moved into braces on their own line`() {
        val code =
            """
            fun loops(xs: List<Int>) {
                for (x in xs) use(x)
                while (c()) step()
                do step() while (c())
            }
            """.trimIndent()
        val formatted =
            """
            fun loops(xs: List<Int>) {
                for (x in xs) {
                    use(x)
                }
                while (c()) {
                    step()
                }
                do {
                    step()
                } while (c())
            }
            """.trimIndent()

        assertThatCode(code)
            .hasLintViolations(
                LintViolation(2, 19, message("for")),
                LintViolation(3, 17, message("while")),
                LintViolation(4, 8, message("do")),
            ).isFormattedAs(formatted)
    }

    @Test
    fun `a braced body that starts on the line of the condition is wrapped`() {
        val code =
            """
            fun act(c: Boolean) {
                if (c) { a() }
            }
            """.trimIndent()
        val formatted =
            """
            fun act(c: Boolean) {
                if (c) {
                    a()
                }
            }
            """.trimIndent()

        assertThatCode(code)
            .hasLintViolation(2, 12, message("if"))
            .isFormattedAs(formatted)
    }

    @Test
    fun `a multi-line body without braces is reported`() {
        val code =
            """
            fun act(c: Boolean) {
                if (c)
                    a()
            }
            """.trimIndent()
        val formatted =
            """
            fun act(c: Boolean) {
                if (c) {
                    a()
                }
            }
            """.trimIndent()

        assertThatCode(code)
            .hasLintViolation(3, 9, message("if"))
            .isFormattedAs(formatted)
    }

    @Test
    fun `an if without else at the end of a lambda is an action, not a value`() {
        val code =
            """
            fun each(xs: List<Boolean>) {
                xs.forEach { if (it) doIt() }
            }
            """.trimIndent()

        assertThatCode(code).hasLintViolation(2, 26, message("if"))
    }

    @Test
    fun `a value if spanning several lines follows the rule in each branch`() {
        val code =
            """
            fun label(dropped: Boolean) {
                val label =
                    if (dropped)
                        "dropped"
                    else
                        "kept"
            }
            """.trimIndent()

        assertThatCode(code).hasLintViolations(
            LintViolation(4, 13, message("if")),
            LintViolation(6, 13, message("else")),
        )
    }

    @Test
    fun `values on one line, when branches, else-if chains and Elvis exits are not reported (constitution exceptions)`() {
        val code =
            """
            fun exceptions(a: Boolean, r: Int, xs: List<Boolean>): Int {
                val label = if (a) "x" else "y"
                call(if (a) 1 else 2)
                val values = xs.map { if (it) 1 else 2 }
                val found = find() ?: return 0
                val next = find() ?: throw IllegalStateException()
                when (r) {
                    1 -> first()
                    else -> other()
                }
                if (a) {
                    x()
                } else if (r > 1) {
                    y()
                } else {
                    z()
                }
                return if (a) 1 else 2
            }

            fun pick(a: Boolean) = if (a) 1 else 2
            """.trimIndent()

        assertThatCode(code).hasNoLintViolations()
    }

    @Test
    fun `the corrected code is accepted by the standard indentation and if-else bracing rules`() {
        val code =
            """
            fun update(rows: Int): Boolean {
                if (rows != 1) return false else if (rows == 0) return true
                return true
            }
            """.trimIndent()
        val formatted =
            """
            fun update(rows: Int): Boolean {
                if (rows != 1) {
                    return false
                } else if (rows == 0) {
                    return true
                }
                return true
            }
            """.trimIndent()

        val assertThatWithStandardRules =
            assertThatRuleBuilder { ControlFlowBodyRule() }
                .addAdditionalRuleProviders({ IndentationRule() }, { IfElseBracingRule() })
                .assertThat()

        assertThatWithStandardRules(code).isFormattedAs(formatted)
    }
}
