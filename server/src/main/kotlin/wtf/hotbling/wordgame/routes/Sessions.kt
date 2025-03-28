@file:OptIn(ExperimentalUuidApi::class)

package wtf.hotbling.wordgame.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.util.logging.KtorSimpleLogger
import wtf.hotbling.wordgame.api.ApiErrorResponse
import wtf.hotbling.wordgame.api.ApiSuccessResponse
import wtf.hotbling.wordgame.api.GuessParams
import wtf.hotbling.wordgame.api.Guesses
import wtf.hotbling.wordgame.api.SessionParams
import wtf.hotbling.wordgame.api.Sessions
import wtf.hotbling.wordgame.plugins.isLetters
import wtf.hotbling.wordgame.services.sessionsService
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.toJavaUuid

fun Route.sessionsApi() {
    val log = KtorSimpleLogger("sessions-api")

    get<Sessions> {
        val sessions = sessionsService.all()
        call.respond(ApiSuccessResponse(sessions.size, sessions))
    }
    get<Sessions.Id> { params ->
        val session = sessionsService.get(params.id.toJavaUuid())
        if (session == null) {
            call.respond(HttpStatusCode.BadRequest)
            return@get
        }
        call.respond(ApiSuccessResponse(data = session))
    }
    get<Sessions.ByAccount> { params ->
        val sessions = sessionsService.byAccount(params.accountId.toJavaUuid())
        call.respond(ApiSuccessResponse(sessions.size, sessions))
    }
    post<Sessions> {
        val req = call.receive<SessionParams>()
        sessionsService.create(req.accountId.toJavaUuid(), req.size, req.max).fold(
            { err -> call.respond(HttpStatusCode.BadRequest, ApiErrorResponse(mapOf(err))) },
            { data -> call.respond(ApiSuccessResponse(data = data)) }
        )
    }

    post<Guesses> {
        val req = call.receive<GuessParams>().sanitize()
        val valid = req.txt.length == 5 && req.txt.isLetters()
        if (!valid) {
            log.warn("bad guess: '${req.txt}'")
            // TODO throw with value
            call.respond(HttpStatusCode.BadRequest)
            return@post
        }
        val txt = req.txt.lowercase()

        sessionsService.createGuess(
            req.sessionId.toJavaUuid(), req.accountId.toJavaUuid(), txt
        ).fold(
            { err -> call.respond(HttpStatusCode.BadRequest, ApiErrorResponse(mapOf(err))) },
            { data -> call.respond(ApiSuccessResponse(data = data)) }
        )
    }
}

fun GuessParams.sanitize() = GuessParams(sessionId, accountId, txt.trim())
