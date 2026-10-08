package timetablescheduler.adapters.web.auth

import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.principal
import timetablescheduler.domain.common.AccountId

/** The logged-in account; only called inside `authenticate(SESSION_AUTH)`, where a session is guaranteed. */
val ApplicationCall.actor: AccountId
    get() = checkNotNull(principal<UserSession>()) { "Route requires authentication" }.accountId
