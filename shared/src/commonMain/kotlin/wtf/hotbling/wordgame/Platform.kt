package wtf.hotbling.wordgame

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform