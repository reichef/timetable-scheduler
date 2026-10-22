package timetablescheduler.tooling.layout

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.ElementType.BLOCK
import com.pinterest.ktlint.rule.engine.core.api.ElementType.CLASS_INITIALIZER
import com.pinterest.ktlint.rule.engine.core.api.ElementType.FUN
import com.pinterest.ktlint.rule.engine.core.api.ElementType.FUNCTION_LITERAL
import com.pinterest.ktlint.rule.engine.core.api.ElementType.LBRACE
import com.pinterest.ktlint.rule.engine.core.api.ElementType.PROPERTY_ACCESSOR
import com.pinterest.ktlint.rule.engine.core.api.ElementType.RBRACE
import com.pinterest.ktlint.rule.engine.core.api.ElementType.SECONDARY_CONSTRUCTOR
import com.pinterest.ktlint.rule.engine.core.api.ElementType.SEMICOLON
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import com.pinterest.ktlint.rule.engine.core.api.isPartOfComment20
import com.pinterest.ktlint.rule.engine.core.api.isWhiteSpace20
import org.jetbrains.kotlin.com.intellij.lang.ASTNode

/**
 * `layout:blank-line-between-steps` (Constitution I layout example, spec 003 FR-002): the steps of a function or lambda
 * body are separated by blank lines. A body may not contain a run of more than [MAXIMUM_STEPS_WITHOUT_BLANK_LINE]
 * statements without a blank line between two of them. Where to separate the steps is a judgement, so the rule offers
 * no auto-correct.
 */
class BlankLineBetweenStepsRule :
    Rule(
        ruleId = RuleId("$RULE_SET_ID:blank-line-between-steps"),
        about = LAYOUT_RULES_ABOUT,
    ),
    RuleAutocorrectApproveHandler {

    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision,
    ) {
        if (node.elementType != BLOCK || node.treeParent?.elementType !in CHECKED_BODIES) {
            return
        }

        statementsStartingTooLongRuns(node).forEach { statement ->
            emit(statement.startOffset, MESSAGE, false)
        }
    }

    /**
     * Walks the children of a body in order and counts consecutive statements. Whitespace containing a blank line ends
     * the current run; comments neither end nor extend it. The statement that makes a run longer than the maximum is
     * reported, once per run.
     */
    private fun statementsStartingTooLongRuns(block: ASTNode): List<ASTNode> {
        val reported = mutableListOf<ASTNode>()
        var runLength = 0
        var child = block.firstChildNode

        while (child != null) {
            when {
                child.isWhiteSpace20 && child.text.count { it == '\n' } >= 2 -> {
                    runLength = 0
                }
                isStatement(child) -> {
                    runLength++

                    if (runLength == MAXIMUM_STEPS_WITHOUT_BLANK_LINE + 1) {
                        reported += child
                    }
                }
            }

            child = child.treeNext
        }

        return reported
    }

    private fun isStatement(node: ASTNode): Boolean =
        !node.isWhiteSpace20 && !node.isPartOfComment20 && node.elementType !in setOf(LBRACE, RBRACE, SEMICOLON)

    companion object {
        const val MAXIMUM_STEPS_WITHOUT_BLANK_LINE = 5
        const val MESSAGE = "More than $MAXIMUM_STEPS_WITHOUT_BLANK_LINE statements in a row without a blank line between steps"

        /** Bodies whose statements are steps: functions, lambdas, property accessors, constructors and initializers. */
        private val CHECKED_BODIES = setOf(FUN, FUNCTION_LITERAL, PROPERTY_ACCESSOR, SECONDARY_CONSTRUCTOR, CLASS_INITIALIZER)
    }
}
