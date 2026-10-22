package timetablescheduler.tooling.layout

import com.pinterest.ktlint.test.KtLintAssertThat.Companion.assertThatRule
import com.pinterest.ktlint.test.LintViolation
import kotlin.test.Test

/** `layout:blank-line-between-steps`: no run of more than 5 statements without a blank line (FR-002). */
class BlankLineBetweenStepsRuleTest {

    private val assertThatCode = assertThatRule { BlankLineBetweenStepsRule() }

    private val message = BlankLineBetweenStepsRule.MESSAGE

    /** [count] statements `step(n)`, each on its own line, indented for a function body. */
    private fun statements(count: Int, first: Int = 1) = (first until first + count).joinToString("\n") { "    step($it)" }

    @Test
    fun `a function body with 6 statements and no blank line is reported at the sixth statement`() {
        val code = "fun work() {\n${statements(6)}\n}"

        assertThatCode(code).hasLintViolationWithoutAutoCorrect(7, 5, message)
    }

    @Test
    fun `5 statements without a blank line are not reported`() {
        val code = "fun work() {\n${statements(5)}\n}"

        assertThatCode(code).hasNoLintViolations()
    }

    @Test
    fun `every run is counted on its own after a blank line`() {
        val shortThenLong = "fun work() {\n${statements(5)}\n\n${statements(7, first = 6)}\n}"
        val twoShortRuns = "fun work() {\n${statements(5)}\n\n${statements(2, first = 6)}\n\n${statements(5, first = 8)}\n}"
        val twoLongRuns = "fun work() {\n${statements(6)}\n\n${statements(6, first = 7)}\n}"

        assertThatCode(shortThenLong).hasLintViolationWithoutAutoCorrect(13, 5, message)
        assertThatCode(twoShortRuns).hasNoLintViolations()
        assertThatCode(twoLongRuns).hasLintViolationsWithoutAutoCorrect(
            LintViolation(7, 5, message, canBeAutoCorrected = false),
            LintViolation(14, 5, message, canBeAutoCorrected = false),
        )
    }

    @Test
    fun `a comment between statements neither breaks nor extends a run`() {
        val code = "fun work() {\n${statements(3)}\n    // the next steps\n${statements(3, first = 4)}\n}"

        assertThatCode(code).hasLintViolationWithoutAutoCorrect(8, 5, message)
    }

    @Test
    fun `lambda bodies are checked like function bodies`() {
        val lambdaBody = (1..6).joinToString("\n") { "        step($it)" }
        val code = "fun work() {\n    run {\n$lambdaBody\n    }\n}"

        assertThatCode(code).hasLintViolationWithoutAutoCorrect(8, 9, message)
    }

    @Test
    fun `a body of assignments only is one mapping step and is not checked`() {
        val columns = (1..7).joinToString("\n") { "        it[column$it] = value$it" }
        val code = "fun store() {\n    table.insert {\n$columns\n    }\n}"

        assertThatCode(code).hasNoLintViolations()
    }

    @Test
    fun `a body mixing assignments with other statements is checked`() {
        val assignments = (1..5).joinToString("\n") { "    value$it = $it" }
        val code = "fun work() {\n$assignments\n    step(6)\n}"

        assertThatCode(code).hasLintViolationWithoutAutoCorrect(7, 5, message)
    }

    @Test
    fun `expression bodies and class bodies are not checked`() {
        val properties = (1..7).joinToString("\n") { "    val p$it = $it" }
        val code = "fun answer() = listOf(1, 2, 3, 4, 5, 6, 7)\n\nclass Many {\n$properties\n}"

        assertThatCode(code).hasNoLintViolations()
    }
}
