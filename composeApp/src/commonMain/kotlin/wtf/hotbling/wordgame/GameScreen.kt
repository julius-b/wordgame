@file:OptIn(ExperimentalUuidApi::class)

package wtf.hotbling.wordgame

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
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
import wtf.hotbling.wordgame.GameScreen.GameEvent.Key
import wtf.hotbling.wordgame.GameScreen.LoadingEvent.Cancel
import wtf.hotbling.wordgame.GameScreen.LoadingEvent.CopyLink
import wtf.hotbling.wordgame.GameScreen.LoadingEvent.Join
import wtf.hotbling.wordgame.GameScreen.State.Game
import wtf.hotbling.wordgame.GameScreen.State.Loading
import wtf.hotbling.wordgame.api.AccountRepository
import wtf.hotbling.wordgame.api.ApiChar
import wtf.hotbling.wordgame.api.ApiCharStatus
import wtf.hotbling.wordgame.api.ApiSession
import wtf.hotbling.wordgame.api.ConnectionUpdate
import wtf.hotbling.wordgame.api.Domain
import wtf.hotbling.wordgame.api.RepoResult
import wtf.hotbling.wordgame.api.SessionRepository
import wtf.hotbling.wordgame.components.SimpleDialog
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// NOTE: hide header for now so it's not too flashy

sealed interface GuessRow {
    data class Attempt(val chars: List<ApiChar>, val accountId: Uuid) : GuessRow
    data class Current(val chars: String) : GuessRow
    data object Empty : GuessRow
}

data class GameScreen(val sessionId: Uuid) : Screen {
    sealed interface State : CircuitUiState {
        val selfId: Uuid?
        val snackbarHostState: SnackbarHostState

        data class Loading(
            // relevant for peer (can't connect until account set)
            override val selfId: Uuid?,
            // relevant for init (can connect)
            val sessionId: Uuid?,
            val peerId: Uuid?,
            override val snackbarHostState: SnackbarHostState,
            val eventSink: (LoadingEvent) -> Unit
        ) : State

        data class Game(
            override val selfId: Uuid,
            val session: ApiSession,
            val guess: String,
            val rows: List<List<GuessRow>>,
            override val snackbarHostState: SnackbarHostState,
            val eventSink: (GameEvent) -> Unit
        ) : State
    }

    sealed interface LoadingEvent : CircuitUiEvent {
        data object CopyLink : LoadingEvent
        data object Cancel : LoadingEvent
        data class Join(val name: String) : LoadingEvent
    }

    sealed interface GameEvent : CircuitUiEvent {
        data class Key(val key: KeyPress) : GameEvent
    }
}

sealed interface KeyPress {
    data object Enter : KeyPress
    data object Backspace : KeyPress
    data class Letter(val char: Char) : KeyPress
}

@CircuitInject(GameScreen::class, AppScope::class)
@Inject
class GamePresenter(
    @Assisted private val screen: GameScreen,
    @Assisted private val navigator: Navigator,
    private val accountRepository: AccountRepository,
    private val sessionRepository: SessionRepository
) : Presenter<GameScreen.State> {
    private val log = Logger.withTag("GameScreen")

    @Composable
    override fun present(): GameScreen.State {
        val scope = rememberStableCoroutineScope()
        var loading by rememberRetained { mutableStateOf(false) }
        log.i { "init: ${accountRepository.getAccountIdSync()}" }
        // session creator has account-it
        var selfId: Uuid? by rememberRetained { mutableStateOf(accountRepository.getAccountIdSync()) }
        var session: ApiSession? by rememberRetained { mutableStateOf(null) }
        val snackbarHostState = remember { SnackbarHostState() }

        fun showSnackbar(text: String) {
            scope.launch(Dispatchers.Main) {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(text, duration = SnackbarDuration.Short)
            }
        }

        LaunchedEffect(selfId) {
            // never set to null
            if (selfId == null) return@LaunchedEffect
            sessionRepository.connect(
                screen.sessionId,
                selfId!!,
                onUpdate = { update ->
                    log.i { "update: $update" }
                    when (update) {
                        ConnectionUpdate.Connected -> {}
                        ConnectionUpdate.Disconnected -> {
                            if (session != null) {
                                showSnackbar("Connection lost")
                                session = null
                            }
                        }

                        is ConnectionUpdate.Session -> {
                            if (session == update.session) return@connect
                            session = update.session
                        }
                    }
                }
            )
            // connect only returns on fatal error, such as account-id doesn't exit
            navigator.pop()
        }

        val peerId = session?.peer?.id

        return when {
            selfId != null && session != null && peerId != null -> {
                // TODO capture non-null
                var guess by rememberRetained { mutableStateOf("") }
                val rows: List<List<GuessRow>> = rememberRetained(guess, session) {
                    List(4) { quadrant ->
                        val list: MutableList<GuessRow> = mutableListOf()
                        //for (attempt in session!!.guesses) {
                        for (attempt in session!!.words[quadrant].guesses) {
                            //list += GuessRow.Attempt(attempt.shards[quadrant], attempt.accountId)
                            list += GuessRow.Attempt(attempt.first, attempt.second)
                        }
                        list += GuessRow.Current(guess)
                        while (list.size < 5) {
                            list += GuessRow.Empty
                        }
                        list
                    }
                }

                // TODO integrate with circuit eventSink
                LaunchedEffect(Unit) {
                    KeyPressObserver.keyPressed.collect {
                        if (guess.length > 4) return@collect
                        guess += it
                    }
                }

                Game(selfId!!, session!!, guess, rows, snackbarHostState) { event ->
                    when (event) {
                        is Key -> {
                            if (session!!.turnId != selfId!!) {
                                log.w { "not this account's turn" }
                                return@Game
                            }
                            when (event.key) {
                                KeyPress.Enter -> {
                                    if (guess.length != 5) return@Game
                                    if (loading) return@Game
                                    loading = true
                                    scope.launch {
                                        val account =
                                            sessionRepository.createGuess(session!!.id, guess)
                                        if (account !is RepoResult.Data) {
                                            showSnackbar("No internet, please try again later")
                                            return@launch
                                        }
                                        guess = ""
                                    }.invokeOnCompletion {
                                        loading = false
                                    }
                                }

                                KeyPress.Backspace -> {
                                    if (guess.isNotEmpty())
                                        guess = guess.dropLast(1)
                                }

                                is KeyPress.Letter -> {
                                    if (guess.length > 4) return@Game
                                    guess += event.key.char
                                }
                            }
                        }
                    }
                }
            }

            else -> {
                Loading(selfId, session?.id, session?.peer?.id, snackbarHostState) { event ->
                    when (event) {
                        is Join -> {
                            if (loading) return@Loading
                            loading = true
                            scope.launch {
                                // find or create account
                                val account = accountRepository.saveAccount(event.name)
                                if (account !is RepoResult.Data) {
                                    showSnackbar("No internet, please try again later")
                                    return@launch
                                }
                                log.i { "self-id: ${account.data.id}" }
                                selfId = account.data.id
                            }.invokeOnCompletion {
                                loading = false
                            }
                        }

                        CopyLink -> {
                            // TODO try LocalClipboardManager
                            scope.launch {
                                session?.let { setClipboard(it.id.sessionLink) }
                            }
                        }

                        Cancel -> navigator.pop()
                    }
                }
            }
        }
    }
}

@CircuitInject(GameScreen::class, AppScope::class)
@Composable
fun GameView(state: GameScreen.State, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(hostState = state.snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (state) {
                is Loading -> LoadingView(state)
                is Game -> ActionView(state)
            }
        }
    }
}

// TODO sealed class Loading
@Composable
fun ActionView(state: Game) {
    Column(
        modifier = Modifier.width(800.dp).padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // TODO LazyGrid? don't need the Lazy part...
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            GuessesView(
                modifier = Modifier.weight(1f),
                //state.session.guesses.map { it.shards.map { it[0] } },
                state.rows[0]
            )
            Spacer(Modifier.width(12.dp))
            GuessesView(
                modifier = Modifier.weight(1f),
                state.rows[1]
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            GuessesView(
                modifier = Modifier.weight(1f),
                state.rows[2]
            )
            Spacer(Modifier.width(12.dp))
            GuessesView(
                modifier = Modifier.weight(1f),
                state.rows[3]
            )
        }
        Spacer(Modifier.height(16.dp))
        KeyBoard(onKey = { state.eventSink(Key(it)) })
    }
}

@Composable
fun GuessesView(
    modifier: Modifier,
    rows: List<GuessRow>
) {
    Column(modifier.wrapContentWidth()) {
        val shape = RoundedCornerShape(12.dp)
        for (row in rows) {
            Row(
                modifier = Modifier.padding(vertical = 2.dp).wrapContentWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                var letterModifier = Modifier
                    .padding(horizontal = 2.dp)
                    //.size(50.dp)
                    .aspectRatio(1f)
                    .weight(1f)
                if (row is GuessRow.Empty)
                    letterModifier = letterModifier.border(4.dp, ColorGrey, shape)
                for (i in 0 until 5) {
                    if (row is GuessRow.Current) {
                        letterModifier =
                            if (row.chars.length > i) letterModifier.border(4.dp, ColorGrey2, shape)
                            else letterModifier.border(4.dp, ColorGrey, shape)
                    }
                    if (row is GuessRow.Attempt) {
                        letterModifier = letterModifier.clip(shape).background(
                            when (row.chars[i].status) {
                                ApiCharStatus.Correct -> ColorGreen
                                ApiCharStatus.Kinda -> ColorYellow
                                ApiCharStatus.Wrong -> ColorGrey
                            }
                        )
                    }
                    val text = when (row) {
                        is GuessRow.Attempt -> row.chars.getOrNull(i)?.char ?: ' '
                        is GuessRow.Current -> row.chars.getOrElse(i) { ' ' }
                        GuessRow.Empty -> ' '
                    }.toString()
                    Box(
                        modifier = letterModifier,
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text,
                            color = if (row is GuessRow.Attempt) Color.White else Color.Unspecified,
                            //style = MaterialTheme.typography.bodyLarge,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun KeyBoard(onKey: (KeyPress) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.width(700.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // TODO Key.Letter, Key.Action, send action to screen, render action as emoji
            val keyboard = arrayOf(
                arrayOf('Q', 'W', 'E', 'R', 'T', 'Y', 'U', 'I', 'O', 'P'),
                arrayOf('A', 'S', 'D', 'F', 'G', 'H', 'J', 'K', 'L'),
                arrayOf('<', 'Z', 'X', 'C', 'V', 'B', 'N', 'M', '¬')
            )

            for (row in keyboard) {
                Row(
                    modifier = Modifier
                        // boxes simply exceed it, draw into neighboring row
                        //.heightIn(max = 50.dp)
                        // no effect
                        //.wrapContentHeight(unbounded = true)
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    for (key in row) {
                        // TODO server should return this as a state info, all guessed letters & their state
                        //val attempts = guesses.filterIsInstance<Guess.Attempt>()
                        //val letters = attempts.flatMap { it.letters.filter { it.letter == key } }
                        //val isGreen = letters.any { it.status == LetterStatus.Green }
                        //val isYellow = letters.any { it.status == LetterStatus.Yellow }
                        //val isGrey = letters.any { it.status == LetterStatus.Grey }
                        val isGreen = true
                        val isYellow = true
                        val isGrey = true
                        // TODO adaptive spacing
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 2.dp)
                                //.aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isGreen) ColorGreen else if (isYellow) ColorYellow else if (isGrey) ColorKeyMiss else ColorKey)
                                .weight(1f)
                                .clickable {
                                    onKey(
                                        when (key) {
                                            '¬' -> KeyPress.Enter
                                            '<' -> KeyPress.Backspace
                                            else -> KeyPress.Letter(key)
                                        }
                                    )
                                }
                            /*.drawBehind {
                                val quadrantSize = size / 2F
                                drawRect(
                                    topLeft = Offset(quadrantSize.width, quadrantSize.height),
                                    color = ColorGreen,
                                    size = quadrantSize
                                )
                            }*/,
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "$key",
                                color = Color.White,
                                //style = MaterialTheme.typography.bodyLarge,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LoadingView(state: Loading) {
    when {
        state.selfId == null -> {
            SimpleDialog(
                title = "Join session",
                canDismiss = false,
                showDismiss = false
            ) {
                var name by rememberRetained { mutableStateOf("") }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Enter a name") },
                    leadingIcon = {
                        Icon(Icons.Default.AccountCircle, contentDescription = "")
                    },
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { state.eventSink(Join(name)) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = name.isNotBlank() && name.length <= 50,
                    // match OutlinedTextField default
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Icon(
                        Icons.Default.PlayArrow, contentDescription = ""
                    )
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text("Join Game", fontSize = 14.sp)
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { state.eventSink(Cancel) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xffa34b44),
                        contentColor = Color.White
                    ),
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Icon(
                        Icons.Default.Close, contentDescription = ""
                    )
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text("Cancel")
                }
            }
        }

        state.sessionId == null -> {
            SimpleDialog(
                title = "Standby",
                canDismiss = false,
                showDismiss = false
            ) {
                Text("Establishing a real-time connection...")
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { state.eventSink(Cancel) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xffa34b44),
                        contentColor = Color.White
                    ),
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Icon(
                        Icons.Default.Close, contentDescription = ""
                    )
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text("Cancel")
                }
            }
        }

        else -> {
            SimpleDialog(
                title = "Waiting for a teammate to join :)",
                canDismiss = false,
                showDismiss = false
            ) {
                Text(buildAnnotatedString {
                    append("Share this link with someone:\n")
                    withLink(LinkAnnotation.Url(url = state.sessionId.sessionLink)) {
                        withStyle(style = SpanStyle(color = ColorRed)) {
                            append(state.sessionId.sessionLink)
                        }
                    }
                })
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { state.eventSink(CopyLink) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Icon(
                        Icons.Default.Share, contentDescription = ""
                    )
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text("Copy link")
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { state.eventSink(Cancel) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xffa34b44),
                        contentColor = Color.White
                    ),
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Icon(
                        Icons.Default.Close, contentDescription = ""
                    )
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text("Cancel")
                }
            }
        }
    }
}

val Uuid.sessionLink: String get() = "$Domain/#room/$this"
