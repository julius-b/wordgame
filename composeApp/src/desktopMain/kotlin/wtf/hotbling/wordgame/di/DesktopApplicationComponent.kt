package wtf.hotbling.wordgame.di

import me.tatarka.inject.annotations.Component
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import wtf.hotbling.wordgame.WordGameApp

@Component
@MergeComponent(AppScope::class)
@SingleIn(AppScope::class)
abstract class DesktopApplicationComponent : SharedApplicationComponent,
    DesktopApplicationComponentMerged {

    abstract val wordGameApp: WordGameApp

    companion object
}
