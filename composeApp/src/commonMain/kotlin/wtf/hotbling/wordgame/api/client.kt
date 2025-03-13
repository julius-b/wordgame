package wtf.hotbling.wordgame.api

import io.ktor.client.HttpClient
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.resources.Resources
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.pingInterval
import io.ktor.http.URLProtocol
import io.ktor.http.path
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

const val Host = "localhost" // localhost / hotbling.wtf
val Port: Int? = 8080 // 8080 / null
val Proto = URLProtocol.HTTP // HTTP / HTTPS

val Domain = "${Proto.name}://$Host${Port.orEmpty(":")}"

fun newHttpClient(): HttpClient {
    val log = co.touchlab.kermit.Logger.withTag("http-client")

    val json = Json {
        encodeDefaults = true
        explicitNulls = false
        prettyPrint = true
        isLenient = true
    }

    val wsJson = Json {
        encodeDefaults = true
        explicitNulls = false
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
    }

    return HttpClient {
        install(Resources)
        install(WebSockets) {
            pingInterval = 15.seconds
            contentConverter = KotlinxWebsocketSerializationConverter(wsJson)
        }
        install(ContentNegotiation) {
            json(json)
        }
        install(UserAgent) {
            agent = "WordGame vX"
        }
        defaultRequest {
            host = Host
            Port?.let { port = it }
            url {
                protocol = Proto
                path("api/v1/")
            }
        }
        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) {
                    log.d { message }
                }
            }

            // TODO ALL except multipart body...
            level = LogLevel.BODY
            //sanitizeHeader { header -> header == HttpHeaders.Authorization }
        }
    }
}

// in cases where string "null" is not wanted
fun Int?.orEmpty(prefix: String = "") = this?.let { "$prefix$it" } ?: ""
