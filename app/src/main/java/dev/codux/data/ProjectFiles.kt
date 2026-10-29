package dev.codux.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.database.Cursor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.UUID

class ProjectFiles(private val context: Context) {
    private val resolver: ContentResolver get() = context.contentResolver
    private val maxBytes = 2 * 1024 * 1024
    private val textExtensions = setOf("kt", "kts", "java", "xml", "json", "md", "txt", "yml", "yaml", "gradle", "properties", "toml", "sh", "py", "js", "ts", "html", "css", "csv", "gitignore", "sql", "c", "h", "cpp", "rs")
    private val childProjection = arrayOf(
        Document.COLUMN_DOCUMENT_ID,
        Document.COLUMN_DISPLAY_NAME,
        Document.COLUMN_MIME_TYPE,
        Document.COLUMN_SIZE
    )

    suspend fun list(directoryUri: Uri): List<WorkspaceFile> = withContext(Dispatchers.IO) {
        val treeUri = asTreeUri(directoryUri)
        val parentId = documentId(directoryUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        val children = mutableListOf<WorkspaceFile>()
        resolver.query(childrenUri, childProjection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext() && children.size < 500) {
                childFromCursor(cursor, treeUri)?.let(children::add)
            }
        } ?: throw IOException("O fornecedor não permitiu listar esta pasta.")
        children.sortedWith(compareBy<WorkspaceFile>({ !it.isDirectory }, { it.name.lowercase() }))
    }

    suspend fun read(parentUri: Uri, uri: Uri, name: String): OpenDocument = withContext(Dispatchers.IO) {
        val mime = queryMimeType(uri) ?: "text/plain"
        val extension = name.substringAfterLast('.', "").lowercase()
        if (!mime.startsWith("text/") && mime !in setOf("application/json", "application/xml", "application/javascript") && extension !in textExtensions) {
            throw IOException("O editor só abre ficheiros de texto.")
        }
        val content = readText(uri)
        OpenDocument(uri, parentUri, name, content, content, mime)
    }

    suspend fun save(document: OpenDocument): OpenDocument = withContext(Dispatchers.IO) {
        val treeUri = asTreeUri(document.parentUri)
        val parentId = documentId(document.parentUri)
        val parentDocumentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentId)
        if (queryMimeType(parentDocumentUri) != Document.MIME_TYPE_DIR) throw IOException("A pasta já não está disponível.")
        val currentId = documentId(document.uri)
        val current = DocumentsContract.buildDocumentUriUsingTree(treeUri, currentId)
        if (queryDisplayName(current) != document.name) throw IOException("O ficheiro foi movido ou renomeado. Reabre-o antes de guardar.")
        if (queryMimeType(current) == Document.MIME_TYPE_DIR) throw IOException("O item selecionado já não é um ficheiro.")
        if (readText(current) != document.originalText) {
            throw IOException("O ficheiro foi alterado desde que o abriste. Reabre-o antes de guardar.")
        }

        val suffix = UUID.randomUUID().toString()
        val tempName = ".${document.name}.codux-$suffix.tmp"
        val backupName = ".${document.name}.codux-$suffix.backup"
        val tempUri = DocumentsContract.createDocument(resolver, parentDocumentUri, document.mimeType, tempName)
            ?: throw IOException("O fornecedor não permite criar um ficheiro temporário.")
        var backupUri: Uri? = null
        var replacementInstalled = false
        try {
            writeText(tempUri, document.editedText)
            if (readText(tempUri) != document.editedText) throw IOException("Não foi possível validar a cópia editada.")
            if (readText(current) != document.originalText) {
                throw IOException("O ficheiro foi alterado durante a edição. Reabre-o antes de guardar.")
            }

            val savedBackup = DocumentsContract.renameDocument(resolver, current, backupName)
                ?: throw IOException("O fornecedor não permite criar uma cópia de segurança por renomeação.")
            backupUri = savedBackup
            val replacementUri = DocumentsContract.renameDocument(resolver, tempUri, document.name)
            if (replacementUri == null) {
                val restored = DocumentsContract.renameDocument(resolver, savedBackup, document.name)
                backupUri = if (restored == null) savedBackup else null
                if (restored == null) throw IOException("A instalação falhou; a cópia original continua guardada como $backupName.")
                throw IOException("Não foi possível instalar a versão editada; a cópia original foi reposta.")
            }
            replacementInstalled = true

            val savedUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getDocumentId(replacementUri))
            if (readText(savedUri) != document.editedText) {
                val failedName = ".${document.name}.codux-$suffix.failed"
                val movedFailed = DocumentsContract.renameDocument(resolver, savedUri, failedName)
                val removedFailed = movedFailed != null || runCatching { DocumentsContract.deleteDocument(resolver, savedUri) }.getOrDefault(false)
                if (!removedFailed) throw IOException("A validação falhou; a cópia original continua guardada como $backupName.")
                val restored = DocumentsContract.renameDocument(resolver, savedBackup, document.name)
                if (restored == null) throw IOException("A validação falhou; a cópia original continua guardada como $backupName.")
                backupUri = null
                throw IOException(if (movedFailed == null) "A validação falhou; a cópia original foi reposta." else "A validação falhou; a versão anterior foi reposta.")
            }

            backupUri?.let { DocumentsContract.deleteDocument(resolver, it) }
            backupUri = null
            document.copy(uri = savedUri, originalText = document.editedText, editedText = document.editedText)
        } catch (error: Exception) {
            if (!replacementInstalled && backupUri != null) {
                val originalStillAtName = findChild(treeUri, parentId, document.name)
                if (originalStillAtName == null) {
                    val restored = DocumentsContract.renameDocument(resolver, backupUri, document.name)
                    if (restored != null) backupUri = null
                }
            }
            if (!replacementInstalled) runCatching { DocumentsContract.deleteDocument(resolver, tempUri) }
            throw error
        }
    }

    fun displayName(uri: Uri): String = queryDisplayName(uri) ?: uri.lastPathSegment ?: "Ficheiro"

    private fun documentId(uri: Uri): String = runCatching { DocumentsContract.getDocumentId(uri) }
        .getOrElse { DocumentsContract.getTreeDocumentId(uri) }

    private fun asTreeUri(uri: Uri): Uri {
        val authority = uri.authority ?: throw IOException("O fornecedor de ficheiros não está disponível.")
        val treeDocumentId = runCatching { DocumentsContract.getTreeDocumentId(uri) }
            .getOrElse { DocumentsContract.getDocumentId(uri) }
        return DocumentsContract.buildTreeDocumentUri(authority, treeDocumentId)
    }

    private fun findChild(treeUri: Uri, parentId: String, name: String): Uri? {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        resolver.query(childrenUri, childProjection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val displayName = cursor.string(Document.COLUMN_DISPLAY_NAME) ?: continue
                if (displayName == name) {
                    val id = cursor.string(Document.COLUMN_DOCUMENT_ID) ?: return null
                    return DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                }
            }
        } ?: throw IOException("O fornecedor não permitiu localizar o ficheiro.")
        return null
    }

    private fun childFromCursor(cursor: Cursor, treeUri: Uri): WorkspaceFile? {
        val id = cursor.string(Document.COLUMN_DOCUMENT_ID) ?: return null
        val name = cursor.string(Document.COLUMN_DISPLAY_NAME) ?: "(sem nome)"
        val mime = cursor.string(Document.COLUMN_MIME_TYPE).orEmpty()
        val sizeIndex = cursor.getColumnIndex(Document.COLUMN_SIZE)
        val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex).takeIf { it >= 0 } else null
        return WorkspaceFile(
            DocumentsContract.buildDocumentUriUsingTree(treeUri, id),
            name,
            mime == Document.MIME_TYPE_DIR,
            size
        )
    }

    private fun queryMimeType(uri: Uri): String? = resolver.query(uri, arrayOf(Document.COLUMN_MIME_TYPE), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.string(Document.COLUMN_MIME_TYPE) else null
    }

    private fun queryDisplayName(uri: Uri): String? = resolver.query(uri, arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.string(Document.COLUMN_DISPLAY_NAME) else null
    }

    private fun readText(uri: Uri): String {
        val input = resolver.openInputStream(uri) ?: throw IOException("O fornecedor não permitiu ler o ficheiro.")
        val bytes = input.use { stream ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                total += count
                if (total > maxBytes) throw IOException("Ficheiro demasiado grande (limite de 2 MB).")
                out.write(buffer, 0, count)
            }
            out.toByteArray()
        }
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (error: java.nio.charset.CharacterCodingException) {
            throw IOException("O ficheiro não contém texto UTF-8 válido e não foi alterado.", error)
        }
    }

    private fun writeText(uri: Uri, text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        if (bytes.size > maxBytes) throw IOException("O editor só guarda ficheiros até 2 MB.")
        resolver.openOutputStream(uri, "wt")?.use { stream ->
            stream.write(bytes)
            stream.flush()
        } ?: throw IOException("O fornecedor não permitiu escrever o ficheiro temporário.")
    }

    private fun Cursor.string(column: String): String? {
        val index = getColumnIndex(column)
        return if (index >= 0 && !isNull(index)) getString(index) else null
    }
}
