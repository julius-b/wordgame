@file:OptIn(ExperimentalUuidApi::class)

package wtf.hotbling.wordgame.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.resources.get
import io.ktor.server.resources.put
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.util.logging.KtorSimpleLogger
import wtf.hotbling.wordgame.api.AccountParams
import wtf.hotbling.wordgame.api.Accounts
import wtf.hotbling.wordgame.api.ApiError
import wtf.hotbling.wordgame.api.ApiErrorResponse
import wtf.hotbling.wordgame.api.ApiSuccessResponse
import wtf.hotbling.wordgame.api.err
import wtf.hotbling.wordgame.services.accountsService
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.toJavaUuid

fun Route.accountsApi() {
    val log = KtorSimpleLogger("accounts-api")

    put<Accounts> {
        val req = call.receive<AccountParams>().sanitize()
        val account = accountsService.upsert(req.id?.toJavaUuid(), req.name)
        call.respond(ApiSuccessResponse(data = account))
    }
    get<Accounts.Id> { params ->
        val account = accountsService.get(params.id.toJavaUuid())
        if (account == null) {
            // TODO throw
            call.respond(
                HttpStatusCode.UnprocessableEntity,
                ApiErrorResponse(mapOf("id" err ApiError.Reference(params.id.toString())))
            )
            return@get
        }
        call.respond(ApiSuccessResponse(data = account))
    }
}

fun AccountParams.sanitize() = AccountParams(
    id, name.trim()
)
