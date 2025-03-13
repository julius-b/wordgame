@file:OptIn(ExperimentalSerializationApi::class)

package wtf.hotbling.wordgame.api

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

const val SessionHeader = "Session"
const val AccountHeader = "Account"

@Serializable
data class ApiSuccessResponse<out T : Any>(
    @EncodeDefault(EncodeDefault.Mode.NEVER) val count: Int? = null, val data: T
)

// TODO make properties part of Actor -> no more Hints
@Serializable
data class HintedApiSuccessResponse<out T : Any, out S : Any>(
    @EncodeDefault(EncodeDefault.Mode.NEVER) val count: Int? = null, val data: T, val hints: S
)

@Serializable
data class ApiErrorResponse(
    @EncodeDefault(EncodeDefault.Mode.NEVER) val errors: Errors
)

typealias Errors = Map<String, Array<out ApiError>>

typealias Error = Pair<String, Array<out ApiError>>

fun Error(field: String, error: ApiError): Error = field to arrayOf(error)

// alternative is using to manually, so this can be infix as well
infix fun String.err(error: ApiError): Error = this to arrayOf(error)
