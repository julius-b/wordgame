@file:OptIn(ExperimentalUuidApi::class)

package wtf.hotbling.wordgame.api

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.resources.post
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.receiveDeserialized
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.plugins.websocket.wss
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.URLProtocol
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Inject
@SingleIn(AppScope::class)
class SessionRepository(
    private val client: HttpClient,
    private val accountRepository: AccountRepository
) {
    private val log = Logger.withTag("SessionRepo")

    suspend fun newSession(size: Int, max: Int): RepoResult<ApiSession> {
        log.i { "new-session" }
        try {
            val accountId = accountRepository.getAccountId()!!
            val resp = client.post(Sessions()) {
                contentType(ContentType.Application.Json)
                setBody(SessionParams(accountId, size, max))
            }
            if (!resp.status.isSuccess()) {
                log.e { "new-session - failed: $resp" }
                val err = resp.body<ApiErrorResponse>()
                return RepoResult.ValidationError(err.errors)
            }
            val success = resp.body<ApiSuccessResponse<ApiSession>>()
            val session = success.data
            log.d { "new-session - success: $success" }

            return RepoResult.Data(session)
        } catch (e: Throwable) {
            log.e(e) { "new-session - unexpected resp: $e" }
            return RepoResult.NetworkError
        }
    }

    suspend fun createGuess(sessionId: Uuid, txt: String): RepoResult<ApiGuess> {
        val tag = "create-guess(sessionId=$sessionId,txt='$txt')"
        log.i { tag }
        try {
            val accountId = accountRepository.getAccountId()!!
            val resp = client.post(Guesses()) {
                contentType(ContentType.Application.Json)
                setBody(GuessParams(sessionId, accountId, txt))
            }
            if (!resp.status.isSuccess()) {
                log.e { "$tag - failed: $resp" }
                val err = resp.body<ApiErrorResponse>()
                return RepoResult.ValidationError(err.errors)
            }
            val success = resp.body<ApiSuccessResponse<ApiGuess>>()
            val guess = success.data
            log.d { "$tag - guess: $guess" }

            return RepoResult.Data(guess)
        } catch (e: Throwable) {
            log.e(e) { "$tag - unexpected resp: $e" }
            return RepoResult.NetworkError
        }
    }

    suspend fun connect(
        sessionId: Uuid,
        accountId: Uuid,
        onUpdate: (ConnectionUpdate) -> Unit
    ) {
        // TODO query sessionId ensure it still exists
        while (true) {
            val self = accountRepository.self()
            log.i { "self: $self" }
            when (self) {
                is RepoResult.Data<*> -> break
                RepoResult.NetworkError -> delay(1.seconds)
                else -> {
                    log.w { "creating account..." }
                    val resp = accountRepository.saveAccount("")
                    log.i { "created account: $resp" }
                    delay(1.seconds)
                }
            }
        }

        suspend fun DefaultClientWebSocketSession.process() {
            onUpdate(ConnectionUpdate.Connected)
            while (true) {
                val session = receiveDeserialized<ApiSession>()
                log.i { "session - new: $session" }
                onUpdate(ConnectionUpdate.Session(session))
            }
        }

        while (true) {
            try {
                val path = "/ws?$SessionHeader=$sessionId&$AccountHeader=$accountId"
                if (Proto == URLProtocol.HTTP) client.webSocket(
                    path = path,
                    request = {
                        // TODO headers don't work in web... CORS?
                        headers[SessionHeader] = sessionId.toString()
                        headers[AccountHeader] = accountId.toString()
                        log.i { "attaching: $SessionHeader=$sessionId, $AccountHeader=$accountId" }
                    },
                ) { process() }
                else client.wss(
                    path = path,
                    request = {
                        // TODO headers don't work in web... CORS?
                        headers[SessionHeader] = sessionId.toString()
                        headers[AccountHeader] = accountId.toString()
                        log.i { "attaching: $SessionHeader=$sessionId, $AccountHeader=$accountId" }
                    }
                ) { process() }
            } catch (e: Throwable) {
                // delay is cancelled anyway, this just suppresses the error log
                if (e is CancellationException) return
                log.e(e) { "session - unexpected resp: $e" }
                onUpdate(ConnectionUpdate.Disconnected)
                delay(1.seconds)
            }
        }
    }
}

sealed interface ConnectionUpdate {
    data object Connected : ConnectionUpdate
    data object Disconnected : ConnectionUpdate
    data class Session(val session: ApiSession) : ConnectionUpdate
}
