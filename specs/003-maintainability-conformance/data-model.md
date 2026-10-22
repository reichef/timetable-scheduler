# Data Model: Maintainability Conformance of the Existing Code

This feature changes no application data and adds no migration. Its "data" is the catalogue of checked rules and
the record of review findings.

## Layout rule (catalogue)

| Id | Source | Checks | Auto-fix |
|----|--------|--------|----------|
| `layout:control-flow-body` | Constitution I, FR-001, FR-003 | Bodies of `if`, `else`, `for`, `while`, `do` are blocks in braces whose first statement starts on its own line, indented one level deeper. Exempt: an `if`/`else` used as a value that fits on one line; single-expression `when` branches; Elvis exits (`?: return`, `?: continue`, `?: break`, `?: throw …`) | yes |
| `layout:blank-line-between-steps` | Constitution I, FR-002 | A function or lambda body with more than 5 statements contains at least one blank line between statements, and no run of more than 5 consecutive statements lacks a blank line | no (judgement) |
| `standard:*` | Constitution v2.1.0, FR-004 | every non-experimental rule of ktlint's standard rule set in the default code style `ktlint_official` — among others indentation, wrapping, `max-line-length` (140), trailing commas, blank lines, spacing, naming, imports | most yes; `max-line-length` and naming no |

Experimental standard rules are disabled (research R3); the exact rule list of the verified ktlint version is
recorded in the setup task. The browser script is checked for the two `layout:` rules by a test (research R4).

## Suppression

A ktlint rule may be suppressed only with a comment giving the reason, for one statement, one file or a defined group
of files (Constitution I, FR-004). Every suppression is a review finding with `rule = ktlint-suppression`,
`status = justified` and the reason as resolution.

| Suppression | Scope | Reason |
|-------------|-------|--------|
| `standard:function-naming` | all test sources (`**/src/test/**`) via `.editorconfig` | test functions are named as sentences in backticks that describe the tested behaviour |

## Review finding (`review-findings.md`)

One row per finding of the clean-code review (FR-006) and per function found without a meaningful test (FR-007).

| Field | Rules |
|-------|-------|
| `id` | `F-001`, `F-002`, … in order of discovery |
| `module` | `domain`, `application`, `adapters:persistence`, `adapters:solver`, `adapters:web`, `bootstrap`, `build`, `tooling` |
| `location` | file path and function name (no line numbers; they change during the work) |
| `rule` | one of: `naming`, `function-size`, `missing-comment`, `missing-test`, `ktlint-suppression` |
| `description` | what is wrong, in one sentence |
| `resolution` | `fixed` with a short note (e.g. "split into loadLevel / validateCurriculum") or `justified` with the reason |
| `status` | `open` → `fixed` \| `justified`; at the end of the feature no finding is `open` (SC-005, SC-007) |

A finding about a defect revealed by a new test (spec assumption) is recorded with `rule = missing-test`, status
`justified` and a reference to the separate change that fixes the defect.
