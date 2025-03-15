package wtf.hotbling.wordgame.di

import com.russhwolf.settings.Settings
import com.slack.circuit.foundation.Circuit
import com.slack.circuit.runtime.presenter.Presenter
import com.slack.circuit.runtime.ui.Ui
import io.ktor.client.HttpClient
import me.tatarka.inject.annotations.Provides
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import wtf.hotbling.wordgame.WordGameApp
import wtf.hotbling.wordgame.api.AccountRepository
import wtf.hotbling.wordgame.api.SessionRepository
import wtf.hotbling.wordgame.api.newHttpClient

@SingleIn(AppScope::class)
interface SharedApplicationComponent {
    val httpClient: HttpClient
    val accountRepository: AccountRepository
    val sessionRepository: SessionRepository
    val settings: Settings

    val presenterFactories: Set<Presenter.Factory>
    val uiFactories: Set<Ui.Factory>
    val circuit: Circuit

    val wordGameApp: WordGameApp

    @Provides
    @SingleIn(AppScope::class)
    fun provideSettings(): Settings = Settings()

    @Provides
    @SingleIn(AppScope::class)
    fun provideHttpClient(): HttpClient = newHttpClient()

    @Provides
    @SingleIn(AppScope::class)
    fun circuit(presenterFactories: Set<Presenter.Factory>, uiFactories: Set<Ui.Factory>): Circuit {
        return Circuit.Builder()
            .addPresenterFactories(presenterFactories)
            .addUiFactories(uiFactories)
            .build()
    }
}
