package dev.codux.runtime

import android.content.Context
import android.os.Build
import android.system.Os
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Owns a Termux-prefix runtime inside Codux's private app data directory. */
class EmbeddedTermuxRuntime(context: Context) {
    private val app = context.applicationContext
    val filesDir: File get() = app.filesDir
    val prefix: File get() = File(filesDir, "usr")
    val home: File get() = File(filesDir, "home")
    val bash: File get() = File(prefix, "bin/bash")

    fun isInstalled(): Boolean = bash.isFile && File(prefix, "etc/termux-bootstrap.version").isFile

    suspend fun installIfNeeded() = withContext(Dispatchers.IO) {
        if (isInstalled()) return@withContext
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
            val bootstrapInfo = File(staging, "etc/termux-bootstrap.version")
            check(bootstrapInfo.parentFile?.mkdirs() == true || bootstrapInfo.parentFile?.isDirectory == true)
            bootstrapInfo.writeText("Codux embedded Termux bootstrap v1\n")
            deleteOwnedTree(prefix)
            check(staging.renameTo(prefix)) { "Não foi possível ativar o prefixo Termux." }
            check(home.mkdirs() || home.isDirectory) { "Não foi possível criar o diretório pessoal do terminal." }
        } catch (error: Throwable) {
            deleteOwnedTree(staging)
            throw error
        }
    }

    fun environment(workdir: File = home): Array<String> {
        require(workdir.isAbsolute && (workdir == filesDir || workdir.path.startsWith(filesDir.path + "/") || workdir.exists()))
        return arrayOf(
            "HOME=${home.absolutePath}",
            "PREFIX=${prefix.absolutePath}",
            "TERMUX__PREFIX=${prefix.absolutePath}",
            "TMPDIR=${File(prefix, "tmp").absolutePath}",
            "PATH=${File(prefix, "bin").absolutePath}:${File(prefix, "bin/applets").absolutePath}:/system/bin",
            "LD_PRELOAD=${File(prefix, "lib/libtermux-exec.so").absolutePath}",
            "LANG=en_US.UTF-8",
            "LC_ALL=en_US.UTF-8",
            "TERM=xterm-256color",
            "SHELL=${bash.absolutePath}",
            "TERMUX__HOME=${home.absolutePath}",
            "PREFIX=${prefix.absolutePath}",
            "ANDROID_DATA=/data",
            "ANDROID_ROOT=/system"
        )
    }

    fun shellPath(): String = bash.absolutePath

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
}
