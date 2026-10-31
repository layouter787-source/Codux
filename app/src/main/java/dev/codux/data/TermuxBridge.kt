package dev.codux.data

import android.content.Context
import dev.codux.runtime.EmbeddedTermuxRuntime
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicReference

/** Runs terminal commands and the Codex App Server inside Codux's private Termux runtime. */
class TermuxBridge(context: Context) {
    private val runtime = EmbeddedTermuxRuntime(context.applicationContext)
    private val appServerProcess = AtomicReference<Process?>(null)

    data class ServerConnection(val port: Int, val endpoint: String, val token: String)

    fun isInstalled(): Boolean = runtime.isInstalled()
    fun isCodexInstalled(): Boolean = File(runtime.prefix, "bin/codex").canExecute()

    fun createConnection(token: String): ServerConnection {
        val port = ServerSocket(0).use { it.localPort }
        return ServerConnection(port, "ws://127.0.0.1:$port", token)
    }

    suspend fun installRuntimeIfNeeded() = runtime.installIfNeeded()

    fun launchAppServer(connection: ServerConnection, workspacePath: String) {
        require(connection.port in 1024..65535)
        require(isInstalled()) { "O ambiente Termux integrado ainda não está instalado." }
        require(isCodexInstalled()) { "O Codex CLI ainda não está instalado no ambiente integrado." }
        stopAppServer()
        val workdir = File(workspacePath).takeIf { it.isAbsolute && it.isDirectory } ?: runtime.home
        val tokenFile = File(runtime.home, ".codux/app-server-token")
        check(tokenFile.parentFile?.mkdirs() == true || tokenFile.parentFile?.isDirectory == true)
        tokenFile.writeText(connection.token)
        check(tokenFile.setReadable(false, false) && tokenFile.setWritable(false, false))
        check(tokenFile.setReadable(true, true) && tokenFile.setWritable(true, true))
        val script = "exec codex app-server --listen '${connection.endpoint}' --ws-auth capability-token --ws-token-file '${tokenFile.absolutePath}'"
        // bashCommand() já prefixa o linker do sistema quando o app mira o API 29+ (variante Play).
        val process = ProcessBuilder(runtime.bashCommand("-lc", script))
            .directory(workdir)
            .redirectErrorStream(true)
            .apply { environment().putAll(runtime.environment(workdir).associate { it.substringBefore('=') to it.substringAfter('=') }) }
            .start()
        check(appServerProcess.compareAndSet(null, process)) { "O servidor Codex já está em execução." }
        // O registo nunca contém o token (ele só existe no ficheiro acima).
        AppServerLog.clear()
        AppServerLog.append("$ codex app-server --listen ${connection.endpoint} --ws-auth capability-token")
        Thread({
            try {
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        android.util.Log.i("CoduxAppServer", line)
                        AppServerLog.append(line)
                    }
                }
            } catch (_: java.io.IOException) {
                // Fluxo fechado ao parar o servidor.
            }
            val code = runCatching { process.waitFor() }.getOrDefault(-1)
            AppServerLog.append("[o processo terminou com o código $code]")
            File(runtime.home, ".codux/app-server-token").delete()
            appServerProcess.compareAndSet(process, null)
        }, "CoduxAppServer-output").apply { isDaemon = true; start() }
    }

    fun stopAppServer() {
        appServerProcess.getAndSet(null)?.let { process ->
            process.destroy()
            if (process.isAlive) process.destroyForcibly()
        }
        File(runtime.home, ".codux/app-server-token").delete()
    }

    fun shellPath(): String = runtime.shellPath()
    fun shellEnvironment(workspacePath: String): Array<String> {
        val dir = File(workspacePath).takeIf { it.isAbsolute && it.isDirectory } ?: runtime.home
        return runtime.environment(dir)
    }
}
