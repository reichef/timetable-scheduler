package timetablescheduler.tooling.layout

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.ElementType.BLOCK
import com.pinterest.ktlint.rule.engine.core.api.ElementType.BODY
import com.pinterest.ktlint.rule.engine.core.api.ElementType.DO_KEYWORD
import com.pinterest.ktlint.rule.engine.core.api.ElementType.DO_WHILE
import com.pinterest.ktlint.rule.engine.core.api.ElementType.ELSE
import com.pinterest.ktlint.rule.engine.core.api.ElementType.ELSE_KEYWORD
import com.pinterest.ktlint.rule.engine.core.api.ElementType.FOR
import com.pinterest.ktlint.rule.engine.core.api.ElementType.FUNCTION_LITERAL
import com.pinterest.ktlint.rule.engine.core.api.ElementType.IF
import com.pinterest.ktlint.rule.engine.core.api.ElementType.LBRACE
import com.pinterest.ktlint.rule.engine.core.api.ElementType.RBRACE
import com.pinterest.ktlint.rule.engine.core.api.ElementType.RPAR
import com.pinterest.ktlint.rule.engine.core.api.ElementType.THEN
import com.pinterest.ktlint.rule.engine.core.api.ElementType.WHILE
import com.pinterest.ktlint.rule.engine.core.api.IndentConfig
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import com.pinterest.ktlint.rule.engine.core.api.editorconfig.EditorConfig
import com.pinterest.ktlint.rule.engine.core.api.editorconfig.INDENT_SIZE_PROPERTY
import com.pinterest.ktlint.rule.engine.core.api.editorconfig.INDENT_STYLE_PROPERTY
import com.pinterest.ktlint.rule.engine.core.api.ifAutocorrectAllowed
import com.pinterest.ktlint.rule.engine.core.api.indent20
import com.pinterest.ktlint.rule.engine.core.api.isCode
import com.pinterest.ktlint.rule.engine.core.api.isPartOfComment20
import com.pinterest.ktlint.rule.engine.core.api.isWhiteSpace20
import com.pinterest.ktlint.rule.engine.core.api.isWhiteSpaceWithoutNewline20
import com.pinterest.ktlint.rule.engine.core.api.nextSibling
import com.pinterest.ktlint.rule.engine.core.api.replaceTextWith
import com.pinterest.ktlint.rule.engine.core.api.upsertWhitespaceAfterMe
import com.pinterest.ktlint.rule.engine.core.api.upsertWhitespaceBeforeMe
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.kotlin.com.intellij.psi.impl.source.tree.PsiWhiteSpaceImpl
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.psiUtil.leaves

/**
 * `layout:control-flow-body` (Constitution I layout example, spec 003 FR-001, FR-003): the body of `if`, `else`, `for`,
 * `while` and `do` is a block in braces whose first statement starts on its own line. An `if`/`else` used as a value
 * may stay on one line; `when` branches and Elvis exits (`?: return`) are not control-flow bodies and are not checked.
 */
class ControlFlowBodyRule :
    Rule(
        ruleId = RuleId("$RULE_SET_ID:control-flow-body"),
        about = LAYOUT_RULES_ABOUT,
        usesEditorConfigProperties = setOf(INDENT_SIZE_PROPERTY, INDENT_STYLE_PROPERTY),
    ),
    RuleAutocorrectApproveHandler {
    private var indentConfig = IndentConfig.DEFAULT_INDENT_CONFIG

    override fun beforeFirstNode(editorConfig: EditorConfig) {
        indentConfig = IndentConfig(indentStyle = editorConfig[INDENT_STYLE_PROPERTY], tabWidth = editorConfig[INDENT_SIZE_PROPERTY])
    }

    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision,
    ) {
        val keyword = keywordOfBody(node) ?: return
        val body = node.firstChildNode ?: return

        if (isElseIf(node) || isExemptValue(node) || startsOnOwnLineInBraces(body)) {
            return
        }

        emit(body.startOffset, messageFor(keyword), true).ifAutocorrectAllowed {
            if (body.elementType == BLOCK) {
                wrapBlockContent(body)
            } else {
                wrapInBlock(node)
            }
        }
    }

    /** The keyword whose body [node] is, or null when [node] is no control-flow body. */
    private fun keywordOfBody(node: ASTNode): String? =
        when (node.elementType) {
            THEN -> {
                "if"
            }

            ELSE -> {
                "else"
            }

            BODY -> {
                when (node.treeParent?.elementType) {
                    FOR -> "for"
                    WHILE -> "while"
                    DO_WHILE -> "do"
                    else -> null
                }
            }

            else -> {
                null
            }
        }

    /** `else if (…)` continues a chain; the nested `if` is checked on its own. */
    private fun isElseIf(node: ASTNode): Boolean = node.elementType == ELSE && node.firstChildNode?.elementType == IF

    /**
     * Constitution exception: an `if`/`else` used as a value may stay on one line. The outermost `if` of an `else if`
     * chain decides: it is a value when it has an `else` branch and stands where a value is expected — anywhere but as
     * a statement of a block, except the last statement of a lambda, which is the lambda's result. Loop bodies are
     * always statements.
     */
    private fun isExemptValue(node: ASTNode): Boolean {
        if (node.elementType == BODY) {
            return false
        }

        val outermostIf = outermostIfOf(node.treeParent)
        val hasElse = outermostIf.findChildByType(ELSE) != null

        return hasElse && isInValuePosition(outermostIf) && !outermostIf.textContains('\n')
    }

    private fun outermostIfOf(ifNode: ASTNode): ASTNode {
        var current = ifNode

        while (current.treeParent?.elementType == ELSE && current.treeParent.treeParent?.elementType == IF) {
            current = current.treeParent.treeParent
        }

        return current
    }

    private fun isInValuePosition(expression: ASTNode): Boolean {
        val parent = expression.treeParent ?: return false

        if (parent.elementType in setOf(THEN, ELSE, BODY)) {
            return false
        }

        if (parent.elementType != BLOCK) {
            return true
        }

        val isLambdaBody = parent.treeParent?.elementType == FUNCTION_LITERAL
        val isLastStatement = expression.nextSibling { it.isCode } == null

        return isLambdaBody && isLastStatement
    }

    /** A block whose first statement starts on a new line; an empty block `{}` passes. */
    private fun startsOnOwnLineInBraces(body: ASTNode): Boolean {
        if (body.elementType != BLOCK) {
            return false
        }

        val afterBrace = body.firstChildNode?.takeIf { it.elementType == LBRACE }?.treeNext ?: return true

        return afterBrace.elementType == RBRACE || (afterBrace.isWhiteSpace20 && afterBrace.textContains('\n'))
    }

    /** `{ a() }` on one line: the statements move to their own lines inside the existing braces. */
    private fun wrapBlockContent(block: ASTNode) {
        val container = block.treeParent

        block.findChildByType(LBRACE)?.upsertWhitespaceAfterMe(indentConfig.childIndentOf(container))
        block.findChildByType(RBRACE)?.upsertWhitespaceBeforeMe(container.indent20)
    }

    /**
     * Replaces the body of [container] by a block in braces holding it on its own line, following the auto-correct of
     * ktlint's `multiline-if-else` rule: the whitespace before the body becomes a single space, comments trailing the
     * body move into the block, and the keyword after the body (`else`, `while` of a do loop) follows the closing brace.
     */
    private fun wrapInBlock(container: ASTNode) {
        val leavesBefore =
            container
                .leaves(forward = false)
                .takeWhile { it.elementType !in setOf(RPAR, ELSE_KEYWORD, DO_KEYWORD) }
                .toList()
                .reversed()
        val trailingComments =
            container
                .leaves(forward = true)
                .takeWhile { it.isWhiteSpaceWithoutNewline20 || it.isPartOfComment20 }
                .toList()
                .dropLastWhile { it.isWhiteSpaceWithoutNewline20 }

        leavesBefore.firstOrNull()?.takeIf { it.isWhiteSpace20 }?.replaceTextWith(" ")

        KtBlockExpression(null).apply {
            val body = container.firstChildNode

            container.replaceChild(body, this)
            addChild(LeafPsiElement(LBRACE, "{"))
            addChild(PsiWhiteSpaceImpl(indentConfig.childIndentOf(container)))

            leavesBefore.dropWhile { it.isWhiteSpace20 }.forEach(::addChild)
            addChild(body)
            trailingComments.forEach(::addChild)

            addChild(PsiWhiteSpaceImpl(container.indent20))
            addChild(LeafPsiElement(RBRACE, "}"))
        }

        container.nextSibling { !it.isWhiteSpace20 && !it.isPartOfComment20 }?.upsertWhitespaceBeforeMe(" ")
    }

    companion object {
        fun messageFor(keyword: String): String = "Body of '$keyword' must be a block in braces starting on its own line"
    }
}
