package dev.codux

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.codux.data.AppState
import dev.codux.data.OpenDocument
import dev.codux.data.ProjectFiles
import dev.codux.data.UnifiedDiff
import dev.codux.data.WorkspaceFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

/** Um ficheiro aberto no editor, com o texto em edição. */
@Stable
internal class EditorTab(initial: OpenDocument) {
    var document by mutableStateOf(initial)
    var value by mutableStateOf(TextFieldValue(initial.editedText))
    val dirty: Boolean get() = value.text != document.originalText
}

/** Separadores abertos; vive em CoduxApp para sobreviver à navegação entre ecrãs. */
@Stable
internal class EditorSession {
    val tabs = mutableStateListOf<EditorTab>()
    var activeIndex by mutableIntStateOf(0)
    val active: EditorTab? get() = tabs.getOrNull(activeIndex)

    fun open(document: OpenDocument) {
        val existing = tabs.indexOfFirst { it.document.uri == document.uri }
        if (existing >= 0) {
            activeIndex = existing
        } else {
            tabs.add(EditorTab(document))
            activeIndex = tabs.lastIndex
        }
    }

    fun close(tab: EditorTab) {
        val index = tabs.indexOf(tab)
        if (index < 0) return
        tabs.removeAt(index)
        activeIndex = activeIndex.coerceIn(0, (tabs.size - 1).coerceAtLeast(0))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditorScreen(
    state: AppState,
    session: EditorSession,
    onMenu: () -> Unit,
    onGoProjects: () -> Unit,
    onList: (Uri) -> Unit,
    onUp: () -> Unit,
    onCreate: (String, Boolean) -> Unit,
    onRename: (WorkspaceFile, String) -> Unit,
    onDelete: (WorkspaceFile) -> Unit,
    onImport: () -> Unit,
    onCopy: (WorkspaceFile) -> Unit,
    onMove: (WorkspaceFile, Uri) -> Unit,
    onError: (String) -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val projectFiles = remember(context) { ProjectFiles(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var showExplorer by remember { mutableStateOf(false) }
    var closeCandidate by remember { mutableStateOf<EditorTab?>(null) }
    var diffText by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var newMenu by remember { mutableStateOf(false) }
    var createKind by remember { mutableStateOf<Boolean?>(null) }
    var createName by remember { mutableStateOf("") }
    var renameFile by remember { mutableStateOf<WorkspaceFile?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteFile by remember { mutableStateOf<WorkspaceFile?>(null) }
    var moveFile by remember { mutableStateOf<WorkspaceFile?>(null) }
    val project = state.projects.firstOrNull { it.uri == state.workspaceUri }

    LaunchedEffect(state.workspaceUri) {
        if (session.tabs.isEmpty() && state.workspaceUri != null) showExplorer = true
    }

    fun openFile(file: WorkspaceFile) {
        if (file.isDirectory) {
            onList(file.uri)
            return
        }
        val parent = state.currentDirectoryUri ?: return
        scope.launch {
            runCatching { projectFiles.read(parent, file.uri, file.name) }
                .onSuccess { session.open(it); showExplorer = false }
                .onFailure { onError(it.message ?: "Não foi possível abrir o ficheiro.") }
        }
    }

    fun save(tab: EditorTab) {
        if (saving || !tab.dirty) return
        saving = true
        scope.launch {
            runCatching { projectFiles.save(tab.document.copy(editedText = tab.value.text)) }
                .onSuccess { saved -> tab.document = saved; onSaved() }
                .onFailure { onError(it.message ?: "Falha ao guardar o ficheiro.") }
            saving = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SidebarToggle(onMenu)
            SplitTitlePill("Editor Codux", project?.name ?: "Projeto", Modifier.weight(1f)) { showExplorer = true }
        }
        if (state.workspaceUri == null) {
            Box(Modifier.fillMaxWidth().padding(16.dp)) {
                EmptyStateCard("Nenhum projeto aberto", "Escolhe um projeto para editar os ficheiros.", "Ver projetos", onGoProjects)
            }
        } else {
            val active = session.active
            Surface(color = MaterialTheme.colorScheme.background, border = BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 6.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        session.tabs.forEachIndexed { index, tab ->
                            EditorTabChip(
                                tab = tab,
                                selected = index == session.activeIndex,
                                onClick = { session.activeIndex = index },
                                onClose = { if (tab.dirty) closeCandidate = tab else session.close(tab) }
                            )
                        }
                    }
                    IconButton(onClick = { showExplorer = true }, modifier = Modifier.size(38.dp)) { Icon(Icons.Default.Folder, "Ficheiros") }
                    IconButton(onClick = { active?.let { save(it) } }, enabled = active?.dirty == true && !saving, modifier = Modifier.size(38.dp)) { Icon(Icons.Default.Save, "Guardar") }
                    Box {
                        IconButton(onClick = { moreMenu = true }, modifier = Modifier.size(38.dp)) { Icon(Icons.Default.MoreVert, "Mais") }
                        DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                            DropdownMenuItem(text = { Text("Ver alterações") }, enabled = active != null, onClick = {
                                moreMenu = false
                                active?.let { diffText = UnifiedDiff.create(it.document.originalText, it.value.text) }
                            })
                            DropdownMenuItem(text = { Text("Fechar ficheiro") }, enabled = active != null, onClick = {
                                moreMenu = false
                                active?.let { if (it.dirty) closeCandidate = it else session.close(it) }
                            })
                        }
                    }
                }
            }
            if (active == null) {
                Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Code, null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("Nenhum ficheiro aberto", Modifier.padding(top = 10.dp, bottom = 14.dp), style = MaterialTheme.typography.titleMedium)
                    Button(onClick = { showExplorer = true }) { Text("Abrir ficheiros") }
                }
            } else {
                CodeEditor(active, !saving, Modifier.weight(1f).fillMaxWidth())
                SymbolBar(active) {
                    val value = active.value
                    val selection = value.selection
                    val text = if (!selection.collapsed) value.text.substring(selection.min, selection.max) else value.text
                    clipboard.setText(AnnotatedString(text))
                }
            }
        }
    }

    if (showExplorer) {
        ModalBottomSheet(onDismissRequest = { showExplorer = false }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.directoryStack.size > 1) IconButton(onClick = onUp) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Pasta anterior") }
                    Text(project?.name ?: "Projeto", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Box {
                        IconButton(onClick = { newMenu = true }) { Icon(Icons.Default.Add, "Novo") }
                        DropdownMenu(expanded = newMenu, onDismissRequest = { newMenu = false }) {
                            DropdownMenuItem(text = { Text("Novo arquivo") }, onClick = { newMenu = false; createKind = false; createName = "" })
                            DropdownMenuItem(text = { Text("Nova pasta") }, onClick = { newMenu = false; createKind = true; createName = "" })
                            DropdownMenuItem(text = { Text("Carregar ficheiro") }, onClick = { newMenu = false; onImport() })
                        }
                    }
                }
                if (state.files.isEmpty()) {
                    Text("Pasta vazia.", Modifier.padding(vertical = 18.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f))
                } else {
                    LazyColumn(Modifier.heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        items(state.files, key = { it.uri.toString() }) { file ->
                            ManagedFileRow(
                                file,
                                onClick = { openFile(file) },
                                onRename = { renameFile = file; renameText = file.name },
                                onDelete = { deleteFile = file },
                                onCopy = { onCopy(file) },
                                onMove = { moveFile = file }
                            )
                        }
                    }
                }
            }
        }
    }

    closeCandidate?.let { tab ->
        AlertDialog(
            onDismissRequest = { closeCandidate = null },
            title = { Text("Descartar alterações?") },
            text = { Text(tab.document.name + " tem alterações por guardar.") },
            confirmButton = { TextButton(onClick = { closeCandidate = null; session.close(tab) }) { Text("Descartar") } },
            dismissButton = { TextButton(onClick = { closeCandidate = null }) { Text("Continuar a editar") } }
        )
    }
    diffText?.let { diff ->
        AlertDialog(
            onDismissRequest = { diffText = null },
            title = { Text("Alterações") },
            text = { LazyColumn(Modifier.heightIn(max = 520.dp)) { item { Text(diff.ifBlank { "Ainda não há alterações para mostrar." }, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) } } },
            confirmButton = { TextButton(onClick = { diffText = null }) { Text("Fechar") } }
        )
    }
    createKind?.let { directory ->
        AlertDialog(
            onDismissRequest = { createKind = null },
            title = { Text(if (directory) "Nova pasta" else "Novo arquivo") },
            text = { OutlinedTextField(value = createName, onValueChange = { createName = it }, singleLine = true, label = { Text("Nome") }) },
            confirmButton = { TextButton(onClick = { if (createName.isNotBlank()) onCreate(createName, directory); createKind = null }) { Text("Criar") } },
            dismissButton = { TextButton(onClick = { createKind = null }) { Text("Cancelar") } }
        )
    }
    renameFile?.let { file ->
        AlertDialog(
            onDismissRequest = { renameFile = null },
            title = { Text("Renomear") },
            text = { OutlinedTextField(value = renameText, onValueChange = { renameText = it }, singleLine = true, label = { Text("Novo nome") }) },
            confirmButton = { TextButton(onClick = { if (renameText.isNotBlank()) onRename(file, renameText); renameFile = null }) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = { renameFile = null }) { Text("Cancelar") } }
        )
    }
    moveFile?.let { file ->
        val directories = state.files.filter { it.isDirectory && it.uri != file.uri }
        AlertDialog(
            onDismissRequest = { moveFile = null },
            title = { Text("Mover " + file.name) },
            text = {
                if (directories.isEmpty()) Text("Não há outras pastas visíveis neste nível. Navega até uma pasta de destino e tenta novamente.")
                else LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(directories, key = { it.uri.toString() }) { folder ->
                        TextButton(onClick = { onMove(file, folder.uri); moveFile = null }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Folder, null); Text(folder.name, Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { moveFile = null }) { Text("Fechar") } }
        )
    }
    deleteFile?.let { file ->
        AlertDialog(
            onDismissRequest = { deleteFile = null },
            title = { Text("Apagar " + file.name + "?") },
            text = { Text("Esta ação remove o item da pasta selecionada.") },
            confirmButton = { TextButton(onClick = { onDelete(file); deleteFile = null }) { Text("Apagar") } },
            dismissButton = { TextButton(onClick = { deleteFile = null }) { Text("Cancelar") } }
        )
    }
}

private fun languageBadge(name: String): Pair<String, Color> = when (name.substringAfterLast('.', "").lowercase()) {
    "kt", "kts" -> "K" to Color(0xFF7F52FF)
    "java" -> "J" to Color(0xFFE76F00)
    "cpp", "cc", "cxx", "hpp", "h" -> "c++" to Color(0xFF0B57FF)
    "c" -> "C" to Color(0xFF0B57FF)
    "js", "jsx" -> "JS" to Color(0xFFC9A100)
    "ts", "tsx" -> "TS" to Color(0xFF2F74C0)
    "py" -> "PY" to Color(0xFF3776AB)
    "xml" -> "XML" to Color(0xFF2E8B57)
    "json" -> "{}" to Color(0xFF8A8A00)
    "html" -> "HTML" to Color(0xFFE34F26)
    "css" -> "CSS" to Color(0xFF1572B6)
    "md" -> "MD" to Color(0xFF555555)
    else -> "TXT" to Color(0xFF777777)
}

@Composable
private fun EditorTabChip(tab: EditorTab, selected: Boolean, onClick: () -> Unit, onClose: () -> Unit) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val (badge, badgeColor) = languageBadge(tab.document.name)
    val shape = RoundedCornerShape(22.dp)
    Surface(
        shape = shape,
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .35f) else MaterialTheme.colorScheme.background,
        border = BorderStroke(2.dp, onSurface),
        modifier = Modifier.height(42.dp).clip(shape).clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(start = 10.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.background, border = BorderStroke(1.5.dp, badgeColor)) {
                Text(badge, Modifier.padding(horizontal = 5.dp, vertical = 1.dp), color = badgeColor, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
            }
            Text(
                (if (tab.dirty) "● " else "") + tab.document.name,
                Modifier.padding(horizontal = 8.dp),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(Icons.Default.Close, "Fechar " + tab.document.name, Modifier.size(18.dp).clickable(onClick = onClose))
        }
    }
}

@Composable
private fun CodeEditor(tab: EditorTab, enabled: Boolean, modifier: Modifier) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 21.sp, color = onSurface)
    val lineCount = remember(tab.value.text) { tab.value.text.count { it == '\n' } + 1 }
    val gutter = remember(lineCount) { (1..lineCount).joinToString("\n") }
    val gutterWidth = (lineCount.toString().length * 9 + 14).dp
    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()
    Row(modifier.verticalScroll(verticalScroll)) {
        Text(
            gutter,
            style = textStyle.copy(color = onSurface.copy(alpha = .55f), textAlign = TextAlign.End),
            softWrap = false,
            modifier = Modifier.width(gutterWidth).padding(start = 4.dp, end = 8.dp, top = 12.dp)
        )
        Box(Modifier.weight(1f).horizontalScroll(horizontalScroll)) {
            BasicTextField(
                value = tab.value,
                onValueChange = { tab.value = it },
                enabled = enabled,
                textStyle = textStyle,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrect = false),
                modifier = Modifier.widthIn(min = 320.dp).heightIn(min = 420.dp).padding(top = 12.dp, end = 24.dp)
            )
        }
    }
}

@Composable
private fun SymbolBar(tab: EditorTab, onCopy: () -> Unit) {
    val symbols = listOf("{", "}", "(", ")", "[", "]", ";", "=", ".", ",", "/", "\"", "'", "<", ">", ":", "!", "&", "|", "+", "-", "*", "_", "#", "\\")
    Surface(color = MaterialTheme.colorScheme.background, border = BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            SymbolKey("Tab") { tab.value = insertText(tab.value, "    ") }
            SymbolKey("//") { tab.value = toggleComment(tab.value, commentPrefix(tab.document.name)) }
            SymbolKey("↑") { tab.value = moveLine(tab.value, -1) }
            SymbolKey("↓") { tab.value = moveLine(tab.value, 1) }
            IconButton(onClick = onCopy, modifier = Modifier.size(42.dp)) { Icon(Icons.Default.ContentCopy, "Copiar", modifier = Modifier.size(20.dp)) }
            Box(Modifier.width(2.dp).height(26.dp).background(MaterialTheme.colorScheme.onSurface))
            symbols.forEach { symbol -> SymbolKey(symbol) { tab.value = insertText(tab.value, symbol) } }
        }
    }
}

@Composable
private fun SymbolKey(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 10.dp), modifier = Modifier.height(42.dp).widthIn(min = 38.dp)) {
        Text(label, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

private fun insertText(value: TextFieldValue, insert: String): TextFieldValue {
    val start = value.selection.min
    val end = value.selection.max
    val newText = value.text.replaceRange(start, end, insert)
    return value.copy(text = newText, selection = TextRange(start + insert.length))
}

private fun commentPrefix(fileName: String): String = when (fileName.substringAfterLast('.', "").lowercase()) {
    "py", "sh", "yml", "yaml", "toml", "properties", "rb" -> "#"
    else -> "//"
}

private fun toggleComment(value: TextFieldValue, prefix: String): TextFieldValue {
    val text = value.text
    val start = value.selection.min
    val end = value.selection.max
    val firstLineStart = text.lastIndexOf('\n', start - 1) + 1
    val lastLineEnd = text.indexOf('\n', end).let { if (it < 0) text.length else it }
    val block = text.substring(firstLineStart, lastLineEnd)
    val lines = block.split('\n')
    val allCommented = lines.filter { it.isNotBlank() }.all { it.trimStart().startsWith(prefix) }
    val changed = lines.joinToString("\n") { line ->
        if (line.isBlank()) {
            line
        } else {
            val indent = line.length - line.trimStart().length
            if (allCommented) line.substring(0, indent) + line.substring(indent).removePrefix(prefix).removePrefix(" ")
            else line.substring(0, indent) + prefix + " " + line.substring(indent)
        }
    }
    val newText = text.substring(0, firstLineStart) + changed + text.substring(lastLineEnd)
    val position = (lastLineEnd + (changed.length - block.length)).coerceIn(0, newText.length)
    return value.copy(text = newText, selection = TextRange(position))
}

private fun moveLine(value: TextFieldValue, delta: Int): TextFieldValue {
    val text = value.text
    val position = value.selection.end.coerceIn(0, text.length)
    val lineStart = text.lastIndexOf('\n', position - 1) + 1
    val column = position - lineStart
    if (delta < 0) {
        if (lineStart == 0) return value.copy(selection = TextRange(0))
        val previousEnd = lineStart - 1
        val previousStart = text.lastIndexOf('\n', previousEnd - 1) + 1
        return value.copy(selection = TextRange(minOf(previousStart + column, previousEnd)))
    }
    val lineEnd = text.indexOf('\n', position).let { if (it < 0) text.length else it }
    if (lineEnd >= text.length) return value.copy(selection = TextRange(text.length))
    val nextStart = lineEnd + 1
    val nextEnd = text.indexOf('\n', nextStart).let { if (it < 0) text.length else it }
    return value.copy(selection = TextRange(minOf(nextStart + column, nextEnd)))
}

@Composable
internal fun ManagedFileRow(file: WorkspaceFile, onClick: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit, onCopy: () -> Unit, onMove: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(15.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .32f)),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (file.isDirectory) Icons.Default.Folder else Icons.Default.Code, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(21.dp))
            Column(Modifier.weight(1f).padding(horizontal = 11.dp)) {
                Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                val metadata = buildList {
                    add(if (file.isDirectory) "Pasta" else (file.size?.let { it.toString() + " B" } ?: "Ficheiro"))
                    file.lastModified?.let { add(SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(it))) }
                }.joinToString(" · ")
                Text(metadata, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .54f))
            }
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "Ações de " + file.name) }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Abrir") }, onClick = { menuOpen = false; onClick() })
                    DropdownMenuItem(text = { Text("Renomear") }, onClick = { menuOpen = false; onRename() })
                    DropdownMenuItem(text = { Text("Copiar") }, onClick = { menuOpen = false; onCopy() })
                    DropdownMenuItem(text = { Text("Mover para…") }, onClick = { menuOpen = false; onMove() })
                    DropdownMenuItem(text = { Text("Apagar") }, onClick = { menuOpen = false; onDelete() })
                }
            }
        }
    }
}
