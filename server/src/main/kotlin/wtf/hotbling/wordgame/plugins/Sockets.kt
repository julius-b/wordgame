package wtf.hotbling.wordgame.plugins

import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.sendSerialized
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.websocket.CloseReason
import io.ktor.websocket.close
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import wtf.hotbling.wordgame.api.AccountHeader
import wtf.hotbling.wordgame.api.SessionHeader
import wtf.hotbling.wordgame.services.sessionsService
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

fun Application.configureSockets() {
    val log = KtorSimpleLogger("sockets")
    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 15.seconds
        contentConverter = KotlinxWebsocketSerializationConverter(Json {
            encodeDefaults = true
            isLenient = true
        })
    }

    routing {
        webSocket("/ws") {
            val sessionId =
                call.request.queryParameters[SessionHeader]?.let { UUID.fromString(it) } //call.request.headers[SessionHeader]?.let { UUID.fromString(it) }
            if (sessionId == null) {
                log.warn("no $SessionHeader submitted")
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Unauthorized"))
                return@webSocket
            }
            val accountId =
                call.request.queryParameters[AccountHeader]?.let { UUID.fromString(it) } // call.request.headers[AccountHeader]?.let { UUID.fromString(it) }
            if (accountId == null) {
                log.warn("no $AccountHeader submitted")
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Unauthorized"))
                return@webSocket
            }
            log.info("new connection to session: $sessionId from account: $accountId")

            // TODO allow ApiError.Constraint(max=session.size) for spectating
            val err = sessionsService.addPeer(sessionId, accountId)
            if (err != null) {
                log.warn("failed to add peer: $accountId to session: $sessionId: $err")
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Unauthorized"))
                return@webSocket
            }

            while (true) {
                val session = sessionsService.get(sessionId)
                if (session == null) {
                    close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Unauthorized"))
                    return@webSocket
                }
                sendSerialized(session)
                delay(1.seconds)
            }
        }
    }
}
