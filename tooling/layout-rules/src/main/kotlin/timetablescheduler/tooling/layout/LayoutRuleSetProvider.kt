package timetablescheduler.tooling.layout

import com.pinterest.ktlint.cli.ruleset.core.api.RuleSetProviderV3
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import com.pinterest.ktlint.rule.engine.core.api.RuleSetId

/** Rule-set id of the project rules; their ids are `layout:<rule>` (contracts/layout-check.md). */
const val RULE_SET_ID = "layout"

val LAYOUT_RULES_ABOUT =
    Rule.About(
        maintainer = "timetable-scheduler",
        repositoryUrl = "https://github.com/reichef/timetable-scheduler",
        issueTrackerUrl = "https://github.com/reichef/timetable-scheduler/issues",
    )

/** Makes the project rules available to ktlint (registered in META-INF/services). */
class LayoutRuleSetProvider : RuleSetProviderV3(RuleSetId(RULE_SET_ID)) {

    override fun getRuleProviders(): Set<RuleProvider> =
        setOf(
            RuleProvider { ControlFlowBodyRule() },
            RuleProvider { BlankLineBetweenStepsRule() },
        )
}
