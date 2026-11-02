package dev.codux.runtime

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.termux.terminal.KeyHandler
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import dev.codux.Codux
import dev.codux.CoduxShape
import dev.codux.CoduxSpace
import dev.codux.StatusDot
import java.io.File
import kotlinx.coroutines.CancellationException

/**
 * Guarda a sessão do shell fora da composição: trocar de ecrã não mata o que está a correr
 * (por exemplo o `codex login`). A sessão só termina com [finish] ou [restart].
 */
internal class TerminalHolder {
    var generation by mutableIntStateOf(0)
    private var client: EmbeddedTerminalClient? = null
    private var session: TerminalSession? = null

    fun clientFor(context: Context): EmbeddedTerminalClient =
        client ?: EmbeddedTerminalClient(context.applicationContext).also { client = it }

    fun sessionFor(runtime: EmbeddedTermuxRuntime, workingDirectory: File, client: EmbeddedTerminalClient): TerminalSession {
        val existing = session
        if (existing != null && existing.isRunning) return existing
        // -l inicia um login shell. Na execução via linker do sistema o bash segue como argumento.
        val command = runtime.bashCommand("-l")
        val created = TerminalSession(
            command.first(),
            workingDirectory.absolutePath,
            command.toTypedArray(),
            runtime.environment(workingDirectory),
            10_000,
            client
        )
        session = created
        return created
    }

    fun restart() {
        session?.finishIfRunning()
        session = null
        generation++
    }

    fun finish() {
        session?.finishIfRunning()
        session = null
    }
}

/** Terminal PTY do Termux dentro do prefixo privado do Codux, com ações, linha de comandos e teclado auxiliar. */
@Composable
internal fun EmbeddedTermuxTerminal(
    runtime: EmbeddedTermuxRuntime,
    holder: TerminalHolder,
    workingDirectory: File = runtime.home,
    modifier: Modifier = Modifier
) {
    var installed by remember(runtime) { mutableStateOf(runtime.isInstalled()) }
    var failure by remember(runtime) { mutableStateOf<String?>(null) }
    // Instala o runtime sozinho ao abrir o terminal; não depende de ligar ao Codex primeiro.
    LaunchedEffect(runtime, installed) {
        if (!installed) {
            try {
                runtime.installIfNeeded()
                installed = runtime.isInstalled()
                if (!installed) {
                    failure = "O bootstrap embutido não contém o Codex CLI. Reconstrói o bootstrap (workflow Build embedded Termux runtime) e gera o APK novamente."
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                failure = error.message ?: "Falha ao instalar o ambiente Linux integrado."
            }
        }
    }
    if (!installed) {
        Column(modifier.padding(CoduxSpace.xl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            if (failure == null) CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp, color = Codux.colors.accent)
            Text(
                failure ?: "A instalar o ambiente Linux integrado…",
                Modifier.padding(top = CoduxSpace.md),
                style = MaterialTheme.typography.bodyMedium,
                color = if (failure == null) Codux.colors.textSecondary else Codux.colors.danger
            )
        }
        return
    }
    TerminalContent(runtime, holder, workingDirectory, modifier)
}

@Composable
private fun TerminalContent(
    runtime: EmbeddedTermuxRuntime,
    holder: TerminalHolder,
    workingDirectory: File,
    modifier: Modifier
) {
    val colors = Codux.colors
    val context = LocalContext.current
    val client = remember(holder) { holder.clientFor(context) }
    val generation = holder.generation
    val session = remember(holder, generation, workingDirectory.absolutePath) { holder.sessionFor(runtime, workingDirectory, client) }
    LaunchedEffect(session) {
        client.activeSession = session
        client.sessionEnded.value = !session.isRunning
    }
    DisposableEffect(session) {
        onDispose { client.view = null }
    }
    val shape = CoduxShape.medium
    Column(modifier) {
        TerminalActionBar(client, holder, session)
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = CoduxSpace.md)
                .clip(shape)
                .border(1.dp, colors.border, shape)
                .background(Color.Black)
                .padding(10.dp)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { viewContext ->
                    TerminalView(viewContext, null).apply {
                        setTerminalViewClient(client)
                        // O renderizador do Termux usa pixels, não dp.
                        setTextSize((14f * viewContext.resources.displayMetrics.density).toInt())
                        client.view = this
                        attachSession(session)
                    }
                },
                update = { view ->
                    client.view = view
                    if (view.mTermSession !== session) view.attachSession(session)
                }
            )
        }
        TerminalCommandBar(session)
        TerminalKeys(client, session)
        TerminalStatusBar(
            location = if (workingDirectory.absolutePath == runtime.home.absolutePath) "~" else workingDirectory.name,
            running = !client.sessionEnded.value
        )
    }
}

/** Sessão atual e ações reais do terminal: limpar, colar, interromper, abrir o teclado e nova sessão. */
@Composable
private fun TerminalActionBar(client: EmbeddedTerminalClient, holder: TerminalHolder, session: TerminalSession) {
    val colors = Codux.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = CoduxSpace.md, vertical = CoduxSpace.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier
                .height(36.dp)
                .clip(CoduxShape.medium)
                .background(colors.surface)
                .border(1.dp, colors.border, CoduxShape.medium)
                .padding(horizontal = CoduxSpace.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Terminal, null, tint = colors.success, modifier = Modifier.size(16.dp))
            Text("bash", Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.labelLarge, color = colors.text)
            StatusDot(if (client.sessionEnded.value) colors.textSecondary else colors.success)
        }
        Spacer(Modifier.weight(1f))
        ActionIcon(Icons.Default.CleaningServices, "Limpar ecrã") { session.write("\u000c") }
        ActionIcon(Icons.Default.ContentPaste, "Colar") { client.onPasteTextFromClipboard(session) }
        ActionIcon(Icons.Default.StopCircle, "Interromper (Ctrl+C)") { session.write("\u0003") }
        ActionIcon(Icons.Default.Keyboard, "Mostrar teclado do terminal") { client.showKeyboard() }
        ActionIcon(Icons.Default.Add, "Nova sessão") { holder.restart() }
    }
}

@Composable
private fun ActionIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(icon, description, tint = Codux.colors.textSecondary, modifier = Modifier.size(22.dp))
    }
}

/**
 * Linha de comandos com o teclado do Android. Escreve o comando no shell e prime Enter, por isso
 * dá para executar comandos sem depender do teclado que o próprio terminal tenta abrir.
 */
@Composable
private fun TerminalCommandBar(session: TerminalSession) {
    val colors = Codux.colors
    var command by remember(session) { mutableStateOf("") }
    val shape = CoduxShape.medium
    fun submit() {
        session.write(command + "\r")
        command = ""
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = CoduxSpace.md, vertical = CoduxSpace.sm)
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.border, shape)
            .padding(start = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("\u276F", color = colors.success, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        BasicTextField(
            value = command,
            onValueChange = { command = it },
            singleLine = true,
            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = colors.text),
            cursorBrush = SolidColor(colors.success),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrect = false, imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { submit() }),
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 12.dp),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (command.isEmpty()) {
                        Text("Escreva um comando…", color = colors.textSecondary, fontFamily = FontFamily.Monospace, fontSize = 14.sp, maxLines = 1)
                    }
                    inner()
                }
            }
        )
        IconButton(onClick = { submit() }) {
            Icon(Icons.AutoMirrored.Filled.Send, "Executar comando", tint = colors.accent)
        }
    }
}

private val SymbolKeys = listOf("/", "-", "|", "~", "$", "'", "\"", "(", ")", "{", "}", "[", "]", ";", "=", "\\", "_", "&", "*", ":")

/**
 * Teclado auxiliar compacto: linha principal (ESC, TAB, CTRL, ALT e setas) e uma linha deslizante com
 * navegação, símbolos de programação e FN, que mostra F1–F8.
 */
@Composable
private fun TerminalKeys(client: EmbeddedTerminalClient, session: TerminalSession) {
    var functionKeys by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = CoduxSpace.sm, vertical = 2.dp)) {
        Row(Modifier.fillMaxWidth()) {
            KeyButton("ESC", Modifier.weight(1f)) { client.sendKey(KeyEvent.KEYCODE_ESCAPE) }
            KeyButton("TAB", Modifier.weight(1f)) { client.sendKey(KeyEvent.KEYCODE_TAB) }
            KeyButton("CTRL", Modifier.weight(1f), active = client.ctrl.value) { client.ctrl.value = !client.ctrl.value }
            KeyButton("ALT", Modifier.weight(1f), active = client.alt.value) { client.alt.value = !client.alt.value }
            KeyButton("←", Modifier.weight(1f)) { client.sendKey(KeyEvent.KEYCODE_DPAD_LEFT) }
            KeyButton("↓", Modifier.weight(1f)) { client.sendKey(KeyEvent.KEYCODE_DPAD_DOWN) }
            KeyButton("↑", Modifier.weight(1f)) { client.sendKey(KeyEvent.KEYCODE_DPAD_UP) }
            KeyButton("→", Modifier.weight(1f)) { client.sendKey(KeyEvent.KEYCODE_DPAD_RIGHT) }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            KeyButton("HOME", Modifier.width(54.dp)) { client.sendKey(KeyEvent.KEYCODE_MOVE_HOME) }
            KeyButton("END", Modifier.width(48.dp)) { client.sendKey(KeyEvent.KEYCODE_MOVE_END) }
            KeyButton("PGUP", Modifier.width(54.dp)) { client.sendKey(KeyEvent.KEYCODE_PAGE_UP) }
            KeyButton("PGDN", Modifier.width(54.dp)) { client.sendKey(KeyEvent.KEYCODE_PAGE_DOWN) }
            SymbolKeys.forEach { symbol -> KeyButton(symbol, Modifier.width(40.dp)) { session.write(symbol) } }
            KeyButton("FN", Modifier.width(48.dp), active = functionKeys) { functionKeys = !functionKeys }
        }
        if (functionKeys) {
            Row(Modifier.fillMaxWidth()) {
                listOf(
                    "F1" to KeyEvent.KEYCODE_F1, "F2" to KeyEvent.KEYCODE_F2, "F3" to KeyEvent.KEYCODE_F3, "F4" to KeyEvent.KEYCODE_F4,
                    "F5" to KeyEvent.KEYCODE_F5, "F6" to KeyEvent.KEYCODE_F6, "F7" to KeyEvent.KEYCODE_F7, "F8" to KeyEvent.KEYCODE_F8
                ).forEach { (label, code) -> KeyButton(label, Modifier.weight(1f)) { client.sendKey(code) } }
            }
        }
    }
}

@Composable
private fun KeyButton(label: String, modifier: Modifier, active: Boolean = false, onClick: () -> Unit) {
    val colors = Codux.colors
    Box(
        modifier
            .padding(2.dp)
            .height(36.dp)
            .clip(CoduxShape.small)
            .background(if (active) colors.accent.copy(alpha = .22f) else colors.surfaceElevated)
            .border(1.dp, if (active) colors.accent else colors.border, CoduxShape.small)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = if (label.length > 3) 10.sp else 13.sp,
            color = if (active) colors.accent else colors.text,
            maxLines = 1,
            softWrap = false
        )
    }
}

/** Pasta atual, shell e codificação, como na barra de estado de um IDE. */
@Composable
private fun TerminalStatusBar(location: String, running: Boolean) {
    val colors = Codux.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.backgroundAlt)
            .padding(horizontal = CoduxSpace.lg, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CoduxSpace.lg)
    ) {
        Text(location, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = colors.textSecondary, maxLines = 1)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("bash", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = colors.textSecondary)
            Spacer(Modifier.width(6.dp))
            StatusDot(if (running) colors.success else colors.textSecondary, 6.dp)
        }
        Text("UTF-8", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = colors.textSecondary)
    }
}

internal class EmbeddedTerminalClient(private val context: Context) : TerminalSessionClient, TerminalViewClient {
    /** View atual; necessária para redesenhar a tela quando chega saída do processo. */
    var view: TerminalView? = null
    /** Sessão que a interface mostra neste momento (ignora o fim de sessões antigas). */
    var activeSession: TerminalSession? = null
    val sessionEnded = mutableStateOf(false)
    /** Teclas Ctrl, Alt e Shift "pegajosas" acionadas pela barra de teclas. */
    val ctrl = mutableStateOf(false)
    val alt = mutableStateOf(false)
    val shift = mutableStateOf(false)

    fun showKeyboard() {
        val target = view ?: return
        target.requestFocus()
        val input = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        input?.showSoftInput(target, 0)
    }

    fun sendKey(keyCode: Int) {
        val target = view ?: return
        if (target.mEmulator == null) return
        var modifiers = 0
        if (ctrl.value) modifiers = modifiers or KeyHandler.KEYMOD_CTRL
        if (alt.value) modifiers = modifiers or KeyHandler.KEYMOD_ALT
        if (shift.value) modifiers = modifiers or KeyHandler.KEYMOD_SHIFT
        target.handleKeyCode(keyCode, modifiers)
        ctrl.value = false
        alt.value = false
        shift.value = false
    }

    override fun onTextChanged(changedSession: TerminalSession) {
        view?.onScreenUpdated()
    }
    override fun onTitleChanged(changedSession: TerminalSession) = Unit
    override fun onSessionFinished(finishedSession: TerminalSession) {
        if (finishedSession === activeSession) sessionEnded.value = true
    }
    override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("Codux terminal", text))
    }
    override fun onPasteTextFromClipboard(session: TerminalSession?) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val clip = clipboard.primaryClip ?: return
        if (clip.itemCount == 0) return
        val text = clip.getItemAt(0).coerceToText(context)?.toString()
        if (!text.isNullOrEmpty()) session?.emulator?.paste(text)
    }
    override fun onBell(session: TerminalSession) = Unit
    override fun onColorsChanged(session: TerminalSession) {
        view?.onScreenUpdated()
    }
    override fun onTerminalCursorStateChange(state: Boolean) = Unit
    override fun setTerminalShellPid(session: TerminalSession, pid: Int) = Unit
    override fun getTerminalCursorStyle(): Int? = null

    override fun onScale(scale: Float): Float = scale
    override fun onSingleTapUp(e: MotionEvent) {
        showKeyboard()
    }
    override fun shouldBackButtonBeMappedToEscape(): Boolean = true
    override fun shouldEnforceCharBasedInput(): Boolean = true
    override fun shouldUseCtrlSpaceWorkaround(): Boolean = false
    override fun isTerminalViewSelected(): Boolean = true
    override fun copyModeChanged(copyMode: Boolean) = Unit
    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean = false
    override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean = false
    override fun onLongPress(event: MotionEvent): Boolean = false
    override fun readControlKey(): Boolean = ctrl.value
    override fun readAltKey(): Boolean = alt.value
    override fun readShiftKey(): Boolean = shift.value
    override fun readFnKey(): Boolean = false
    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean {
        // Ctrl, Alt e Shift pegajosos valem para uma única tecla.
        if (ctrl.value) ctrl.value = false
        if (alt.value) alt.value = false
        if (shift.value) shift.value = false
        return false
    }
    override fun onEmulatorSet() = Unit

    override fun logError(tag: String, message: String) { android.util.Log.e("CoduxTerminal/$tag", message) }
    override fun logWarn(tag: String, message: String) { android.util.Log.w("CoduxTerminal/$tag", message) }
    override fun logInfo(tag: String, message: String) { android.util.Log.i("CoduxTerminal/$tag", message) }
    override fun logDebug(tag: String, message: String) { android.util.Log.d("CoduxTerminal/$tag", message) }
    override fun logVerbose(tag: String, message: String) { android.util.Log.v("CoduxTerminal/$tag", message) }
    override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) { android.util.Log.e("CoduxTerminal/$tag", message, e) }
    override fun logStackTrace(tag: String, e: Exception) { android.util.Log.e("CoduxTerminal/$tag", "Terminal error", e) }
}
