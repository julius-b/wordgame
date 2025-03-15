package wtf.hotbling.wordgame

import android.app.Application
import wtf.hotbling.wordgame.di.AndroidApplicationComponent
import wtf.hotbling.wordgame.di.create

class ClipboardApplication : Application() {

    val component: AndroidApplicationComponent by lazy {
        AndroidApplicationComponent.create(this)
    }
    //val component = AndroidApplicationComponent::class.create(this)

}
