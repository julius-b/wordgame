package wtf.hotbling.wordgame.di

import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Provides
import org.w3c.dom.Window
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

@Component
@MergeComponent(AppScope::class)
@SingleIn(AppScope::class)
abstract class WasmJsApplicationComponent(@get:Provides protected val window: Window) :
    SharedApplicationComponent, WasmJsApplicationComponentMerged {

    companion object
}
