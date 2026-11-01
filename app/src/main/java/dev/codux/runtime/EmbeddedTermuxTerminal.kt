package dev.codux.runtime

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

/** Terminal PTY do Termux dentro do prefixo privado do Codux, com linha de comandos e barra de teclas. */
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
        Box(modifier.padding(16.dp)) {
            Text(failure ?: "A instalar o ambiente Linux integrado…")
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
    val context = LocalContext.current
    val client = remember(holder) { holder.clientFor(context) }
    val generation = holder.generation
    val session = remember(holder, generation, workingDirectory.absolutePath) { holder.sessionFor(runtime, workingDirectory, client) }
    DisposableEffect(session) {
        onDispose { client.view = null }
    }
    val shape = RoundedCornerShape(14.dp)
    Column(modifier) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .clip(shape)
                .border(1.dp, Color(0xFF3C3C3C), shape)
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
        TerminalKeys(client, holder, session)
    }
}

/**
 * Linha de comandos com o teclado do Android. Escreve o comando no shell e prime Enter, por isso
 * dá para executar comandos sem depender do teclado que o próprio terminal tenta abrir.
 */
@Composable
private fun TerminalCommandBar(session: TerminalSession) {
    var command by remember(session) { mutableStateOf("") }
    val accent = Color(0xFF4EC9B0)
    val shape = RoundedCornerShape(14.dp)
    fun submit() {
        session.write(command + "\r")
        command = ""
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(shape)
            .background(Color(0xFF1E1E1E))
            .padding(start = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("\u276F", color = accent, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        BasicTextField(
            value = command,
            onValueChange = { command = it },
            singleLine = true,
            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = Color(0xFFD4D4D4)),
            cursorBrush = SolidColor(accent),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrect = false, imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { submit() }),
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 13.dp),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (command.isEmpty()) {
                        Text("Escreva um comando…", color = Color(0xFF6E7681), fontFamily = FontFamily.Monospace, fontSize = 14.sp, maxLines = 1)
                    }
                    inner()
                }
            }
        )
        TextButton(onClick = { session.write("\u0003") }, contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.height(44.dp)) {
            Text("^C", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Color(0xFFCE9178))
        }
        IconButton(onClick = { submit() }) {
            Icon(Icons.AutoMirrored.Filled.Send, "Executar comando", tint = accent)
        }
    }
}

/** Duas linhas de teclas, como no desenho: ESC ≡ ⇳ HOME ↑ END TAB PGDN F1-F4 / ⇤ CTRL ← ↓ → PGUP SHIFT F5-F8. */
@Composable
private fun TerminalKeys(client: EmbeddedTerminalClient, holder: TerminalHolder, session: TerminalSession) {
    var menu by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp)) {
            Row(Modifier.fillMaxWidth()) {
                KeyCell("ESC") { client.sendKey(KeyEvent.KEYCODE_ESCAPE) }
                KeyCell("≡") { menu = true }
                KeyCell("⇳") { client.showKeyboard() }
                KeyCell("HOME") { client.sendKey(KeyEvent.KEYCODE_MOVE_HOME) }
                KeyCell("↑") { client.sendKey(KeyEvent.KEYCODE_DPAD_UP) }
                KeyCell("END") { client.sendKey(KeyEvent.KEYCODE_MOVE_END) }
                KeyCell("TAB") { client.sendKey(KeyEvent.KEYCODE_TAB) }
                KeyCell("PGDN") { client.sendKey(KeyEvent.KEYCODE_PAGE_DOWN) }
                KeyCell("F1") { client.sendKey(KeyEvent.KEYCODE_F1) }
                KeyCell("F2") { client.sendKey(KeyEvent.KEYCODE_F2) }
                KeyCell("F3") { client.sendKey(KeyEvent.KEYCODE_F3) }
                KeyCell("F4") { client.sendKey(KeyEvent.KEYCODE_F4) }
            }
            Row(Modifier.fillMaxWidth()) {
                KeyCell("⇤") { session.write("\u001b[Z") }
                KeyCell("CTRL", active = client.ctrl.value) { client.ctrl.value = !client.ctrl.value }
                Spacer(Modifier.weight(1f))
                KeyCell("←") { client.sendKey(KeyEvent.KEYCODE_DPAD_LEFT) }
                KeyCell("↓") { client.sendKey(KeyEvent.KEYCODE_DPAD_DOWN) }
                KeyCell("→") { client.sendKey(KeyEvent.KEYCODE_DPAD_RIGHT) }
                KeyCell("PGUP") { client.sendKey(KeyEvent.KEYCODE_PAGE_UP) }
                KeyCell("SHIFT", active = client.shift.value) { client.shift.value = !client.shift.value }
                KeyCell("F5") { client.sendKey(KeyEvent.KEYCODE_F5) }
                KeyCell("F6") { client.sendKey(KeyEvent.KEYCODE_F6) }
                KeyCell("F7") { client.sendKey(KeyEvent.KEYCODE_F7) }
                KeyCell("F8") { client.sendKey(KeyEvent.KEYCODE_F8) }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Colar") }, onClick = { menu = false; client.onPasteTextFromClipboard(session) })
            DropdownMenuItem(text = { Text("Ctrl+C (interromper)") }, onClick = { menu = false; session.write("\u0003") })
            DropdownMenuItem(text = { Text("Ctrl+D (terminar entrada)") }, onClick = { menu = false; session.write("\u0004") })
            DropdownMenuItem(text = { Text("Limpar ecrã") }, onClick = { menu = false; session.write("\u000c") })
            DropdownMenuItem(text = { Text("Nova sessão") }, onClick = { menu = false; holder.restart() })
        }
    }
}

@Composable
private fun RowScope.KeyCell(label: String, active: Boolean = false, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.weight(1f).height(38.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(
            label,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = if (label.length > 3) 9.sp else 12.sp,
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false
        )
    }
}

internal class EmbeddedTerminalClient(private val context: Context) : TerminalSessionClient, TerminalViewClient {
    /** View atual; necessária para redesenhar a tela quando chega saída do processo. */
    var view: TerminalView? = null
    /** Teclas Ctrl e Shift "pegajosas" acionadas pela barra de teclas. */
    val ctrl = mutableStateOf(false)
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
        if (shift.value) modifiers = modifiers or KeyHandler.KEYMOD_SHIFT
        target.handleKeyCode(keyCode, modifiers)
        ctrl.value = false
        shift.value = false
    }

    override fun onTextChanged(changedSession: TerminalSession) {
        view?.onScreenUpdated()
    }
    override fun onTitleChanged(changedSession: TerminalSession) = Unit
    override fun onSessionFinished(finishedSession: TerminalSession) = Unit
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
    override fun readAltKey(): Boolean = false
    override fun readShiftKey(): Boolean = shift.value
    override fun readFnKey(): Boolean = false
    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean {
        // Ctrl e Shift pegajosos valem para uma única tecla.
        if (ctrl.value) ctrl.value = false
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
