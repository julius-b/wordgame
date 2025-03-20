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
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import wtf.hotbling.wordgame.api.ApiChar
import wtf.hotbling.wordgame.api.ApiCharStatus
import wtf.hotbling.wordgame.api.ApiError
import wtf.hotbling.wordgame.api.ApiGuess
import wtf.hotbling.wordgame.api.ApiSession
import wtf.hotbling.wordgame.api.ApiSessionWord
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
    val turn = integer("turn").default(0)

    // no. of accounts
    val size = integer("size")

    // no. of attempts
    val limit = integer("limit").nullable()

    val createdAt = timestamp("created_at").clientDefault {
        Clock.System.now()
    }
}

class Session(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<Session>(Sessions)

    var turn by Sessions.turn
    var size by Sessions.size
    var limit by Sessions.limit
    val createdAt by Sessions.createdAt

    val peers by Peer referrersOn Peers orderBy Peers.pos
    val words by Word via SessionWords orderBy SessionWords.wordId
    val swords by SessionWord referrersOn SessionWords
    val guesses by Guess referrersOn Guesses.sessionId
}

object Peers : CompositeIdTable() {
    val sessionId = reference("session", Sessions)
    val accountId = reference("account", Accounts)
    val pos = integer("pos")

    init {
        addIdColumn(sessionId)
        addIdColumn(accountId)
    }

    override val primaryKey = PrimaryKey(sessionId, accountId)
}

class Peer(id: EntityID<CompositeID>) : CompositeEntity(id) {
    companion object : CompositeEntityClass<Peer>(Peers)

    var sessionId by Peers.sessionId
    var accountId by Peers.accountId
    var pos by Peers.pos

    val session by Session referencedOn Peers
    val account by Account referencedOn Peers
}

object SessionWords : CompositeIdTable() {
    val sessionId = reference("session", Sessions)
    val wordId = reference("word", Words)
    val solved = integer("solved").nullable().default(null)

    init {
        addIdColumn(sessionId)
        addIdColumn(wordId)
    }

    override val primaryKey = PrimaryKey(sessionId, wordId)
}

class SessionWord(id: EntityID<CompositeID>) : CompositeEntity(id) {
    companion object : CompositeEntityClass<SessionWord>(SessionWords)

    val sessionId by SessionWords.sessionId
    val wordId by SessionWords.wordId
    var solved by SessionWords.solved

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

data class SessionWordDAO(
    val word: String,
    val solved: Int?,
    val guesses: List<List<ApiChar>>,
    val keyboard: Keyboard
) {
    val isSolved: Boolean = solved != null
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
        Peer.find { (Peers.accountId eq accountId) }
            .map { it.session.toDTO() }
    }

    suspend fun create(accountId: UUID, size: Int, limit: Int?): ApiSession = tx {
        //val word = Words.selectAll().limit(1).orderBy(Random())
        //val word = Word.all().orderBy(Random() to SortOrder.ASC).limit(1).first()
        val words = Word.all().orderBy(Random() to SortOrder.ASC).limit(4).toList()
        log.info("create - words: ${words.map { Pair(it.id, it.txt) }}")

        val account = Account.findById(accountId)!!

        val session = Session.new {
            this.size = size
            this.limit = limit
        }

        Peer.new {
            this.sessionId = session.id
            this.accountId = account.id
            this.pos = 0
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
        if (session.turn != session.peers.indexOfFirst { it.accountId == account.id })
            return@tx ("accountId" err accountId.constraintErr(eq = "turn")).left()

        val match = Word.find { Words.txt eq txt }.firstOrNull()
        if (match == null)
            return@tx ("txt" err txt.referenceErr()).left()

        val latest = Guess.find {
            Guesses.sessionId eq sessionId
        }.orderBy(Guesses.pos to SortOrder.DESC).limit(1).firstOrNull()
        val guessPos = (latest?.pos ?: -1) + 1

        // Entity join TODO
        SessionWord.findSingleByAndUpdate(
            (SessionWords.sessionId eq session.id) and
                    (SessionWords.wordId eq match.id) and
                    (SessionWords.solved eq null)
        ) {
            it.solved = guessPos
        }

        val nextTurn =
            if (session.turn + 1 == session.peers.count().toInt()) 0 else session.turn + 1
        Session.findByIdAndUpdate(sessionId) {
            it.turn = nextTurn
        }
        Guess.new {
            this.txt = txt
            this.sessionId = session.id
            this.accountId = account.id
            this.pos = guessPos
        }.toDTO(nextTurn).right()
    }

    suspend fun addPeer(sessionId: UUID, accountId: UUID): Error? = tx {
        val session = Session.findById(sessionId)
            ?: return@tx "sessionId" err sessionId.referenceErr()
        val account = Account.findById(accountId)
            ?: return@tx "accountId" err accountId.referenceErr()

        if (session.peers.any { it.accountId.value == accountId })
            return@tx null
        // TODO maybe ApiError.Conflict would be simpler
        if (session.peers.count().toInt() == session.size)
            return@tx "size" err ApiError.Constraint(max = session.size.toLong())

        val existingPos = session.peers.map { it.pos }
        val possiblePos = (0 until session.size).filter { existingPos.contains(it) }
        val pos = possiblePos.random()

        Peer.new {
            this.sessionId = session.id
            this.accountId = account.id
            this.pos = pos
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
        turn,
        size,
        limit,
        peers.map { it.account.toDTO() },
        swords.map { it.toDTO(simpleGuesses.map { it.first }) }.map {
            ApiSessionWord(
                if (it.isSolved) it.word else null,
                it.solved,
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

fun SessionWord.toDTO(guesses: List<String>): SessionWordDAO {
    val clouds = buildClouds(word.txt, guesses)
    val guessRatings = cloudsToRatings(clouds, guesses)
    return SessionWordDAO(
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

fun Guess.toDTO(nextTurn: Int?) =
    ApiGuess(txt, accountId.value.toKotlinUuid(), sessionId.value.toKotlinUuid(), pos, nextTurn)

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
