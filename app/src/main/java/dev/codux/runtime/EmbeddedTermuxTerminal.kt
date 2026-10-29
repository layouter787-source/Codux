package dev.codux.runtime

import android.content.Context
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import java.io.File

/** Displays the upstream Termux PTY terminal backed by Codux's private prefix. */
@Composable
fun EmbeddedTermuxTerminal(
    runtime: EmbeddedTermuxRuntime,
    workingDirectory: File = runtime.home,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    if (!runtime.isInstalled()) {
        Box(modifier) {
            Text("O ambiente Linux integrado ainda não foi instalado nesta versão do Codux.")
        }
        return
    }
    val client = remember(context) { EmbeddedTerminalClient(context) }
    val session = remember(runtime, workingDirectory.absolutePath) {
        TerminalSession(runtime.shellPath(), workingDirectory.absolutePath, arrayOf("-l"), runtime.environment(workingDirectory), 10_000, client)
    }
    DisposableEffect(session) {
        onDispose { session.finishIfRunning() }
    }
    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            TerminalView(viewContext).apply {
                setTerminalViewClient(client)
                setTextSize(14)
                attachSession(session)
            }
        },
        update = { view ->
            if (view.mTermSession !== session) view.attachSession(session)
        }
    )
}

private class EmbeddedTerminalClient(private val context: Context) : TerminalSessionClient, TerminalViewClient {
    override fun onTextChanged(changedSession: TerminalSession) = Unit
    override fun onTitleChanged(changedSession: TerminalSession) = Unit
    override fun onSessionFinished(finishedSession: TerminalSession) = Unit
    override fun onCopyTextToClipboard(session: TerminalSession, text: String) = Unit
    override fun onPasteTextFromClipboard(session: TerminalSession?) = Unit
    override fun onBell(session: TerminalSession) = Unit
    override fun onColorsChanged(session: TerminalSession) = Unit
    override fun onTerminalCursorStateChange(state: Boolean) = Unit
    override fun setTerminalShellPid(session: TerminalSession, pid: Int) = Unit
    override fun getTerminalCursorStyle(): Int? = null

    override fun onScale(scale: Float): Float = scale
    override fun onSingleTapUp(e: MotionEvent) = Unit
    override fun shouldBackButtonBeMappedToEscape(): Boolean = true
    override fun shouldEnforceCharBasedInput(): Boolean = true
    override fun shouldUseCtrlSpaceWorkaround(): Boolean = false
    override fun isTerminalViewSelected(): Boolean = true
    override fun copyModeChanged(copyMode: Boolean) = Unit
    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean = false
    override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean = false
    override fun onLongPress(event: MotionEvent): Boolean = false
    override fun readControlKey(): Boolean = false
    override fun readAltKey(): Boolean = false
    override fun readShiftKey(): Boolean = false
    override fun readFnKey(): Boolean = false
    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean = false
    override fun onEmulatorSet() = Unit

    override fun logError(tag: String, message: String) = android.util.Log.e("CoduxTerminal/$tag", message)
    override fun logWarn(tag: String, message: String) = android.util.Log.w("CoduxTerminal/$tag", message)
    override fun logInfo(tag: String, message: String) = android.util.Log.i("CoduxTerminal/$tag", message)
    override fun logDebug(tag: String, message: String) = android.util.Log.d("CoduxTerminal/$tag", message)
    override fun logVerbose(tag: String, message: String) = android.util.Log.v("CoduxTerminal/$tag", message)
    override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) = android.util.Log.e("CoduxTerminal/$tag", message, e)
    override fun logStackTrace(tag: String, e: Exception) = android.util.Log.e("CoduxTerminal/$tag", "Terminal error", e)
}
