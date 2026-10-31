package dev.codux.runtime

import android.content.Context
import android.os.Build
import android.system.Os
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Owns a Termux-prefix runtime inside Codux's private app data directory. */
class EmbeddedTermuxRuntime(context: Context) {
    private val app = context.applicationContext
    val filesDir: File get() = app.filesDir
    val prefix: File get() = File(filesDir, "usr")
    val home: File get() = File(filesDir, "home")
    val bash: File get() = File(prefix, "bin/bash")

    /**
     * Apps que miram o API 29+ não podem executar arquivos de filesDir diretamente (Android 10+).
     * Nesse caso tudo é executado via /system/bin/linker64 (termux-exec system_linker_exec).
     * Com targetSdk 28 (versão normal do Codux) os binários são executados direto.
     */
    val usesSystemLinker: Boolean get() = app.applicationInfo.targetSdkVersion >= 29

    private val systemLinker: String
        get() = if (android.os.Process.is64Bit()) "/system/bin/linker64" else "/system/bin/linker"

    /** Linha de comando (argv completo, argv[0] = executável) para rodar o bash do runtime. */
    fun bashCommand(vararg args: String): List<String> =
        (if (usesSystemLinker) listOf(systemLinker, bash.absolutePath) else listOf(bash.absolutePath)) + args

    fun isInstalled(): Boolean {
        val marker = File(prefix, "etc/termux-bootstrap.version")
        return bash.isFile && File(prefix, "bin/codex").canExecute() && marker.isFile && marker.readText() == BOOTSTRAP_MARKER
    }

    /** Seguro para chamadas concorrentes (terminal e ligação ao Codex): só uma instalação corre de cada vez. */
    suspend fun installIfNeeded() = withContext(Dispatchers.IO) {
        installLock.withLock {
            if (isInstalled()) {
                // Garante pastas que o runtime espera mesmo em instalações anteriores.
                runCatching { prepareRuntimeDirectories(prefix) }
                runCatching { home.mkdirs() }
                return@withContext
            }
            val asset = bootstrapAsset() ?: error("O bootstrap Termux ainda não foi incluído para esta arquitetura Android.")
            val staging = File(filesDir, "usr-staging")
            deleteOwnedTree(staging)
            check(staging.mkdirs() || staging.isDirectory) { "Não foi possível preparar o prefixo Termux." }
            try {
                val symlinks = mutableListOf<Pair<String, File>>()
                app.assets.open(asset).use { raw ->
                    ZipInputStream(raw.buffered()).use { zip ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val entry = zip.nextEntry ?: break
                            if (entry.name == "SYMLINKS.txt") {
                                val reader = java.io.BufferedReader(java.io.InputStreamReader(zip, Charsets.UTF_8))
                                while (true) {
                                    val line = reader.readLine() ?: break
                                    if (line.isBlank()) continue
                                    val separator = line.indexOf('←')
                                    require(separator > 0 && separator < line.lastIndex) { "SYMLINKS.txt contém uma entrada inválida." }
                                    val target = line.substring(0, separator)
                                    val relativeLink = line.substring(separator + 1).removePrefix("./")
                                    symlinks += target to safeChild(staging, relativeLink)
                                }
                                continue
                            }
                            val relativeEntry = entry.name.removePrefix("./").removeSuffix("/")
                            if (relativeEntry.isEmpty() || relativeEntry == ".") continue
                            val target = safeChild(staging, relativeEntry)
                            if (entry.isDirectory) {
                                check(target.mkdirs() || target.isDirectory) { "Não foi possível criar ${entry.name}." }
                            } else {
                                check(target.parentFile?.mkdirs() == true || target.parentFile?.isDirectory == true) { "Não foi possível criar a pasta de ${entry.name}." }
                                FileOutputStream(target).use { out ->
                                    while (true) {
                                        val count = zip.read(buffer)
                                        if (count < 0) break
                                        out.write(buffer, 0, count)
                                    }
                                }
                                if (entry.name.startsWith("bin/") || entry.name.startsWith("libexec/") ||
                                    entry.name.startsWith("lib/apt/apt-helper") || entry.name.startsWith("lib/apt/methods/")) {
                                    Os.chmod(target.absolutePath, 448) // 0700
                                }
                            }
                        }
                    }
                }
                check(symlinks.isNotEmpty()) { "O bootstrap não contém SYMLINKS.txt." }
                symlinks.forEach { (target, link) ->
                    check(link.parentFile?.isDirectory == true) { "A pasta do link Termux não existe." }
                    Os.symlink(target, link.absolutePath)
                }
                prepareRuntimeDirectories(staging)
                val bootstrapInfo = File(staging, "etc/termux-bootstrap.version")
                check(bootstrapInfo.parentFile?.mkdirs() == true || bootstrapInfo.parentFile?.isDirectory == true)
                bootstrapInfo.writeText(BOOTSTRAP_MARKER)
                // Somente o prefixo é substituído: home (credenciais do Codex, ~/.codex) é preservada.
                deleteOwnedTree(prefix)
                check(staging.renameTo(prefix)) { "Não foi possível ativar o prefixo Termux." }
                check(home.mkdirs() || home.isDirectory) { "Não foi possível criar o diretório pessoal do terminal." }
            } catch (error: Throwable) {
                deleteOwnedTree(staging)
                throw error
            }
        }
    }

    fun environment(workdir: File = home): Array<String> {
        require(workdir.isAbsolute && (workdir == filesDir || workdir.path.startsWith(filesDir.path + "/") || workdir.exists()))
        val env = linkedMapOf(
            "HOME" to home.absolutePath,
            "PREFIX" to prefix.absolutePath,
            "TMPDIR" to File(prefix, "tmp").absolutePath,
            "PATH" to File(prefix, "bin").absolutePath + ":" + File(prefix, "bin/applets").absolutePath + ":/system/bin",
            "LANG" to "en_US.UTF-8",
            "LC_ALL" to "en_US.UTF-8",
            "TERM" to "xterm-256color",
            "COLORTERM" to "truecolor",
            "SHELL" to bash.absolutePath,
            "TERMUX__HOME" to home.absolutePath,
            "TERMUX__PREFIX" to prefix.absolutePath,
            "TERMUX__ROOTFS" to filesDir.absolutePath,
            // Com targetSdk 28 os binários rodam direto; reexecutar via linker faria /proc/self/exe apontar
            // para o linker e quebraria o Codex. Com targetSdk 29+ o linker é obrigatório (ver usesSystemLinker).
            "TERMUX_EXEC__SYSTEM_LINKER_EXEC__MODE" to (if (usesSystemLinker) "force" else "disable"),
            "CODEX_HOME" to File(home, ".codex").absolutePath
        )
        val preload = mutableListOf<String>()
        selfExeShim()?.let { preload += it.absolutePath }
        preloadLibrary()?.let { preload += it.absolutePath }
        if (preload.isNotEmpty()) env["LD_PRELOAD"] = preload.joinToString(":")
        val certificates = File(prefix, "etc/tls/cert.pem")
        if (certificates.isFile) env["SSL_CERT_FILE"] = certificates.absolutePath
        val certificateDirectory = File(prefix, "etc/tls/certs")
        if (certificateDirectory.isDirectory) env["SSL_CERT_DIR"] = certificateDirectory.absolutePath
        env["ANDROID_ROOT"] = System.getenv("ANDROID_ROOT") ?: "/system"
        env["ANDROID_DATA"] = System.getenv("ANDROID_DATA") ?: "/data"
        // O terminal embutido (TerminalSession) não herda o ambiente do app, então repassa o que o Android define.
        ANDROID_PASSTHROUGH.forEach { name -> System.getenv(name)?.let { env[name] = it } }
        return env.map { (name, value) -> "$name=$value" }.toTypedArray()
    }

    fun shellPath(): String = bash.absolutePath

    /** Biblioteca de LD_PRELOAD do próprio app que corrige /proc/self/exe (só na variante Play). */
    private fun selfExeShim(): File? =
        File(app.applicationInfo.nativeLibraryDir, "libcodux-selfexe.so").takeIf { usesSystemLinker && it.isFile }

    /**
     * Biblioteca LD_PRELOAD do termux-exec. Na execução via linker usa a variante "linker";
     * na execução direta, o nome novo (2.x) tem prioridade sobre o link legado.
     */
    private fun preloadLibrary(): File? {
        val names = if (usesSystemLinker) {
            listOf(
                "lib/libtermux-exec-linker-ld-preload.so",
                "lib/libtermux-exec-ld-preload.so",
                "lib/libtermux-exec-direct-ld-preload.so",
                "lib/libtermux-exec.so"
            )
        } else {
            listOf("lib/libtermux-exec-ld-preload.so", "lib/libtermux-exec.so")
        }
        return names.map { File(prefix, it) }.firstOrNull { it.exists() }
    }

    private fun prepareRuntimeDirectories(root: File) {
        val tmp = File(root, "tmp")
        check(tmp.mkdirs() || tmp.isDirectory) { "Não foi possível criar a pasta temporária do runtime." }
        Os.chmod(tmp.absolutePath, 448) // 0700
        runCatching {
            val resolv = File(root, "etc/resolv.conf")
            if (!resolv.exists()) {
                resolv.parentFile?.mkdirs()
                resolv.writeText("nameserver 8.8.8.8\nnameserver 8.8.4.4\n")
            }
        }
    }

    private fun bootstrapAsset(): String? {
        val arch = Build.SUPPORTED_ABIS.firstOrNull() ?: return null
        val termuxArch = when (arch) {
            "arm64-v8a" -> "aarch64"
            "armeabi-v7a", "armeabi" -> "arm"
            "x86_64" -> "x86_64"
            "x86" -> "i686"
            else -> return null
        }
        return "bootstrap-$termuxArch.zip".takeIf { name ->
            runCatching { app.assets.open(name).use { } }.isSuccess
        }
    }

    private fun safeChild(root: File, relativePath: String): File {
        require(relativePath.isNotBlank() && !relativePath.startsWith('/')) { "Caminho inválido no bootstrap." }
        val base = root.canonicalFile
        val child = File(base, relativePath).canonicalFile
        require(child.path.startsWith(base.path + File.separator)) { "O bootstrap contém um caminho fora do prefixo." }
        return child
    }

    private fun deleteOwnedTree(target: File) {
        if (!target.exists() && !Files.isSymbolicLink(target.toPath())) return
        require(target.absoluteFile.parentFile?.canonicalFile == filesDir.canonicalFile) { "Recusa de apagar diretório fora dos dados do Codux." }
        fun remove(node: File) {
            if (!Files.isSymbolicLink(node.toPath()) && node.isDirectory) {
                node.listFiles()?.forEach(::remove)
            }
            Files.deleteIfExists(node.toPath())
        }
        remove(target)
    }

    private companion object {
        const val BOOTSTRAP_MARKER = "Codux embedded Termux bootstrap v2\n"
        val installLock = Mutex()
        val ANDROID_PASSTHROUGH = listOf(
            "ANDROID_ART_ROOT",
            "ANDROID_I18N_ROOT",
            "ANDROID_RUNTIME_ROOT",
            "ANDROID_TZDATA_ROOT",
            "ANDROID_STORAGE",
            "BOOTCLASSPATH",
            "DEX2OATBOOTCLASSPATH",
            "EXTERNAL_STORAGE"
        )
    }
}
