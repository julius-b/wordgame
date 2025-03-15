@file:OptIn(ExperimentalUuidApi::class)

package wtf.hotbling.wordgame

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.touchlab.kermit.Logger
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.internal.rememberStableCoroutineScope
import com.slack.circuit.runtime.presenter.Presenter
import com.slack.circuit.runtime.screen.Screen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import wtf.hotbling.wordgame.MainScreen.Event.CreateSession
import wtf.hotbling.wordgame.MainScreen.Event.GoToSource
import wtf.hotbling.wordgame.MainScreen.Event.JoinSession
import wtf.hotbling.wordgame.MainScreen.Event.ToggleManual
import wtf.hotbling.wordgame.api.AccountRepository
import wtf.hotbling.wordgame.api.ApiSession
import wtf.hotbling.wordgame.api.NameMaxLen
import wtf.hotbling.wordgame.api.RepoResult
import wtf.hotbling.wordgame.api.SessionRepository
import wtf.hotbling.wordgame.components.SimpleDialog
import wtf.hotbling.wordgame.parcel.CommonParcelize
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@CommonParcelize
data object MainScreen : Screen {
    data class State(
        val loading: Boolean,
        val manualPrompt: Boolean,
        val sessions: List<ApiSession>?,
        val snackbarHostState: SnackbarHostState,
        val eventSink: (Event) -> Unit
    ) : CircuitUiState

    sealed interface Event : CircuitUiEvent {
        data object ToggleManual : Event
        data class CreateSession(val name: String) : Event
        data class JoinSession(val link: String) : Event
        data object GoToSource : Event
    }
}

@CircuitInject(MainScreen::class, AppScope::class)
@Inject
class MainPresenter(
    @Assisted private val navigator: Navigator,
    private val accountRepository: AccountRepository,
    private val sessionRepository: SessionRepository
) : Presenter<MainScreen.State> {
    private val log = Logger.withTag("MainScreen")

    @Composable
    override fun present(): MainScreen.State {
        val scope = rememberStableCoroutineScope()
        val uriHandler = LocalUriHandler.current
        var loading by rememberRetained { mutableStateOf(false) }
        var manualPrompt by rememberRetained { mutableStateOf(false) }
        val snackbarHostState = remember { SnackbarHostState() }

        fun showSnackbar(text: String) {
            scope.launch(Dispatchers.Main) {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(text, duration = SnackbarDuration.Short)
            }
        }

        // TODO load previous games & name at this point via api
        LaunchedEffect(Unit) {
            // retry loading account & all it's session (2nd request) until successful
            setPath("")
        }

        return MainScreen.State(
            loading, manualPrompt, null, snackbarHostState
        ) { event ->
            when (event) {
                is CreateSession -> {
                    if (loading) return@State
                    loading = true
                    scope.launch {
                        // find or create account
                        val account = accountRepository.saveAccount(event.name)
                        if (account !is RepoResult.Data) {
                            showSnackbar("No internet, please try again later")
                            return@launch
                        }
                        val session = sessionRepository.newSession()
                        if (session !is RepoResult.Data) {
                            showSnackbar("No internet, please try again later")
                            return@launch
                        }
                        navigator.goTo(GameScreen(session.data.id))
                    }.invokeOnCompletion {
                        loading = false
                    }
                }

                is JoinSession -> {
                    if (loading) return@State
                    loading = true
                    try {
                        val sessionId = Uuid.parse(event.link.split('/').last())
                        navigator.goTo(GameScreen(sessionId))
                    } catch (e: Throwable) {
                        log.w(e) { "bad code" }
                    } finally {
                        loading = false
                    }
                }

                ToggleManual -> manualPrompt = !manualPrompt
                GoToSource -> uriHandler.openUri("https://github.com/julius-b/wordgame")
            }
        }
    }
}

@CircuitInject(MainScreen::class, AppScope::class)
@Composable
fun MainView(state: MainScreen.State, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(hostState = state.snackbarHostState) }
    ) { innerPadding ->
        if (state.manualPrompt) {
            SimpleDialog(
                title = "Enter a code:",
                onDismiss = { state.eventSink(ToggleManual) },
                canDismiss = !state.loading
            ) {
                var code by rememberRetained { mutableStateOf("") }
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Link / code") }
                )
                Button(
                    onClick = { state.eventSink(JoinSession(code)) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = code.isNotBlank(),
                    // match OutlinedTextField default
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Icon(
                        Icons.Default.PlayArrow, contentDescription = ""
                    )
                    Spacer(Modifier.height(8.dp))
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text("Join Friend", fontSize = 14.sp)
                }
            }
        }

        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            SectionBoundary {
                Header(Modifier.padding(top = 24.dp, bottom = 32.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(
                        modifier = Modifier.width(512.dp),
                    ) {
                        var name by rememberRetained { mutableStateOf("") }
                        val nameValid = name.length <= NameMaxLen
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Enter a name") },
                            singleLine = true,
                            isError = !nameValid,
                            leadingIcon = {
                                Icon(Icons.Default.AccountCircle, contentDescription = "")
                            },
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { state.eventSink(CreateSession(name)) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = name.isNotBlank() && nameValid,
                            // match OutlinedTextField default
                            shape = MaterialTheme.shapes.extraSmall
                        ) {
                            Icon(
                                Icons.Default.PlayArrow, contentDescription = ""
                            )
                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                            Text("New Game", fontSize = 24.sp)
                        }
                        Spacer(Modifier.height(8.dp))

                        SessionsList(state.sessions)

                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            IconButton(
                                onClick = { state.eventSink(ToggleManual) },
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Enter code"
                                )
                            }
                            IconButton(
                                onClick = { state.eventSink(GoToSource) },
                            ) {
                                Icon(
                                    Icons.Default.Build,
                                    contentDescription = "Check out the source code"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SessionsList(sessions: List<ApiSession>?) {
    if (sessions == null) {
        // TODO just loading animated icon
        Text("Loading past games...")
        return
    }
}
