@file:OptIn(ExperimentalUuidApi::class)

package wtf.hotbling.wordgame.services

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import wtf.hotbling.wordgame.api.ApiAccount
import wtf.hotbling.wordgame.plugins.DatabaseSingleton.tx
import java.util.UUID
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.toKotlinUuid

object Accounts : UUIDTable() {
    val name = varchar("name", 50)
}

class Account(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<Account>(Accounts)

    var name by Accounts.name
}

class AccountsService {
    suspend fun all(): List<ApiAccount> = tx {
        Account.all().map(Account::toDTO)
    }

    suspend fun get(id: UUID): ApiAccount? = tx {
        Account.findById(id)?.toDTO()
    }

    suspend fun upsert(id: UUID?, name: String): ApiAccount = tx {
        val curr = id?.let {
            Account.findByIdAndUpdate(it) {
                it.name = name
            }?.toDTO()
        }
        if (curr != null) return@tx curr

        Account.new(id) {
            this.name = name
        }.toDTO()
    }
}

fun Account.toDTO() = ApiAccount(
    id.value.toKotlinUuid(),
    name,
)

val accountsService = AccountsService()
