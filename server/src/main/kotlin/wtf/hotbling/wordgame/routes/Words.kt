package wtf.hotbling.wordgame.routes

import io.ktor.server.resources.get
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.util.logging.KtorSimpleLogger
import wtf.hotbling.wordgame.api.ApiSuccessResponse
import wtf.hotbling.wordgame.api.Words
import wtf.hotbling.wordgame.services.wordsService

fun Route.wordsApi() {
    val log = KtorSimpleLogger("sessions-api")

    get<Words.Random> {
        val word = wordsService.random(null)
        call.respond(ApiSuccessResponse(data = word))
    }
    get<Words.Solution> {
        val word = wordsService.random(true)
        call.respond(ApiSuccessResponse(data = word))
    }
}
