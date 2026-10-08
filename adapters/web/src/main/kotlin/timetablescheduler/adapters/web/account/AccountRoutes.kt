package timetablescheduler.adapters.web.account

import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import timetablescheduler.adapters.web.WebDependencies
import timetablescheduler.adapters.web.auth.SESSION_AUTH
import timetablescheduler.adapters.web.auth.actor
import timetablescheduler.adapters.web.layout.idParameter
import timetablescheduler.adapters.web.layout.receiveCheckedForm
import timetablescheduler.adapters.web.layout.respondFormResult
import timetablescheduler.adapters.web.layout.respondResult
import timetablescheduler.adapters.web.layout.seeOther
import timetablescheduler.adapters.web.layout.text
import timetablescheduler.adapters.web.layout.version
import timetablescheduler.application.account.AccountUseCases
import timetablescheduler.application.error.ApplicationError
import timetablescheduler.application.error.UseCaseResult
import timetablescheduler.domain.account.Role
import timetablescheduler.domain.common.AccountId
import timetablescheduler.domain.validation.ValidationError

private const val FIELD_ROLE = "role"

private suspend fun ApplicationCall.accountIdParameter(): AccountId? = idParameter("accountId")?.let(::AccountId)

private fun Parameters.role(): Role? = Role.entries.firstOrNull { it.name == this[FIELD_ROLE] }

private val invalidRole = UseCaseResult.Failure(ApplicationError.Invalid(listOf(ValidationError.InvalidFormat(FIELD_ROLE))))

/** Shows the account page again with the business errors of a rejected change (422). */
private suspend fun ApplicationCall.showAccountAgain(accounts: AccountUseCases, accountId: AccountId, errors: List<ValidationError>) {
    respondResult(accounts.get.execute(actor, accountId)) { respondAccountDetails(it, errors, HttpStatusCode.UnprocessableEntity) }
}

/** Account management for planners (FR-032 – FR-034) and the own password change for every user. */
fun Route.accountRoutes(dependencies: WebDependencies) {
    val accounts = dependencies.useCases.accounts

    authenticate(SESSION_AUTH) {
        get(ACCOUNTS_PATH) {
            call.respondResult(accounts.list.execute(call.actor)) { call.respondAccountList(it) }
        }

        post(ACCOUNTS_PATH) {
            val form = call.receiveCheckedForm()
            val entered = AccountFormInput(form.text("username"), form.text(FIELD_ROLE))
            val role = form.role()
            val result = if (role == null) invalidRole
            else accounts.create.execute(call.actor, entered.username, role, form.text("initialPassword"))
            call.respondFormResult(
                result,
                backLink = ACCOUNTS_PATH,
                onInvalid = { errors ->
                    call.respondResult(accounts.list.execute(call.actor)) {
                        call.respondAccountList(it, errors, entered, HttpStatusCode.UnprocessableEntity)
                    }
                },
            ) { call.seeOther(ACCOUNTS_PATH) }
        }

        get("$ACCOUNTS_PATH/{accountId}") {
            val accountId = call.accountIdParameter() ?: return@get
            call.respondResult(accounts.get.execute(call.actor, accountId)) { call.respondAccountDetails(it) }
        }

        post("$ACCOUNTS_PATH/{accountId}") {
            val accountId = call.accountIdParameter() ?: return@post
            val form = call.receiveCheckedForm()
            val role = form.role()
            val result = if (role == null) invalidRole else accounts.updateRole.execute(call.actor, accountId, role, form.version())
            call.respondFormResult(
                result,
                backLink = "$ACCOUNTS_PATH/${accountId.value}",
                onInvalid = { errors -> call.showAccountAgain(accounts, accountId, errors) },
            ) { call.seeOther(accountPath(it)) }
        }

        post("$ACCOUNTS_PATH/{accountId}/deactivate") {
            val accountId = call.accountIdParameter() ?: return@post
            val form = call.receiveCheckedForm()
            call.respondFormResult(
                accounts.deactivate.execute(call.actor, accountId, form.version()),
                backLink = "$ACCOUNTS_PATH/${accountId.value}",
                onInvalid = { errors -> call.showAccountAgain(accounts, accountId, errors) },
            ) { call.seeOther(accountPath(it)) }
        }

        post("$ACCOUNTS_PATH/{accountId}/activate") {
            val accountId = call.accountIdParameter() ?: return@post
            val form = call.receiveCheckedForm()
            call.respondFormResult(
                accounts.activate.execute(call.actor, accountId, form.version()),
                backLink = "$ACCOUNTS_PATH/${accountId.value}",
                onInvalid = { errors -> call.showAccountAgain(accounts, accountId, errors) },
            ) { call.seeOther(accountPath(it)) }
        }

        post("$ACCOUNTS_PATH/{accountId}/password") {
            val accountId = call.accountIdParameter() ?: return@post
            val form = call.receiveCheckedForm()
            call.respondFormResult(
                accounts.resetPassword.execute(call.actor, accountId, form.text("newPassword")),
                backLink = "$ACCOUNTS_PATH/${accountId.value}",
                onInvalid = { errors -> call.showAccountAgain(accounts, accountId, errors) },
            ) { call.seeOther("$ACCOUNTS_PATH/${accountId.value}") }
        }

        get(OWN_PASSWORD_PATH) {
            call.respondOwnPasswordForm(changed = call.request.queryParameters["changed"] != null)
        }

        post(OWN_PASSWORD_PATH) {
            val form = call.receiveCheckedForm()
            call.respondFormResult(
                accounts.changeOwnPassword.execute(call.actor, form.text("currentPassword"), form.text("newPassword")),
                backLink = OWN_PASSWORD_PATH,
                onInvalid = { errors -> call.respondOwnPasswordForm(errors, status = HttpStatusCode.UnprocessableEntity) },
            ) { call.seeOther("$OWN_PASSWORD_PATH?changed=true") }
        }
    }
}
