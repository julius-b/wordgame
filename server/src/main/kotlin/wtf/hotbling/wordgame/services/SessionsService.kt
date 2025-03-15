@file:OptIn(ExperimentalUuidApi::class)

package wtf.hotbling.wordgame.services

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.datetime.Clock
import org.jetbrains.exposed.dao.CompositeEntity
import org.jetbrains.exposed.dao.CompositeEntityClass
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.CompositeID
import org.jetbrains.exposed.dao.id.CompositeIdTable
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.Random
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import org.jetbrains.exposed.sql.or
import wtf.hotbling.wordgame.api.ApiChar
import wtf.hotbling.wordgame.api.ApiCharStatus
import wtf.hotbling.wordgame.api.ApiError
import wtf.hotbling.wordgame.api.ApiGuess
import wtf.hotbling.wordgame.api.ApiSession
import wtf.hotbling.wordgame.api.ApiSessionWord
import wtf.hotbling.wordgame.api.ApiSessionWord2
import wtf.hotbling.wordgame.api.Error
import wtf.hotbling.wordgame.api.Keyboard
import wtf.hotbling.wordgame.api.constraintErr
import wtf.hotbling.wordgame.api.err
import wtf.hotbling.wordgame.api.referenceErr
import wtf.hotbling.wordgame.plugins.DatabaseSingleton.tx
import java.util.UUID
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.toKotlinUuid

object Sessions : UUIDTable() {
    val initId = reference("init", Accounts)
    val peerId = reference("peer", Accounts).nullable()
    val turnId = reference("turn", Accounts)

    val createdAt = timestamp("created_at").clientDefault {
        Clock.System.now()
    }
}

class Session(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<Session>(Sessions)

    var initId by Sessions.initId
    var peerId by Sessions.peerId
    var turnId by Sessions.turnId
    val createdAt by Sessions.createdAt

    val init by Account referencedOn Sessions.initId
    val peer by Account optionalReferencedOn Sessions.peerId

    // TODO keyboard & guesses per word, a) for matches, b) stop when solved
    val words by Word via SessionWords orderBy SessionWords.wordId

    // TODO don't leak wordId
    val swords by SessionWordEntity referrersOn SessionWords
    val guesses by Guess referrersOn Guesses.sessionId
}

object SessionWords : CompositeIdTable() {
    val sessionId = reference("session", Sessions)
    val wordId = reference("word", Words)
    val solved = bool("solved").default(false)

    init {
        addIdColumn(sessionId)
        addIdColumn(wordId)
    }

    override val primaryKey = PrimaryKey(sessionId, wordId)
}

class SessionWordEntity(id: EntityID<CompositeID>) : CompositeEntity(id) {
    companion object : CompositeEntityClass<SessionWordEntity>(SessionWords)

    val sessionId by SessionWords.sessionId
    val wordId by SessionWords.wordId
    val solved by SessionWords.solved

    val word by Word referencedOn SessionWords
}

object Guesses : UUIDTable() {
    val txt = varchar("txt", 5)
    val sessionId = reference("session", Sessions)
    val accountId = reference("account", Accounts)
    val pos = integer("pos")

    val createdAt = timestamp("created_at").clientDefault {
        Clock.System.now()
    }
}

class Guess(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<Guess>(Guesses)

    var txt by Guesses.txt
    var sessionId by Guesses.sessionId
    var accountId by Guesses.accountId
    var pos by Guesses.pos
}

class SessionsService {
    private val log = KtorSimpleLogger("sessions-svc")

    suspend fun all(): List<ApiSession> = tx {
        Session.all().map(Session::toDTO)
    }

    suspend fun get(id: UUID): ApiSession? = tx {
        Session.findById(id)?.toDTO()
    }

    suspend fun byAccount(accountId: UUID): List<ApiSession> = tx {
        Session
            .find { (Sessions.initId eq accountId) or (Sessions.peerId eq accountId) }
            .map(Session::toDTO)
    }

    suspend fun create(initId: UUID): ApiSession = tx {
        //val word = Words.selectAll().limit(1).orderBy(Random())
        //val word = Word.all().orderBy(Random() to SortOrder.ASC).limit(1).first()
        val words = Word.all().orderBy(Random() to SortOrder.ASC).limit(4).toList()
        log.info("create - words: ${words.map { Pair(it.id, it.txt) }}")

        val session = Session.new {
            this.initId = EntityID(initId, Accounts)
            this.turnId = EntityID(initId, Accounts)
        }

        for (word in words) {
            SessionWords.insert {
                it[sessionId] = session.id
                it[wordId] = word.id
            }
        }

        // return with words
        session.toDTO()
    }

    suspend fun createGuess(
        sessionId: UUID, accountId: UUID, txt: String
    ): Either<Error, ApiGuess> = tx {
        val session = Session.findById(sessionId)!!
        val account = Account.findById(accountId)!!
        if (session.turnId.value != accountId)
            return@tx ("accountId" err accountId.constraintErr(eq = "turn")).left()

        if (Word.find { Words.txt eq txt }.none())
            return@tx ("txt" err txt.referenceErr()).left()

        val latest = Guess.find {
            Guesses.sessionId eq sessionId
        }.orderBy(Guesses.pos to SortOrder.DESC).limit(1).firstOrNull()

        val nextTurn = if (session.turnId == session.initId) session.peerId!! else session.initId
        Session.findByIdAndUpdate(sessionId) {
            it.turnId = nextTurn
        }
        Guess.new {
            this.txt = txt
            this.sessionId = session.id
            this.accountId = account.id
            this.pos = (latest?.pos ?: -1) + 1
        }.toDTO().right()
    }

    suspend fun addPeer(sessionId: UUID, accountId: UUID): Error? = tx {
        val account = Account.findById(accountId)
            ?: return@tx "accountId" err ApiError.Reference(accountId.toString())
        val session = Session.findById(sessionId)
            ?: return@tx "sessionId" err ApiError.Reference(sessionId.toString())
        if (session.initId.value == accountId) return@tx null

        if (session.peerId != null) {
            if (session.peerId?.value == accountId) return@tx null
            return@tx "peerId" err ApiError.Conflict()
        }

        if (session.peerId == null) {
            Session.findByIdAndUpdate(sessionId) {
                it.peerId = account.id
            } ?: return@tx "accountId" err ApiError.Internal()
            return@tx null
        }

        return@tx null
    }

    suspend fun count() = tx {
        Session.count()
    }
}

fun Session.toDTO(): ApiSession {
    val simpleGuesses = guesses.map { Pair(it.txt, it.accountId.value) }
    return ApiSession(
        id.value.toKotlinUuid(),
        init.toDTO(),
        peer?.toDTO(),
        turnId.value.toKotlinUuid(),
        // TODO word null while state != solved
        swords.map { it.toDTO(simpleGuesses.map { it.first }) }.map {
            ApiSessionWord2(
                if (it.solved) it.word else null,
                it.guesses
                    .whileTake { guess ->
                        guess.map { it.char }.toCharArray().concatToString() != it.word
                    }
                    .zip(simpleGuesses.map { it.second })
                    .map { (a, b) -> Pair(a, b.toKotlinUuid()) },
                it.keyboard
            )
        },
        createdAt
    )
}

fun SessionWordEntity.toDTO(guesses: List<String>): ApiSessionWord {
    val clouds = buildClouds(word.txt, guesses)
    val guessRatings = cloudsToRatings(clouds, guesses)
    return ApiSessionWord(
        word.txt,
        solved,
        guessRatings,
        buildKeyboard(guessRatings)
    )
}

fun cloudsToRatings(clouds: List<List<ApiCharStatus>>, guesses: List<String>) =
    clouds.mapIndexed { i, g -> g.mapIndexed { k, s -> ApiChar(guesses[i][k], s) } }

fun buildClouds(word: String, guesses: List<String>): List<List<ApiCharStatus>> {
    val clouds = mutableListOf<List<ApiCharStatus>>()
    for (guess in guesses) {
        clouds += buildCloud(word, guess)
    }
    return clouds
}

fun buildCloud(word: String, guess: String): List<ApiCharStatus> {
    var word = word
    val cloud = MutableList(word.length) { ApiCharStatus.Wrong }
    for ((i, v) in word.zip(guess).withIndex()) {
        val (c, g) = v
        if (c == g) {
            cloud[i] = ApiCharStatus.Correct
            word = word.replaceFirst(c, '-')
        }
    }
    for ((i, g) in guess.withIndex()) {
        if (word.contains(g) && cloud[i] == ApiCharStatus.Wrong) {
            cloud[i] = ApiCharStatus.Kinda
            word = word.replaceFirst(g, '-')
        }
    }
    return cloud
}

fun buildKeyboard(ratings: List<List<ApiChar>>): Keyboard {
    val board = mutableMapOf<Char, ApiCharStatus>()
    for (rating in ratings) {
        for (char in rating) {
            val override =
                board[char.char] == null || board[char.char]!!.ordinal > char.status.ordinal
            if (override) board[char.char] = char.status
        }
    }

    return board
}

fun Guess.toDTO() =
    ApiGuess(txt, accountId.value.toKotlinUuid(), sessionId.value.toKotlinUuid(), pos)

// similar to kotlin's `takeWhile`
inline fun <T> Iterable<T>.whileTake(predicate: (T) -> Boolean): List<T> {
    val list = ArrayList<T>()
    for (item in this) {
        list.add(item)
        if (!predicate(item)) break
    }
    return list
}

val sessionsService = SessionsService()
