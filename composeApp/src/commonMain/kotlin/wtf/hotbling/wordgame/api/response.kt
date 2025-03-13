package wtf.hotbling.wordgame.api

sealed interface RepoResult<out T : Any> {
    val loading: Boolean

    data class Data<T : Any>(
        val data: T, val cached: Boolean = false, override val loading: Boolean = false
    ) : RepoResult<T>

    // NOTE / TODO: should also include 401
    data class ValidationError(
        val errors: Errors
    ) : RepoResult<Nothing> {
        override val loading = false
    }

    // couldn't be queried, may or may not exist
    data object NetworkError : RepoResult<Nothing> {
        override val loading = false
    }

    // `loading = false`: doesn't exist, not even locally, possibly deleted (deleted_at is set)
    data class Empty(override val loading: Boolean) : RepoResult<Nothing>
}
