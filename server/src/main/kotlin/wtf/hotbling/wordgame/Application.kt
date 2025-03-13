package wtf.hotbling.wordgame

import io.ktor.server.application.Application
import io.ktor.server.netty.EngineMain
import wtf.hotbling.wordgame.plugins.configureDatabases
import wtf.hotbling.wordgame.plugins.configureRouting
import wtf.hotbling.wordgame.plugins.configureSerialization
import wtf.hotbling.wordgame.plugins.configureSockets
import wtf.hotbling.wordgame.plugins.configureWords

fun main(args: Array<String>): Unit = EngineMain.main(args)

fun Application.module() {
    configureDatabases()
    configureWords()
    configureSerialization()
    configureSockets()
    configureRouting()
}
