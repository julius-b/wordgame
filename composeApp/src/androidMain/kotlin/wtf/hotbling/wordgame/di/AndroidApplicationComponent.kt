package wtf.hotbling.wordgame.di

import android.app.Application
import android.content.Context
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Provides
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

@Component
@MergeComponent(AppScope::class)
@SingleIn(AppScope::class)
abstract class AndroidApplicationComponent(@get:Provides protected val application: Application) :
    SharedApplicationComponent, AndroidApplicationComponentMerged {

    @AppContext
    @Provides
    fun provideApplicationContext(application: Application): Context =
        application.applicationContext

    companion object
}
