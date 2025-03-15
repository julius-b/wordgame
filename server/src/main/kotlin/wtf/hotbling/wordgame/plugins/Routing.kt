package wtf.hotbling.wordgame.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.http.content.staticFiles
import io.ktor.server.resources.Resources
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import wtf.hotbling.wordgame.api.ApiStatus
import wtf.hotbling.wordgame.api.ApiSuccessResponse
import wtf.hotbling.wordgame.getPlatform
import wtf.hotbling.wordgame.routes.accountsApi
import wtf.hotbling.wordgame.routes.sessionsApi
import wtf.hotbling.wordgame.routes.wordsApi
import wtf.hotbling.wordgame.services.accountsService
import wtf.hotbling.wordgame.services.sessionsService
import wtf.hotbling.wordgame.services.wordsService
import java.io.File

fun Application.configureRouting() {
    install(Resources)
    routing {
        val frontend =
            if (developmentMode) "../composeApp/build/dist/wasmJs/developmentExecutable" else "frontend"
        staticFiles("/", File(frontend)) {
            // TODO test if wasm compression efficient
            // preCompressed(CompressedFileType.BROTLI, CompressedFileType.GZIP)
        }
        route("api") {
            route("v1") {
                accountsApi()
                sessionsApi()
                wordsApi()
            }
        }
        get("status") {
            call.respond(
                ApiSuccessResponse(
                    data = ApiStatus(
                        getPlatform().name, developmentMode,
                        entities = mapOf(
                            "words" to wordsService.count(),
                            "solutions" to wordsService.countSolutions(),
                            "accounts" to accountsService.count(),
                            "sessions" to sessionsService.count()
                        )
                    )
                )
            )
        }
    }
}
