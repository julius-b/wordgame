@file:OptIn(ExperimentalUuidApi::class)

package wtf.hotbling.wordgame.services

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.Random
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import wtf.hotbling.wordgame.api.ApiWord
import wtf.hotbling.wordgame.plugins.DatabaseSingleton.tx
import java.util.UUID
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.toKotlinUuid

// TODO usage stats, specific likelihood, blocklist (to keep filter when adding new wordlist)

object Words : UUIDTable() {
    val txt = varchar("txt", 50)
    val solution = bool("solution")

    init {
        uniqueIndex(txt)
    }
}

class Word(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<Word>(Words)

    var txt by Words.txt
    var solution by Words.solution
}

class WordsService {
    suspend fun all(): List<ApiWord> = tx {
        Word.all().map(Word::toDTO)
    }

    suspend fun create(word: String, solution: Boolean): ApiWord = tx {
        val txt = word.lowercase()
        val curr = Word.find { Words.txt eq txt }.firstOrNull()?.toDTO()
        if (curr != null) {
            if (curr.solution != solution) throw IllegalStateException("dup: $txt")
            return@tx curr
        }
        Word.new {
            this.txt = txt
            this.solution = solution
        }.toDTO()
    }

    suspend fun random(solution: Boolean?): ApiWord = tx {
        val wordIt = if (solution == null) Word.all()
        else Word.find { Words.solution eq solution }
        // orderBy approach guarantees no dups are selected
        val word = wordIt.orderBy(Random() to SortOrder.ASC).limit(1).first()
        return@tx ApiWord(word.id.value.toKotlinUuid(), word.txt, word.solution)
    }

    suspend fun count() = tx {
        Word.count()
    }

    suspend fun countSolutions() = tx {
        Word.count(Words.solution eq true)
    }
}

fun Word.toDTO() = ApiWord(
    id.value.toKotlinUuid(),
    txt,
    solution
)

val wordsService = WordsService()
