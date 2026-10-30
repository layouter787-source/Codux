package dev.codux.runtime

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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

/** Displays the upstream Termux PTY terminal backed by Codux's private prefix. */
@Composable
fun EmbeddedTermuxTerminal(
    runtime: EmbeddedTermuxRuntime,
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
        Box(modifier) {
            Text(failure ?: "A instalar o ambiente Linux integrado…")
        }
        return
    }
    TerminalContent(runtime, workingDirectory, modifier)
}

@Composable
private fun TerminalContent(
    runtime: EmbeddedTermuxRuntime,
    workingDirectory: File,
    modifier: Modifier
) {
    val context = LocalContext.current
    val client = remember(context) { EmbeddedTerminalClient(context) }
    val session = remember(runtime, workingDirectory.absolutePath) {
        // argv[0] precisa ser o nome do shell com '-' na frente para iniciar um login shell.
        TerminalSession(
            runtime.shellPath(),
            workingDirectory.absolutePath,
            arrayOf("-" + File(runtime.shellPath()).name),
            runtime.environment(workingDirectory),
            10_000,
            client
        )
    }
    DisposableEffect(session) {
        onDispose {
            client.view = null
            session.finishIfRunning()
        }
    }
    Column(modifier) {
        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
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
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            ExtraKey("ESC") { client.sendKey(KeyEvent.KEYCODE_ESCAPE) }
            ExtraKey("TAB") { client.sendKey(KeyEvent.KEYCODE_TAB) }
            ExtraKey(if (client.ctrl.value) "CTRL•" else "CTRL") { client.ctrl.value = !client.ctrl.value }
            ExtraKey("←") { client.sendKey(KeyEvent.KEYCODE_DPAD_LEFT) }
            ExtraKey("↓") { client.sendKey(KeyEvent.KEYCODE_DPAD_DOWN) }
            ExtraKey("↑") { client.sendKey(KeyEvent.KEYCODE_DPAD_UP) }
            ExtraKey("→") { client.sendKey(KeyEvent.KEYCODE_DPAD_RIGHT) }
            ExtraKey("Colar") { client.onPasteTextFromClipboard(session) }
            ExtraKey("⌨") { client.showKeyboard() }
        }
    }
}

@Composable
private fun RowScope.ExtraKey(label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.weight(1f),
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)
    ) {
        Text(label, fontSize = 12.sp, maxLines = 1)
    }
}

private class EmbeddedTerminalClient(private val context: Context) : TerminalSessionClient, TerminalViewClient {
    /** View atual; necessária para redesenhar a tela quando chega saída do processo. */
    var view: TerminalView? = null
    /** Tecla Ctrl "pegajosa" acionada pela barra de teclas extras. */
    val ctrl = mutableStateOf(false)

    fun showKeyboard() {
        val target = view ?: return
        target.requestFocus()
        val input = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        input?.showSoftInput(target, 0)
    }

    fun sendKey(keyCode: Int) {
        val target = view ?: return
        if (target.mEmulator == null) return
        val modifiers = if (ctrl.value) KeyHandler.KEYMOD_CTRL else 0
        target.handleKeyCode(keyCode, modifiers)
        ctrl.value = false
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
    override fun readShiftKey(): Boolean = false
    override fun readFnKey(): Boolean = false
    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean {
        // O Ctrl pegajoso vale para uma única tecla.
        if (ctrl.value) ctrl.value = false
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
