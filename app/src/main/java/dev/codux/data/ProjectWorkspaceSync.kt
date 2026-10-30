package dev.codux.data

import android.content.ContentResolver
import android.database.Cursor
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import java.io.File
import java.io.InputStream
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Mirrors a Storage Access Framework folder into Codux's private POSIX runtime. */
class ProjectWorkspaceSync(private val context: Context) {
    private val resolver: ContentResolver = context.contentResolver

    fun localPath(treeUri: Uri): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(treeUri.toString().toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(File(context.filesDir, "home/projects"), digest).absolutePath
    }

    suspend fun fromSaf(treeUri: Uri, path: String) = withContext(Dispatchers.IO) {
        val root = File(path)
        if (!root.exists() && !root.mkdirs()) throw IOException("Não foi possível criar a cópia local do projeto.")
        if (!root.isDirectory) throw IOException("O caminho local do projeto não é uma pasta.")
        copyFromSaf(treeUri, root, root.canonicalFile, 0)
        pruneLocalTree(treeUri, root, root.canonicalFile, 0)
    }

    suspend fun toSaf(path: String, treeUri: Uri) = withContext(Dispatchers.IO) {
        val root = File(path).canonicalFile
        if (!root.isDirectory) throw IOException("A cópia local do projeto não está disponível.")
        copyToSaf(root, root, treeUri, 0)
        pruneSafTree(root, root, treeUri, 0)
    }

    private suspend fun copyFromSaf(parentUri: Uri, localParent: File, root: File, depth: Int) {
        checkDepth(depth)
        listSaf(parentUri).forEach { item ->
            validateName(item.name)
            val local = File(localParent, item.name)
            ensureInside(local, root)
            if (item.isDirectory) {
                if (local.exists() && !local.isDirectory) throw IOException("Conflito de tipo ao sincronizar ${item.name}.")
                if (!local.exists() && !local.mkdir()) throw IOException("Não foi possível criar a pasta ${item.name}.")
                copyFromSaf(item.uri, local, root, depth + 1)
            } else {
                if (local.exists() && local.isDirectory) throw IOException("Conflito de tipo ao sincronizar ${item.name}.")
                val temp = File(localParent, ".${item.name}.codux-${UUID.randomUUID()}.tmp")
                try {
                    (resolver.openInputStream(item.uri) ?: throw IOException("Não foi possível ler ${item.name} do projeto.")).use { input ->
                        temp.outputStream().use { output -> input.copyTo(output) }
                    }
                    if (local.exists() && !local.delete()) throw IOException("Não foi possível atualizar ${item.name}.")
                    if (!temp.renameTo(local)) throw IOException("Não foi possível instalar ${item.name} na cópia local.")
                } finally { temp.delete() }
            }
        }
    }

    private suspend fun copyToSaf(root: File, localParent: File, safParent: Uri, depth: Int) {
        checkDepth(depth)
        val children = localParent.listFiles() ?: throw IOException("Não foi possível listar a cópia local do projeto.")
        val remoteItems = listSaf(safParent).associateBy { it.name }
        children.forEach { local ->
            if (local.name.startsWith(".") && local.name.endsWith(".tmp")) return@forEach
            validateName(local.name)
            ensureInside(local, root)
            if (Files.isSymbolicLink(local.toPath())) throw IOException("Ligação simbólica não suportada: ${local.name}.")
            val remote = remoteItems[local.name]
            if (local.isDirectory) {
                if (remote != null && !remote.isDirectory) throw IOException("Conflito de tipo ao sincronizar ${local.name}.")
                val directory = remote?.uri ?: create(safParent, local.name, true)
                copyToSaf(root, local, directory, depth + 1)
            } else if (local.isFile) {
                if (remote != null && remote.isDirectory) throw IOException("Conflito de tipo ao sincronizar ${local.name}.")
                writeFile(local, remote?.uri, safParent)
            }
        }
    }

    private fun create(parentUri: Uri, name: String, directory: Boolean): Uri {
        val tree = asTreeUri(parentUri)
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, documentId(parentUri))
        return DocumentsContract.createDocument(resolver, parent, if (directory) Document.MIME_TYPE_DIR else mimeFor(name), name)
            ?: throw IOException("Não foi possível criar $name no projeto.")
    }

    private fun writeFile(local: File, current: Uri?, parentUri: Uri) {
        if (current != null && sameContent(local, current)) return
        val tree = asTreeUri(parentUri)
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, documentId(parentUri))
        val temp = DocumentsContract.createDocument(resolver, parent, mimeFor(local.name), ".${local.name}.codux-${UUID.randomUUID()}.tmp")
            ?: throw IOException("Não foi possível preparar ${local.name} para sincronização.")
        var backup: Uri? = null
        try {
            local.inputStream().use { input ->
                val output = resolver.openOutputStream(temp, "wt") ?: throw IOException("Não foi possível escrever ${local.name} no projeto.")
                output.use { input.copyTo(it) }
            }
            if (current == null) {
                if (DocumentsContract.renameDocument(resolver, temp, local.name) == null) throw IOException("Não foi possível instalar ${local.name} no projeto.")
            } else {
                backup = DocumentsContract.renameDocument(resolver, current, ".${local.name}.codux-${UUID.randomUUID()}.backup")
                    ?: throw IOException("Não foi possível proteger a versão atual de ${local.name}.")
                if (DocumentsContract.renameDocument(resolver, temp, local.name) == null) {
                    if (DocumentsContract.renameDocument(resolver, backup, local.name) != null) backup = null
                    throw IOException("Não foi possível atualizar ${local.name} no projeto.")
                }
                backup?.let { if (DocumentsContract.deleteDocument(resolver, it)) backup = null }
            }
        } finally {
            runCatching { DocumentsContract.deleteDocument(resolver, temp) }
        }
    }

    private fun sameContent(local: File, remote: Uri): Boolean {
        val localHash = local.inputStream().use(::sha256)
        val remoteHash = (resolver.openInputStream(remote) ?: return false).use(::sha256)
        return MessageDigest.isEqual(localHash, remoteHash)
    }

    private fun sha256(input: InputStream): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(32 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        return digest.digest()
    }

    private fun asTreeUri(uri: Uri): Uri {
        val authority = uri.authority ?: throw IOException("O fornecedor de ficheiros não está disponível.")
        val id = runCatching { DocumentsContract.getTreeDocumentId(uri) }
            .getOrElse { DocumentsContract.getDocumentId(uri) }
        return DocumentsContract.buildTreeDocumentUri(authority, id)
    }

    private fun documentId(uri: Uri): String = runCatching { DocumentsContract.getDocumentId(uri) }
        .getOrElse { DocumentsContract.getTreeDocumentId(uri) }

    private fun mimeFor(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "json" -> "application/json"
        "xml" -> "application/xml"
        "html" -> "text/html"
        "css" -> "text/css"
        "kt", "kts", "java", "js", "ts", "py", "sh", "rs", "c", "h", "cpp", "md", "txt", "yaml", "yml", "properties" -> "text/plain"
        else -> "application/octet-stream"
    }

    private fun validateName(name: String) {
        if (name.isBlank() || name == "." || name == ".." || name.contains('/') || name.contains('\\') || name.indexOf('\u0000') >= 0) {
            throw IOException("O projeto contém um nome de ficheiro inválido.")
        }
    }

    private fun ensureInside(file: File, root: File) {
        if (!file.canonicalFile.toPath().startsWith(root.toPath())) throw IOException("Foi detetado um caminho fora da pasta do projeto.")
    }

    private fun checkDepth(depth: Int) {
        if (depth > 100) throw IOException("O projeto tem pastas demasiado profundas para sincronizar.")
    }

    private suspend fun pruneLocalTree(parentUri: Uri, localParent: File, root: File, depth: Int) {
        checkDepth(depth)
        val remote = listSaf(parentUri)
        val remoteNames = remote.mapTo(mutableSetOf()) { it.name }
        val locals = localParent.listFiles() ?: throw IOException("Não foi possível listar a cópia local do projeto.")
        locals.filter { it.name !in remoteNames && !isRecoveryFile(it.name) }.forEach { stale ->
            ensureInside(stale, root)
            if (Files.isSymbolicLink(stale.toPath())) Files.deleteIfExists(stale.toPath())
            else if (!stale.deleteRecursively()) throw IOException("Não foi possível remover ${stale.name} da cópia local.")
        }
        remote.filter { it.isDirectory }.forEach { item ->
            val local = File(localParent, item.name)
            if (local.isDirectory) pruneLocalTree(item.uri, local, root, depth + 1)
        }
    }

    private suspend fun pruneSafTree(root: File, localParent: File, safParent: Uri, depth: Int) {
        checkDepth(depth)
        val locals = localParent.listFiles() ?: throw IOException("Não foi possível listar a cópia local do projeto.")
        val localNames = locals.filterNot { isRecoveryFile(it.name) }.mapTo(mutableSetOf()) { it.name }
        val remote = listSaf(safParent)
        remote.filter { it.name !in localNames && !isRecoveryFile(it.name) }.forEach { stale ->
            if (!DocumentsContract.deleteDocument(resolver, stale.uri)) throw IOException("Não foi possível remover ${stale.name} da pasta escolhida.")
        }
        locals.filter { it.isDirectory && !isRecoveryFile(it.name) }.forEach { local ->
            ensureInside(local, root)
            val directory = remote.firstOrNull { it.name == local.name && it.isDirectory } ?: return@forEach
            pruneSafTree(root, local, directory.uri, depth + 1)
        }
    }

    private fun listSaf(parentUri: Uri): List<WorkspaceFile> {
        val tree = asTreeUri(parentUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, documentId(parentUri))
        val projection = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED)
        val result = mutableListOf<WorkspaceFile>()
        resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.value(Document.COLUMN_DOCUMENT_ID) ?: continue
                val name = cursor.value(Document.COLUMN_DISPLAY_NAME) ?: "(sem nome)"
                val mime = cursor.value(Document.COLUMN_MIME_TYPE).orEmpty()
                val size = cursor.longValue(Document.COLUMN_SIZE)
                val modified = cursor.longValue(Document.COLUMN_LAST_MODIFIED)?.takeIf { it > 0 }
                result += WorkspaceFile(DocumentsContract.buildDocumentUriUsingTree(tree, id), name, mime == Document.MIME_TYPE_DIR, size, modified)
            }
        } ?: throw IOException("O fornecedor não permitiu listar esta pasta.")
        return result
    }

    private fun Cursor.value(column: String): String? {
        val index = getColumnIndex(column)
        return if (index >= 0 && !isNull(index)) getString(index) else null
    }

    private fun Cursor.longValue(column: String): Long? {
        val index = getColumnIndex(column)
        return if (index >= 0 && !isNull(index)) getLong(index).takeIf { it >= 0 } else null
    }

    private fun isRecoveryFile(name: String): Boolean = name.startsWith(".") && name.contains(".codux-") && (name.endsWith(".tmp") || name.endsWith(".backup"))
}
