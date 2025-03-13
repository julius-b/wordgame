package wtf.hotbling.wordgame.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.http.content.staticFiles
import io.ktor.server.resources.Resources
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import wtf.hotbling.wordgame.getPlatform
import wtf.hotbling.wordgame.routes.accountsApi
import wtf.hotbling.wordgame.routes.sessionsApi
import wtf.hotbling.wordgame.routes.wordsApi
import java.io.File

fun Application.configureRouting() {
    install(Resources)
    routing {
        //staticFiles("/", File("frontend")) {
        staticFiles("/", File("../composeApp/build/dist/wasmJs/developmentExecutable")) {
            // TODO test if wasm compression efficient
            // preCompressed(CompressedFileType.BROTLI, CompressedFileType.GZIP)
        }
        get("/status") {
            call.respondText("Ktor: ${getPlatform().name}")
        }
        route("api") {
            route("v1") {
                accountsApi()
                sessionsApi()
                wordsApi()
            }
        }
    }
}
