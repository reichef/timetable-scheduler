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

fun Role.germanName() = when (this) {
    Role.PLANNER -> "Planer"
    Role.TEACHER -> "Lehrkraft"
}

private fun UserAccount.stateName() = if (active) "aktiv" else "deaktiviert"

/** Entered values of the new-account form; the password is never shown again. */
data class AccountFormInput(val username: String, val role: String)

private fun FORM.roleSelect(selectedRole: String?) {
    label {
        +"Rolle"
        select {
            name = "role"
            Role.entries.forEach { role ->
                option {
                    value = role.name
                    selected = selectedRole == role.name
                    +role.germanName()
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
    respondPage("Konten", status) { session ->
        table {
            tr { th { +"Benutzername" }; th { +"Rolle" }; th { +"Status" } }
            accounts.forEach { account ->
                tr {
                    td { a(href = accountPath(account)) { +account.username } }
                    td { +account.role.germanName() }
                    td { +account.stateName() }
                }
            }
        }
        h2 { +"Konto anlegen" }
        validationErrors(errors)
        form(action = ACCOUNTS_PATH, method = FormMethod.post) {
            csrfField(session)
            textField("username", "Benutzername", entered?.username)
            roleSelect(entered?.role ?: Role.TEACHER.name)
            textField("initialPassword", "Anfangspasswort (mindestens 10 Zeichen)", null, InputType.password)
            button { +"Anlegen" }
        }
    }
}

suspend fun ApplicationCall.respondAccountDetails(
    account: UserAccount,
    errors: List<ValidationError> = emptyList(),
    status: HttpStatusCode = HttpStatusCode.OK,
) {
    respondPage("Konto ${account.username}", status) { session ->
        p { +"Status: ${account.stateName()}" }
        validationErrors(errors)
        h2 { +"Rolle" }
        form(action = accountPath(account), method = FormMethod.post) {
            csrfField(session)
            hiddenField("version", "${account.version}")
            roleSelect(account.role.name)
            button { +"Speichern" }
        }
        h2 { +if (account.active) "Deaktivieren" else "Aktivieren" }
        form(action = "${accountPath(account)}/${if (account.active) "deactivate" else "activate"}", method = FormMethod.post) {
            csrfField(session)
            hiddenField("version", "${account.version}")
            button(classes = if (account.active) "danger" else null) { +if (account.active) "Konto deaktivieren" else "Konto aktivieren" }
        }
        h2 { +"Passwort zurücksetzen" }
        form(action = "${accountPath(account)}/password", method = FormMethod.post) {
            csrfField(session)
            textField("newPassword", "Neues Passwort (mindestens 10 Zeichen)", null, InputType.password)
            button { +"Zurücksetzen" }
        }
        p { a(href = ACCOUNTS_PATH) { +"Zur Kontenliste" } }
    }
}

suspend fun ApplicationCall.respondOwnPasswordForm(
    errors: List<ValidationError> = emptyList(),
    changed: Boolean = false,
    status: HttpStatusCode = HttpStatusCode.OK,
) {
    respondPage("Passwort ändern", status) { session ->
        if (changed) p(classes = "notice") { +"Das Passwort wurde geändert." }
        validationErrors(errors)
        form(action = OWN_PASSWORD_PATH, method = FormMethod.post) {
            csrfField(session)
            textField("currentPassword", "Aktuelles Passwort", null, InputType.password)
            textField("newPassword", "Neues Passwort (mindestens 10 Zeichen)", null, InputType.password)
            button { +"Ändern" }
        }
    }
}
