@file:OptIn(ExperimentalUuidApi::class)

package wtf.hotbling.wordgame.api

import io.ktor.resources.Resource
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

const val NameMaxLen = 20

typealias Keyboard = Map<Char, ApiCharStatus>

@Resource("accounts")
class Accounts {
    @Resource("{id}")
    class Id(val parent: Accounts = Accounts(), val id: Uuid)
}

@Resource("sessions")
class Sessions {
    @Resource("{id}")
    class Id(val parent: Sessions = Sessions(), val id: Uuid)

    @Resource("by-account/{accountId}")
    class ByAccount(val parent: Sessions = Sessions(), val accountId: Uuid)
}

@Resource("guesses")
class Guesses

@Resource("words")
class Words {
    @Resource("random")
    class Random(val parent: Words = Words())

    @Resource("solution")
    class Solution(val parent: Words = Words())
}

@Serializable
data class ApiAccount(
    val id: Uuid,
    val name: String
)

@Serializable
data class AccountParams(
    val id: Uuid? = null,
    val name: String
)

@Serializable
data class ApiSession(
    val id: Uuid,
    val turn: Int,
    val size: Int,
    val limit: Int?,
    val peers: List<ApiAccount>,
    val words: List<ApiSessionWord>,
    //val guesses: List<ApiGuess>,
    @SerialName("created_at") val createdAt: Instant,
) {
    val turnId = peers[turn].id

    val isSolved = words.all { it.solved != null }

    // if limit is null, all solved is still over
    val isOver = isSolved || words.any { it.guesses.size == limit }

    sealed interface SessionState {
        data class Loading(val connected: Int) : SessionState
        data object Game : SessionState
        data class Done(val solved: Boolean) : SessionState
    }

    fun status(): SessionState = when {
        peers.size != size -> SessionState.Loading(peers.size)
        isOver -> SessionState.Done(isSolved)
        else -> SessionState.Game
    }
}

@Serializable
data class SessionParams(
    @SerialName("account_id") val accountId: Uuid,
    @SerialName("size") val size: Int,
    @SerialName("max") val max: Int?
)

@Serializable
data class ApiSessionWord(
    // revealed when session is done
    val word: String?,
    val solved: Int?,
    val guesses: List<Pair<List<ApiChar>, Uuid>>,
    val keyboard: Keyboard
)

@Serializable
data class ApiGuess(
    val txt: String,
    @SerialName("account_id") val accountId: Uuid,
    @SerialName("session_id") val sessionId: Uuid,
    val pos: Int,
    val nextTurn: Int?
)

@Serializable
data class GuessParams(
    @SerialName("session_id") val sessionId: Uuid,
    @SerialName("account_id") val accountId: Uuid,
    val txt: String
)

@Serializable
data class ApiChar(
    val char: Char,
    val status: ApiCharStatus
)

// ordinal: lowest is "best"
@Serializable
enum class ApiCharStatus {
    @SerialName("correct")
    Correct,

    @SerialName("kinda")
    Kinda,

    @SerialName("wrong")
    Wrong
}

@Serializable
data class ApiWord(
    val id: Uuid,
    val txt: String,
    val solution: Boolean
)

@Serializable
data class ApiStatus(
    val platform: String,
    val dev: Boolean,
    val entities: Map<String, Long>
)
