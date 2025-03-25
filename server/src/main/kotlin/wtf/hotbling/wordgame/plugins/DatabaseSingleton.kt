package wtf.hotbling.wordgame.plugins

import io.ktor.server.config.ApplicationConfig
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import wtf.hotbling.wordgame.services.Accounts
import wtf.hotbling.wordgame.services.Guesses
import wtf.hotbling.wordgame.services.Peers
import wtf.hotbling.wordgame.services.SessionWords
import wtf.hotbling.wordgame.services.Sessions
import wtf.hotbling.wordgame.services.Words

object DatabaseSingleton {
    private val LOGGER = KtorSimpleLogger("DB")

    fun init(config: ApplicationConfig) {
        val driver = config.property("storage.driver").getString()
        val url = config.property("storage.url").getString()

        LOGGER.info("driver: $driver, url: $url")
        val database = Database.connect(url, driver)
        transaction(database) {
            SchemaUtils.create(Words)
            SchemaUtils.create(Accounts)
            SchemaUtils.create(Sessions)
            SchemaUtils.create(Peers)
            SchemaUtils.create(SessionWords)
            SchemaUtils.create(Guesses)
        }
    }

    suspend fun <T> tx(
        transactionIsolation: Int? = null, readOnly: Boolean = false, block: suspend () -> T
    ): T = newSuspendedTransaction(
        Dispatchers.IO, transactionIsolation = transactionIsolation, readOnly = readOnly
    ) { block() }
}
