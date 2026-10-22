package timetablescheduler.adapters.web.account

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import kotlinx.html.FORM
import kotlinx.html.FormMethod
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.form
import kotlinx.html.h2
import kotlinx.html.label
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.select
import kotlinx.html.table
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.tr
import timetablescheduler.adapters.web.i18n.UiTexts
import timetablescheduler.adapters.web.i18n.texts
import timetablescheduler.adapters.web.layout.csrfField
import timetablescheduler.adapters.web.layout.hiddenField
import timetablescheduler.adapters.web.layout.respondPage
import timetablescheduler.adapters.web.layout.textField
import timetablescheduler.adapters.web.layout.validationErrors
import timetablescheduler.domain.account.Role
import timetablescheduler.domain.account.UserAccount
import timetablescheduler.domain.validation.ValidationError

const val ACCOUNTS_PATH = "/accounts"
const val OWN_PASSWORD_PATH = "/account/password"

fun accountPath(account: UserAccount) = "$ACCOUNTS_PATH/${account.id.value}"

/** Entered values of the new-account form; the password is never shown again. */
data class AccountFormInput(
    val username: String,
    val role: String,
)

private fun FORM.roleSelect(
    selectedRole: String?,
    texts: UiTexts,
) {
    label {
        +texts.accounts.role
        select {
            name = "role"
            Role.entries.forEach { role ->
                option {
                    value = role.name
                    selected = selectedRole == role.name
                    +texts.common.roleName(role)
                }
            }
        }
    }
}

suspend fun ApplicationCall.respondAccountList(
    accounts: List<UserAccount>,
    errors: List<ValidationError> = emptyList(),
    entered: AccountFormInput? = null,
    status: HttpStatusCode = HttpStatusCode.OK,
) {
    val texts = texts
    respondPage(texts.accounts.title, status) { session ->
        table {
            tr {
                th { +texts.auth.username }
                th { +texts.accounts.roleColumn }
                th { +texts.accounts.statusColumn }
            }
            accounts.forEach { account ->
                tr {
                    td { a(href = accountPath(account)) { +account.username } }
                    td { +texts.common.roleName(account.role) }
                    td { +texts.accounts.stateName(account.active) }
                }
            }
        }
        h2 { +texts.accounts.createAccount }
        validationErrors(errors, texts)
        form(action = ACCOUNTS_PATH, method = FormMethod.post) {
            csrfField(session)
            textField("username", texts.auth.username, entered?.username)
            roleSelect(entered?.role ?: Role.TEACHER.name, texts)
            textField("initialPassword", texts.accounts.initialPassword, null, InputType.password)
            button { +texts.common.create }
        }
    }
}

suspend fun ApplicationCall.respondAccountDetails(
    account: UserAccount,
    errors: List<ValidationError> = emptyList(),
    status: HttpStatusCode = HttpStatusCode.OK,
) {
    val texts = texts
    respondPage(texts.accounts.accountTitle(account.username), status) { session ->
        p { +texts.accounts.status(account.active) }
        validationErrors(errors, texts)
        h2 { +texts.accounts.role }
        form(action = accountPath(account), method = FormMethod.post) {
            csrfField(session)
            hiddenField("version", "${account.version}")
            roleSelect(account.role.name, texts)
            button { +texts.common.save }
        }
        h2 { +if (account.active) texts.accounts.deactivateHeading else texts.accounts.activateHeading }
        form(action = "${accountPath(account)}/${if (account.active) "deactivate" else "activate"}", method = FormMethod.post) {
            csrfField(session)
            hiddenField("version", "${account.version}")
            button(classes = if (account.active) "danger" else null) {
                +if (account.active) texts.accounts.deactivateButton else texts.accounts.activateButton
            }
        }
        h2 { +texts.accounts.resetPassword }
        form(action = "${accountPath(account)}/password", method = FormMethod.post) {
            csrfField(session)
            textField("newPassword", texts.accounts.newPassword, null, InputType.password)
            button { +texts.accounts.resetButton }
        }
        p { a(href = ACCOUNTS_PATH) { +texts.accounts.toAccounts } }
    }
}

suspend fun ApplicationCall.respondOwnPasswordForm(
    errors: List<ValidationError> = emptyList(),
    changed: Boolean = false,
    status: HttpStatusCode = HttpStatusCode.OK,
) {
    val texts = texts
    respondPage(texts.accounts.ownPasswordTitle, status) { session ->
        if (changed) {
            p(classes = "notice") { +texts.accounts.passwordChanged }
        }
        validationErrors(errors, texts)
        form(action = OWN_PASSWORD_PATH, method = FormMethod.post) {
            csrfField(session)
            textField("currentPassword", texts.accounts.currentPassword, null, InputType.password)
            textField("newPassword", texts.accounts.newPassword, null, InputType.password)
            button { +texts.accounts.changeButton }
        }
    }
}
