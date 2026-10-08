package timetablescheduler.adapters.web.layout

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.header
import io.ktor.server.response.respond

/** Post/Redirect/Get: answers a successful form post with 303 See Other. */
suspend fun ApplicationCall.seeOther(location: String) {
    response.header(HttpHeaders.Location, location)
    respond(HttpStatusCode.SeeOther)
}
