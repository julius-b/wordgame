package wtf.hotbling.wordgame.plugins

import io.ktor.server.application.Application
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.runBlocking
import wtf.hotbling.wordgame.plugins.DatabaseSingleton.tx
import wtf.hotbling.wordgame.services.wordsService
import java.io.File
import java.util.Scanner

fun Application.configureWords() {
    val log = KtorSimpleLogger("cfg-words")

    val allowed = File("../data/official/official_allowed_guesses.txt")
    val solutions = File("../data/official/shuffled_real_wordles.txt")
    if (!allowed.exists()) throw IllegalStateException("file allowed required")
    if (!solutions.exists()) throw IllegalStateException("file real required")

    // TODO bulk load would be more efficient...
    runBlocking {
        tx {
            val allowedRes = saveWords(allowed, false)
            val solutionsRes = saveWords(solutions, true)
            log.info("allowed-res: $allowedRes")
            log.info("solutions-res: $solutionsRes")
        }
    }
}

private suspend fun Application.saveWords(wordlist: File, solution: Boolean): Pair<Int, Int> {
    val log = KtorSimpleLogger("cfg-words-save")

    var saved = 0
    var errs = 0
    val sc = Scanner(wordlist)
    while (sc.hasNextLine()) {
        val line = sc.nextLine()
        if (line.startsWith('#')) continue
        // TODO dev only
        if (saved >= 30) continue

        val valid = line.length == 5 && line.isLetters()
        if (!valid) {
            log.error("bad word: $line (wordlist: $wordlist)")
            errs++
            continue
        }
        val word = wordsService.create(line.lowercase(), solution)
        log.debug("saved: $word")
        saved++
    }
    return Pair(saved, errs)
}

fun String.isLetters() = all { it.isLetter() }
