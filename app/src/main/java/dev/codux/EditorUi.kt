package dev.codux

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
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

private fun TextFieldValue.toState() = EditState(text, selection.start, selection.end)
private fun EditState.toValue() = TextFieldValue(text, TextRange(start, end))

/** Um ficheiro aberto no editor, com o texto em edição e o histórico de desfazer. */
@Stable
internal class EditorTab(initial: OpenDocument) {
    var document by mutableStateOf(initial)
    var value by mutableStateOf(TextFieldValue(initial.editedText))
    var undoDepth by mutableIntStateOf(0)
    var redoDepth by mutableIntStateOf(0)
    private val undoStack = ArrayList<TextFieldValue>()
    private val redoStack = ArrayList<TextFieldValue>()
    private var lastEditAt = 0L

    val language: CodeLanguage get() = CodeSyntax.languageFor(document.name)
    val dirty: Boolean get() = value.text != document.originalText

    /** Edição vinda do teclado: aplica indentação automática e fecho de pares. */
    fun typed(new: TextFieldValue) {
        if (new.text == value.text) {
            value = new
            return
        }
        val adjusted = CodeEditing.afterTyping(value.toState(), new.toState(), language == CodeLanguage.PYTHON)
        // Só substitui o valor do teclado quando uma regra mexeu no texto (preserva a composição do IME).
        commit(if (adjusted == new.toState()) new else adjusted.toValue(), coalesce = true)
    }

    /** Edição pedida pela barra de comandos. */
    fun apply(state: EditState) = commit(state.toValue(), coalesce = false)

    fun select(range: TextRange) {
        value = value.copy(selection = range)
    }

    private fun commit(next: TextFieldValue, coalesce: Boolean) {
        if (next.text != value.text) {
            val now = System.currentTimeMillis()
            if (!coalesce || undoStack.isEmpty() || now - lastEditAt > 600) {
                undoStack.add(value)
                if (undoStack.size > 300) undoStack.removeAt(0)
            }
            lastEditAt = now
            redoStack.clear()
        }
        value = next
        undoDepth = undoStack.size
        redoDepth = redoStack.size
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.add(value)
        value = undoStack.removeAt(undoStack.lastIndex)
        undoDepth = undoStack.size
        redoDepth = redoStack.size
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.add(value)
        value = redoStack.removeAt(redoStack.lastIndex)
        undoDepth = undoStack.size
        redoDepth = redoStack.size
    }
}

/** Separadores abertos e estado da procura; vive em CoduxApp para sobreviver à navegação entre ecrãs. */
@Stable
internal class EditorSession {
    val tabs = mutableStateListOf<EditorTab>()
    var activeIndex by mutableIntStateOf(0)
    val active: EditorTab? get() = tabs.getOrNull(activeIndex)
    var fontSize by mutableIntStateOf(13)
    var findOpen by mutableStateOf(false)
    var replaceOpen by mutableStateOf(false)
    var query by mutableStateOf("")
    var replacement by mutableStateOf("")
    var caseSensitive by mutableStateOf(false)
    var matchIndex by mutableIntStateOf(0)
    var revealOffset by mutableIntStateOf(-1)
    var revealTick by mutableIntStateOf(0)

    fun reveal(offset: Int) {
        revealOffset = offset
        revealTick++
    }

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

/** Cores do VS Code (Dark+ e Light+). */
private class EditorPalette(val dark: Boolean) {
    val background = if (dark) Color(0xFF1E1E1E) else Color(0xFFFFFFFF)
    val foreground = if (dark) Color(0xFFD4D4D4) else Color(0xFF000000)
    val gutter = if (dark) Color(0xFF858585) else Color(0xFF237893)
    val gutterActive = if (dark) Color(0xFFC6C6C6) else Color(0xFF0B216F)
    val currentLine = if (dark) Color(0xFF2A2D2E) else Color(0xFFF0F0F0)
    val selection = if (dark) Color(0xFF264F78) else Color(0xFFADD6FF)
    val findMatch = if (dark) Color(0xFF515C6A) else Color(0xFFFFE0A0)
    val findCurrent = if (dark) Color(0xFF7A6F2E) else Color(0xFFFFB84D)
    val statusBar = Color(0xFF007ACC)
    val panel = if (dark) Color(0xFF252526) else Color(0xFFF3F3F3)
    val border = if (dark) Color(0xFF3C3C3C) else Color(0xFFCCCCCC)

    fun token(type: TokenType): Color = if (dark) {
        when (type) {
            TokenType.KEYWORD -> Color(0xFF569CD6)
            TokenType.CONTROL -> Color(0xFFC586C0)
            TokenType.STRING -> Color(0xFFCE9178)
            TokenType.COMMENT -> Color(0xFF6A9955)
            TokenType.NUMBER -> Color(0xFFB5CEA8)
            TokenType.TYPE -> Color(0xFF4EC9B0)
            TokenType.FUNCTION -> Color(0xFFDCDCAA)
            TokenType.ANNOTATION -> Color(0xFF4EC9B0)
            TokenType.TAG -> Color(0xFF569CD6)
            TokenType.ATTRIBUTE -> Color(0xFF9CDCFE)
            TokenType.PROPERTY -> Color(0xFF9CDCFE)
        }
    } else {
        when (type) {
            TokenType.KEYWORD -> Color(0xFF0000FF)
            TokenType.CONTROL -> Color(0xFFAF00DB)
            TokenType.STRING -> Color(0xFFA31515)
            TokenType.COMMENT -> Color(0xFF008000)
            TokenType.NUMBER -> Color(0xFF098658)
            TokenType.TYPE -> Color(0xFF267F99)
            TokenType.FUNCTION -> Color(0xFF795E26)
            TokenType.ANNOTATION -> Color(0xFF267F99)
            TokenType.TAG -> Color(0xFF800000)
            TokenType.ATTRIBUTE -> Color(0xFFE50000)
            TokenType.PROPERTY -> Color(0xFF001080)
        }
    }
}

private class SyntaxTransformation(
    private val language: CodeLanguage,
    private val palette: EditorPalette,
    private val matches: List<IntRange>,
    private val currentMatch: Int
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val builder = AnnotatedString.Builder(raw)
        CodeSyntax.tokenize(raw, language).forEach { token ->
            val style = if (token.type == TokenType.COMMENT) SpanStyle(color = palette.token(token.type), fontStyle = FontStyle.Italic)
            else SpanStyle(color = palette.token(token.type))
            builder.addStyle(style, token.start, token.end)
        }
        matches.forEachIndexed { index, range ->
            if (range.first >= 0 && range.last + 1 <= raw.length) {
                builder.addStyle(SpanStyle(background = if (index == currentMatch) palette.findCurrent else palette.findMatch), range.first, range.last + 1)
            }
        }
        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
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
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val palette = remember(dark) { EditorPalette(dark) }
    var showExplorer by remember { mutableStateOf(false) }
    var closeCandidate by remember { mutableStateOf<EditorTab?>(null) }
    var diffText by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var newMenu by remember { mutableStateOf(false) }
    var gotoOpen by remember { mutableStateOf(false) }
    var gotoText by remember { mutableStateOf("") }
    var createKind by remember { mutableStateOf<Boolean?>(null) }
    var createName by remember { mutableStateOf("") }
    var renameFile by remember { mutableStateOf<WorkspaceFile?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteFile by remember { mutableStateOf<WorkspaceFile?>(null) }
    var moveFile by remember { mutableStateOf<WorkspaceFile?>(null) }
    val project = state.projects.firstOrNull { it.uri == state.workspaceUri }
    val active = session.active

    LaunchedEffect(state.workspaceUri) {
        if (session.tabs.isEmpty() && state.workspaceUri != null) showExplorer = true
    }

    val matches = remember(active?.value?.text, session.query, session.caseSensitive, session.findOpen) {
        if (session.findOpen && active != null) CodeEditing.findAll(active.value.text, session.query, session.caseSensitive) else emptyList()
    }
    val currentMatch = if (matches.isEmpty()) -1 else session.matchIndex.coerceIn(0, matches.lastIndex)

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

    fun goToMatch(index: Int) {
        val tab = session.active ?: return
        if (matches.isEmpty()) return
        val wrapped = ((index % matches.size) + matches.size) % matches.size
        session.matchIndex = wrapped
        val match = matches[wrapped]
        tab.select(TextRange(match.first, match.last + 1))
        session.reveal(match.first)
    }

    fun replaceCurrent() {
        val tab = session.active ?: return
        if (currentMatch < 0) return
        val match = matches[currentMatch]
        val text = tab.value.text
        val newText = text.replaceRange(match.first, match.last + 1, session.replacement)
        val cursor = match.first + session.replacement.length
        tab.apply(EditState(newText, cursor, cursor))
    }

    fun replaceEverything() {
        val tab = session.active ?: return
        if (session.query.isEmpty()) return
        val newText = CodeEditing.replaceAll(tab.value.text, session.query, session.replacement, session.caseSensitive)
        tab.apply(EditState(newText, 0, 0))
    }

    fun runShortcut(command: String): Boolean {
        val tab = session.active ?: return false
        return when (command) {
            "save" -> { save(tab); true }
            "find" -> { session.findOpen = true; true }
            "goto" -> { gotoText = ""; gotoOpen = true; true }
            "undo" -> { tab.undo(); true }
            "redo" -> { tab.redo(); true }
            "comment" -> { tab.apply(CodeEditing.toggleComment(tab.value.toState(), CodeSyntax.commentPrefix(tab.language))); true }
            "duplicate" -> { tab.apply(CodeEditing.duplicateLines(tab.value.toState())); true }
            "indent" -> { tab.apply(CodeEditing.indent(tab.value.toState(), outdent = false)); true }
            "outdent" -> { tab.apply(CodeEditing.indent(tab.value.toState(), outdent = true)); true }
            else -> false
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
                            DropdownMenuItem(text = { Text("Procurar e substituir") }, enabled = active != null, onClick = { moreMenu = false; session.findOpen = true })
                            DropdownMenuItem(text = { Text("Ir para a linha…") }, enabled = active != null, onClick = { moreMenu = false; gotoText = ""; gotoOpen = true })
                            DropdownMenuItem(text = { Text("Ver alterações") }, enabled = active != null, onClick = {
                                moreMenu = false
                                active?.let { diffText = UnifiedDiff.create(it.document.originalText, it.value.text) }
                            })
                            DropdownMenuItem(text = { Text("Aumentar letra") }, onClick = { session.fontSize = (session.fontSize + 1).coerceAtMost(24) })
                            DropdownMenuItem(text = { Text("Diminuir letra") }, onClick = { session.fontSize = (session.fontSize - 1).coerceAtLeast(9) })
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
                if (session.findOpen) {
                    FindPanel(
                        session = session,
                        palette = palette,
                        matchCount = matches.size,
                        currentMatch = currentMatch,
                        onNext = { goToMatch(currentMatch + 1) },
                        onPrevious = { goToMatch(currentMatch - 1) },
                        onQueryChanged = {
                            val found = CodeEditing.findAll(active.value.text, session.query, session.caseSensitive)
                            if (found.isNotEmpty()) {
                                val cursor = active.value.selection.start
                                val first = found.indexOfFirst { it.first >= cursor }.let { if (it < 0) 0 else it }
                                session.matchIndex = first
                                active.select(TextRange(found[first].first, found[first].last + 1))
                                session.reveal(found[first].first)
                            }
                        },
                        onReplace = { replaceCurrent() },
                        onReplaceAll = { replaceEverything() },
                        onClose = { session.findOpen = false }
                    )
                }
                CodeEditor(
                    tab = active,
                    palette = palette,
                    fontSize = session.fontSize,
                    matches = matches,
                    currentMatch = currentMatch,
                    enabled = !saving,
                    revealOffset = session.revealOffset,
                    revealTick = session.revealTick,
                    onShortcut = { runShortcut(it) },
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
                StatusBar(active, palette)
                CommandBars(
                    tab = active,
                    palette = palette,
                    onCommand = { runShortcut(it) },
                    onCopy = {
                        val value = active.value
                        val selection = value.selection
                        clipboard.setText(AnnotatedString(if (!selection.collapsed) value.text.substring(selection.min, selection.max) else value.text))
                    }
                )
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

    if (gotoOpen) {
        AlertDialog(
            onDismissRequest = { gotoOpen = false },
            title = { Text("Ir para a linha") },
            text = { OutlinedTextField(value = gotoText, onValueChange = { gotoText = it.filter(Char::isDigit) }, singleLine = true, label = { Text("Número da linha") }) },
            confirmButton = {
                TextButton(onClick = {
                    val tab = session.active
                    val line = gotoText.toIntOrNull()
                    if (tab != null && line != null && line > 0) {
                        val offset = CodeEditing.offsetOfLine(tab.value.text, line)
                        tab.select(TextRange(offset))
                        session.reveal(offset)
                    }
                    gotoOpen = false
                }) { Text("Ir") }
            },
            dismissButton = { TextButton(onClick = { gotoOpen = false }) { Text("Cancelar") } }
        )
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
    "swift" -> "SW" to Color(0xFFF05138)
    "rs" -> "RS" to Color(0xFFB7410E)
    "go" -> "GO" to Color(0xFF00ADD8)
    "dart" -> "DT" to Color(0xFF0175C2)
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
private fun CodeEditor(
    tab: EditorTab,
    palette: EditorPalette,
    fontSize: Int,
    matches: List<IntRange>,
    currentMatch: Int,
    enabled: Boolean,
    revealOffset: Int,
    revealTick: Int,
    onShortcut: (String) -> Boolean,
    modifier: Modifier
) {
    val density = LocalDensity.current
    val value = tab.value
    val text = value.text
    val textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = fontSize.sp, lineHeight = (fontSize * 1.6f).sp, color = palette.foreground)
    val lineCount = remember(text) { text.count { it == '\n' } + 1 }
    val cursorLine = remember(text, value.selection.end) { CodeEditing.lineAndColumn(text, value.selection.end).first }
    val gutterText = remember(lineCount, cursorLine, palette) {
        buildAnnotatedString {
            for (line in 1..lineCount) {
                if (line == cursorLine) withStyle(SpanStyle(color = palette.gutterActive)) { append(line.toString()) } else append(line.toString())
                if (line < lineCount) append("\n")
            }
        }
    }
    val gutterWidth = (lineCount.toString().length * fontSize * 0.62f + 20f).dp
    val vertical = rememberScrollState()
    val horizontal = rememberScrollState()
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var viewportWidth by remember { mutableIntStateOf(0) }
    var viewportHeight by remember { mutableIntStateOf(0) }
    val topPad = 10.dp
    val topPadPx = with(density) { topPad.toPx() }
    val transformation = remember(tab.language, palette, matches, currentMatch) { SyntaxTransformation(tab.language, palette, matches, currentMatch) }
    val minTextWidth = with(density) { (viewportWidth.toDp() - gutterWidth).coerceAtLeast(160.dp) }

    LaunchedEffect(revealTick) {
        val current = layout
        if (revealTick > 0 && revealOffset >= 0 && current != null) {
            val line = current.getLineForOffset(revealOffset.coerceIn(0, text.length))
            val target = (current.getLineTop(line) + topPadPx - viewportHeight / 2f).toInt().coerceAtLeast(0)
            vertical.animateScrollTo(target)
        }
    }

    CompositionLocalProvider(LocalTextSelectionColors provides TextSelectionColors(handleColor = MaterialTheme.colorScheme.primary, backgroundColor = palette.selection)) {
        Row(
            modifier
                .background(palette.background)
                .onSizeChanged { viewportWidth = it.width; viewportHeight = it.height }
                .verticalScroll(vertical)
        ) {
            Text(
                gutterText,
                style = textStyle.copy(color = palette.gutter, textAlign = TextAlign.End),
                softWrap = false,
                modifier = Modifier.width(gutterWidth).padding(end = 10.dp, top = topPad)
            )
            Box(
                Modifier
                    .weight(1f)
                    .horizontalScroll(horizontal)
                    .drawBehind {
                        val current = layout
                        if (current != null && value.selection.collapsed) {
                            val line = current.getLineForOffset(value.selection.end.coerceIn(0, text.length))
                            val top = current.getLineTop(line) + topPadPx
                            drawRect(palette.currentLine, Offset(0f, top), Size(size.width, current.getLineBottom(line) - current.getLineTop(line)))
                        }
                    }
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = { tab.typed(it) },
                    enabled = enabled,
                    textStyle = textStyle,
                    cursorBrush = SolidColor(Color(0xFFAEAFAD).takeIf { palette.dark } ?: Color.Black),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrect = false),
                    visualTransformation = transformation,
                    onTextLayout = { layout = it },
                    modifier = Modifier
                        .widthIn(min = minTextWidth)
                        .heightIn(min = 420.dp)
                        .padding(top = topPad, end = 32.dp, bottom = 120.dp)
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            if (event.isCtrlPressed) {
                                when (event.key) {
                                    Key.S -> onShortcut("save")
                                    Key.F -> onShortcut("find")
                                    Key.G -> onShortcut("goto")
                                    Key.Z -> onShortcut(if (event.isShiftPressed) "redo" else "undo")
                                    Key.Y -> onShortcut("redo")
                                    Key.Slash -> onShortcut("comment")
                                    Key.D -> onShortcut("duplicate")
                                    else -> false
                                }
                            } else if (event.key == Key.Tab) {
                                onShortcut(if (event.isShiftPressed) "outdent" else "indent")
                            } else {
                                false
                            }
                        }
                )
            }
        }
    }
}

@Composable
private fun StatusBar(tab: EditorTab, palette: EditorPalette) {
    val value = tab.value
    val (line, column) = CodeEditing.lineAndColumn(value.text, value.selection.end)
    val selected = value.selection.max - value.selection.min
    val label = buildString {
        append("Ln ").append(line).append(", Col ").append(column)
        if (selected > 0) append(" (").append(selected).append(" selecionados)")
    }
    Row(Modifier.fillMaxWidth().background(palette.statusBar).padding(horizontal = 10.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(1f), maxLines = 1)
        Text("Espaços: 4   UTF-8   " + tab.language.label, color = Color.White, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun CommandBars(tab: EditorTab, palette: EditorPalette, onCommand: (String) -> Boolean, onCopy: () -> Unit) {
    val symbols = listOf("{", "}", "(", ")", "[", "]", ";", "=", ".", ",", "/", "\"", "'", "<", ">", ":", "!", "&", "|", "+", "-", "*", "_", "#", "\\", "$", "@", "?", "~", "^", "%")
    Column(Modifier.fillMaxWidth().background(palette.panel).border(1.dp, palette.border)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconCommand(Icons.AutoMirrored.Filled.Undo, "Desfazer", tab.undoDepth > 0) { onCommand("undo") }
            IconCommand(Icons.AutoMirrored.Filled.Redo, "Refazer", tab.redoDepth > 0) { onCommand("redo") }
            IconCommand(Icons.Default.Search, "Procurar", true) { onCommand("find") }
            TextCommand("Ln") { onCommand("goto") }
            TextCommand("Tab") { onCommand("indent") }
            TextCommand("⇤") { onCommand("outdent") }
            TextCommand("//") { onCommand("comment") }
            IconCommand(Icons.Default.KeyboardArrowUp, "Cursor para cima", true) { tab.apply(CodeEditing.moveCursorLine(tab.value.toState(), -1)) }
            IconCommand(Icons.Default.KeyboardArrowDown, "Cursor para baixo", true) { tab.apply(CodeEditing.moveCursorLine(tab.value.toState(), 1)) }
            IconCommand(Icons.Default.ArrowUpward, "Subir linha", true) { tab.apply(CodeEditing.moveLines(tab.value.toState(), -1)) }
            IconCommand(Icons.Default.ArrowDownward, "Descer linha", true) { tab.apply(CodeEditing.moveLines(tab.value.toState(), 1)) }
            TextCommand("Dup") { onCommand("duplicate") }
            TextCommand("Del") { tab.apply(CodeEditing.deleteLines(tab.value.toState())) }
            IconCommand(Icons.Default.ContentCopy, "Copiar", true, onCopy)
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            symbols.forEach { symbol ->
                TextCommand(symbol) {
                    val state = tab.value.toState()
                    val inserted = state.text.replaceRange(state.min, state.max, symbol)
                    // Passa pelas mesmas regras do teclado (fecho de pares) quando é um único carácter.
                    val raw = EditState(inserted, state.min + symbol.length, state.min + symbol.length)
                    tab.typed(TextFieldValue(raw.text, TextRange(raw.start, raw.end)))
                }
            }
        }
    }
}

@Composable
private fun IconCommand(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(42.dp)) {
        Icon(icon, description, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun TextCommand(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.height(42.dp).widthIn(min = 38.dp)) {
        Text(label, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun PanelField(value: String, onChange: (String) -> Unit, placeholder: String, palette: EditorPalette, modifier: Modifier) {
    Box(modifier.height(36.dp).background(palette.background).border(1.dp, palette.border).padding(horizontal = 8.dp), contentAlignment = Alignment.CenterStart) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = palette.foreground),
            cursorBrush = SolidColor(palette.foreground),
            modifier = Modifier.fillMaxWidth()
        )
        if (value.isEmpty()) Text(placeholder, color = palette.foreground.copy(alpha = .45f), fontSize = 13.sp)
    }
}

@Composable
private fun FindPanel(
    session: EditorSession,
    palette: EditorPalette,
    matchCount: Int,
    currentMatch: Int,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onQueryChanged: () -> Unit,
    onReplace: () -> Unit,
    onReplaceAll: () -> Unit,
    onClose: () -> Unit
) {
    Column(Modifier.fillMaxWidth().background(palette.panel).padding(horizontal = 6.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            PanelField(session.query, { session.query = it; onQueryChanged() }, "Procurar", palette, Modifier.weight(1f))
            Text(
                if (session.query.isEmpty()) "" else if (matchCount == 0) "Sem resultados" else (currentMatch + 1).toString() + "/" + matchCount,
                fontSize = 11.sp,
                color = palette.foreground,
                maxLines = 1
            )
            IconButton(onClick = onPrevious, enabled = matchCount > 0, modifier = Modifier.size(34.dp)) { Icon(Icons.Default.KeyboardArrowUp, "Resultado anterior") }
            IconButton(onClick = onNext, enabled = matchCount > 0, modifier = Modifier.size(34.dp)) { Icon(Icons.Default.KeyboardArrowDown, "Próximo resultado") }
            TextButton(onClick = { session.caseSensitive = !session.caseSensitive; onQueryChanged() }, contentPadding = PaddingValues(horizontal = 6.dp), modifier = Modifier.height(34.dp)) {
                Text("Aa", fontWeight = FontWeight.Bold, color = if (session.caseSensitive) MaterialTheme.colorScheme.primary else palette.foreground.copy(alpha = .6f))
            }
            TextButton(onClick = { session.replaceOpen = !session.replaceOpen }, contentPadding = PaddingValues(horizontal = 6.dp), modifier = Modifier.height(34.dp)) { Text("⇄") }
            IconButton(onClick = onClose, modifier = Modifier.size(34.dp)) { Icon(Icons.Default.Close, "Fechar procura") }
        }
        if (session.replaceOpen) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                PanelField(session.replacement, { session.replacement = it }, "Substituir por", palette, Modifier.weight(1f))
                TextButton(onClick = onReplace, enabled = matchCount > 0, contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.height(34.dp)) { Text("Substituir") }
                TextButton(onClick = onReplaceAll, enabled = matchCount > 0, contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.height(34.dp)) { Text("Todos") }
            }
        }
    }
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
