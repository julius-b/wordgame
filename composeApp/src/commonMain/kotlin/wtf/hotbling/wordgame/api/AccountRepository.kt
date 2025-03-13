@file:OptIn(ExperimentalUuidApi::class)

package wtf.hotbling.wordgame.api

import co.touchlab.kermit.Logger
import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.resources.get
import io.ktor.client.plugins.resources.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Inject
@SingleIn(AppScope::class)
class AccountRepository(private val client: HttpClient, private val settings: Settings) {
    private val log = Logger.withTag("AccountRepo")

    // sync lock
    private val mutex = Mutex()

    suspend fun self(): RepoResult<ApiAccount> = mutex.withLock {
        val currId = getAccountIdSync()
        log.i { "self(currId=$currId)" }
        if (currId == null) return RepoResult.Empty(false)
        try {
            val resp = client.get(Accounts.Id(id = currId))
            if (!resp.status.isSuccess()) {
                log.e { "self(currId=$currId) - failed: $resp" }
                val err = resp.body<ApiErrorResponse>()
                return RepoResult.ValidationError(err.errors)
            }
            val success = resp.body<ApiSuccessResponse<ApiAccount>>()
            val account = success.data
            log.d { "self(currId=$currId) - success: $success" }
            settings[ACCOUNT_KEY] = account.id.toString()

            return RepoResult.Data(account)
        } catch (e: Throwable) {
            log.e(e) { "self(currId=$currId) - unexpected resp: $e" }
            // TODO special handling?
            //if (e is SerializationException) { }
            return RepoResult.NetworkError
        }
    }

    suspend fun saveAccount(name: String): RepoResult<ApiAccount> = mutex.withLock {
        val currId = getAccountIdSync()
        log.i { "init-account(currId=$currId,name='$name')" }
        try {
            val resp = client.put(Accounts()) {
                contentType(ContentType.Application.Json)
                setBody(AccountParams(currId, name))
            }
            if (!resp.status.isSuccess()) {
                log.e { "init-account(currId=$currId,name='$name') - failed: $resp" }
                val err = resp.body<ApiErrorResponse>()
                return RepoResult.ValidationError(err.errors)
            }
            val success = resp.body<ApiSuccessResponse<ApiAccount>>()
            val account = success.data
            log.d { "init-account(currId=$currId,name='$name') - success: $success" }
            settings[ACCOUNT_KEY] = account.id.toString()

            return RepoResult.Data(account)
        } catch (e: Throwable) {
            log.e(e) { "init-account(currId=$currId,name='$name') - unexpected resp: $e" }
            return RepoResult.NetworkError
        }
    }

    suspend fun getAccountId(): Uuid? = mutex.withLock {
        // TODO https://github.com/russhwolf/multiplatform-settings?tab=readme-ov-file#serialization-module
        return@withLock settings.getStringOrNull(ACCOUNT_KEY)?.let { Uuid.parse(it) }
    }

    fun getAccountIdSync(): Uuid? = settings.getStringOrNull(ACCOUNT_KEY)?.let { Uuid.parse(it) }

    companion object {
        const val ACCOUNT_KEY = "ACCOUNT_ID"
    }
}
