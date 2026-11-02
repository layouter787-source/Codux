package dev.codux

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
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

/** Um ficheiro aberto no editor, com o texto em edição e a posição de rolagem própria. */
@Stable
internal class EditorTab(initial: OpenDocument) {
    var document by mutableStateOf(initial)
    var value by mutableStateOf(TextFieldValue(initial.editedText))
    val scroll = ScrollState(0)
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

/** Cores do editor derivadas da paleta do Codux. */
@Composable
private fun rememberEditorColors(): EditorColors {
    val p = Codux.colors
    return remember(p) {
        EditorColors(
            background = if (p.dark) p.backgroundAlt else Color.White,
            text = if (p.dark) Color(0xFFD4D4D4) else Color(0xFF1F1F1F),
            gutter = p.textSecondary.copy(alpha = .75f),
            tabBar = p.background,
            tabIdle = p.surface,
            statusBar = p.backgroundAlt,
            statusText = p.textSecondary
        )
    }
}

private fun allIndexes(text: String, query: String): List<Int> {
    if (query.isEmpty()) return emptyList()
    val hits = ArrayList<Int>()
    var from = 0
    while (hits.size < 2000) {
        val hit = text.indexOf(query, from, ignoreCase = true)
        if (hit < 0) break
        hits.add(hit)
        from = hit + query.length
    }
    return hits
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditorScreen(
    state: AppState,
    session: EditorSession,
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
    val density = LocalDensity.current
    val palette = Codux.colors
    val dark = palette.dark
    val colors = rememberEditorColors()
    var showExplorer by remember { mutableStateOf(false) }
    var showManage by remember { mutableStateOf(false) }
    var treeRefresh by remember { mutableIntStateOf(0) }
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
    var findOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var currentHit by remember { mutableIntStateOf(-1) }
    val project = state.projects.firstOrNull { it.uri == state.workspaceUri }
    val activeTab = session.active
    val activeName = activeTab?.document?.name
    val language = remember(activeName) { activeName?.let { CodeSyntax.forFileName(it) } }
    val hits = remember(activeTab?.value?.text, query, findOpen) {
        if (!findOpen || activeTab == null) emptyList() else allIndexes(activeTab.value.text, query)
    }

    // Quando o conteúdo da pasta atual muda (criar, renomear, apagar...), a árvore recarrega.
    LaunchedEffect(state.files) { treeRefresh++ }

    fun openFromTree(parent: Uri, file: WorkspaceFile) {
        scope.launch {
            runCatching { projectFiles.read(parent, file.uri, file.name) }
                .onSuccess { session.open(it); showExplorer = false }
                .onFailure { onError(it.message ?: "Não foi possível abrir o ficheiro.") }
        }
    }

    fun openFromList(file: WorkspaceFile) {
        if (file.isDirectory) {
            onList(file.uri)
            return
        }
        val parent = state.currentDirectoryUri ?: return
        openFromTree(parent, file)
        showManage = false
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

    fun jump(forward: Boolean) {
        val tab = session.active ?: return
        if (query.isEmpty() || hits.isEmpty()) return
        val base = if (currentHit >= 0) currentHit else if (forward) tab.value.selection.end - 1 else tab.value.selection.start
        val target = if (forward) (hits.firstOrNull { it > base } ?: hits.first()) else (hits.lastOrNull { it < base } ?: hits.last())
        currentHit = target
        tab.value = tab.value.copy(selection = TextRange(target, target + query.length))
        val line = tab.value.text.substring(0, target).count { it == '\n' }
        val y = (line * with(density) { 21.sp.toPx() } - with(density) { 120.dp.toPx() }).toInt().coerceAtLeast(0)
        scope.launch { tab.scroll.animateScrollTo(y) }
    }

    LaunchedEffect(query, findOpen, session.activeIndex) {
        currentHit = -1
        if (findOpen && query.isNotEmpty()) jump(true)
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 720.dp
        LaunchedEffect(state.workspaceUri, wide) {
            if (session.tabs.isEmpty() && state.workspaceUri != null && !wide) showExplorer = true
        }
        Column(Modifier.fillMaxSize()) {
            CoduxTopBar(
                title = "Editor Codux",
                subtitle = project?.name ?: "Nenhum projeto",
                icon = Icons.Default.Code
            ) {
                if (state.workspaceUri != null) {
                    if (!wide) CoduxIconButton(Icons.Default.Folder, "Explorador de ficheiros", { showExplorer = true })
                    CoduxIconButton(Icons.Default.Search, "Localizar no ficheiro", { findOpen = !findOpen }, enabled = activeTab != null, tint = if (findOpen) palette.accent else palette.textSecondary)
                    CoduxIconButton(Icons.Default.Save, "Guardar", { activeTab?.let { save(it) } }, enabled = activeTab?.dirty == true && !saving, tint = palette.accent)
                    Box {
                        CoduxIconButton(Icons.Default.MoreVert, "Mais opções", { moreMenu = true })
                        DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                            DropdownMenuItem(text = { Text("Ver alterações") }, enabled = activeTab != null, onClick = {
                                moreMenu = false
                                activeTab?.let { diffText = UnifiedDiff.create(it.document.originalText, it.value.text) }
                            })
                            DropdownMenuItem(text = { Text("Fechar ficheiro") }, enabled = activeTab != null, onClick = {
                                moreMenu = false
                                activeTab?.let { if (it.dirty) closeCandidate = it else session.close(it) }
                            })
                            DropdownMenuItem(text = { Text("Gerir ficheiros…") }, onClick = { moreMenu = false; showManage = true })
                        }
                    }
                }
            }
            if (state.workspaceUri == null) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CoduxEmptyState(
                        icon = Icons.Default.Folder,
                        title = "Nenhum projeto aberto",
                        message = "Escolhe um projeto para editar os ficheiros.",
                        actionLabel = "Ver projetos",
                        onAction = onGoProjects
                    )
                }
            } else {
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    if (wide) {
                        ExplorerPanel(
                            projectName = project?.name ?: "Projeto",
                            rootUri = state.workspaceUri,
                            projectFiles = projectFiles,
                            selectedUri = activeTab?.document?.uri,
                            refreshKey = treeRefresh,
                            onOpenFile = ::openFromTree,
                            onManage = { showManage = true },
                            onRefresh = { treeRefresh++ },
                            modifier = Modifier.width(280.dp).fillMaxHeight()
                        )
                        Box(Modifier.width(1.dp).fillMaxHeight().background(palette.border))
                    }
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        Row(Modifier.fillMaxWidth().background(colors.tabBar), verticalAlignment = Alignment.CenterVertically) {
                            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                                session.tabs.forEachIndexed { index, tab ->
                                    EditorTabChip(
                                        tab = tab,
                                        selected = index == session.activeIndex,
                                        colors = colors,
                                        onClick = { session.activeIndex = index },
                                        onClose = { if (tab.dirty) closeCandidate = tab else session.close(tab) }
                                    )
                                }
                            }
                            if (!wide) CoduxIconButton(Icons.Default.Add, "Abrir ficheiro", { showExplorer = true })
                        }
                        Box(Modifier.fillMaxWidth().height(1.dp).background(palette.border))
                        if (findOpen && activeTab != null) {
                            FindBar(
                                query = query,
                                onQuery = { query = it },
                                counter = when {
                                    query.isEmpty() -> ""
                                    hits.isEmpty() -> "0/0"
                                    else -> (if (currentHit >= 0) (hits.indexOf(currentHit) + 1).toString() else "–") + "/" + hits.size
                                },
                                onPrevious = { jump(false) },
                                onNext = { jump(true) },
                                onClose = { findOpen = false }
                            )
                        }
                        if (activeTab == null) {
                            Column(
                                Modifier.weight(1f).fillMaxWidth().background(colors.background),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CoduxEmptyState(
                                    icon = Icons.Default.Code,
                                    title = "Nenhum ficheiro aberto",
                                    message = if (wide) "Escolhe um ficheiro no explorador para começar a editar." else "Abre o explorador para escolher um ficheiro.",
                                    actionLabel = if (wide) null else "Abrir ficheiros",
                                    onAction = { showExplorer = true }
                                )
                            }
                        } else {
                            CodeEditor(
                                tab = activeTab,
                                enabled = !saving,
                                colors = colors,
                                language = language,
                                dark = dark,
                                findQuery = if (findOpen) query else "",
                                currentHit = currentHit,
                                modifier = Modifier.weight(1f).fillMaxWidth()
                            )
                            EditorStatusBar(activeTab, language, colors, palette.border)
                            SymbolBar(activeTab, colors, palette.border) {
                                val value = activeTab.value
                                val selection = value.selection
                                val text = if (!selection.collapsed) value.text.substring(selection.min, selection.max) else value.text
                                clipboard.setText(AnnotatedString(text))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showExplorer) {
        ModalBottomSheet(onDismissRequest = { showExplorer = false }) {
            ExplorerPanel(
                projectName = project?.name ?: "Projeto",
                rootUri = state.workspaceUri,
                projectFiles = projectFiles,
                selectedUri = activeTab?.document?.uri,
                refreshKey = treeRefresh,
                onOpenFile = ::openFromTree,
                onManage = { showExplorer = false; showManage = true },
                onRefresh = { treeRefresh++ },
                modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp)
            )
        }
    }

    if (showManage) {
        ModalBottomSheet(onDismissRequest = { showManage = false; treeRefresh++ }) {
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
                    Text("Pasta vazia.", Modifier.padding(vertical = 18.dp), color = palette.textSecondary)
                } else {
                    LazyColumn(Modifier.heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(state.files, key = { it.uri.toString() }) { file ->
                            ManagedFileRow(
                                file,
                                onClick = { openFromList(file) },
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

// ───────────────────────────── Explorador em árvore ─────────────────────────────

private class TreeNode(val file: WorkspaceFile, val depth: Int, val parent: Uri)

private fun flatten(dir: Uri, depth: Int, children: Map<String, List<WorkspaceFile>>, expanded: Map<String, Boolean>, out: MutableList<TreeNode>) {
    children[dir.toString()]?.forEach { file ->
        out += TreeNode(file, depth, dir)
        if (file.isDirectory && expanded[file.uri.toString()] == true) flatten(file.uri, depth + 1, children, expanded, out)
    }
}

/** Árvore real do projeto: pastas e subpastas expansíveis, carregadas à medida que se abrem. */
@Composable
private fun ExplorerPanel(
    projectName: String,
    rootUri: Uri?,
    projectFiles: ProjectFiles,
    selectedUri: Uri?,
    refreshKey: Int,
    onOpenFile: (Uri, WorkspaceFile) -> Unit,
    onManage: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier
) {
    val colors = Codux.colors
    val scope = rememberCoroutineScope()
    val expanded = remember(rootUri) { mutableStateMapOf<String, Boolean>() }
    val children = remember(rootUri) { mutableStateMapOf<String, List<WorkspaceFile>>() }
    LaunchedEffect(rootUri, refreshKey) {
        val root = rootUri ?: return@LaunchedEffect
        val keys = listOf(root.toString()) + expanded.filterValues { it }.keys
        keys.forEach { key -> children[key] = runCatching { projectFiles.list(Uri.parse(key)) }.getOrDefault(emptyList()) }
    }
    val nodes = ArrayList<TreeNode>().also { out -> rootUri?.let { flatten(it, 0, children, expanded, out) } }
    Column(modifier.background(colors.backgroundAlt)) {
        Row(Modifier.fillMaxWidth().padding(start = CoduxSpace.lg, end = CoduxSpace.xs), verticalAlignment = Alignment.CenterVertically) {
            Text("EXPLORADOR", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
            CoduxIconButton(Icons.Default.Refresh, "Atualizar árvore", onRefresh)
            CoduxIconButton(Icons.Default.Tune, "Gerir ficheiros (criar, renomear, apagar)", onManage)
        }
        Text(
            projectName.uppercase(),
            Modifier.fillMaxWidth().padding(horizontal = CoduxSpace.lg, vertical = CoduxSpace.xs),
            style = MaterialTheme.typography.labelLarge,
            color = colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (rootUri != null && children[rootUri.toString()] == null) {
            Box(Modifier.fillMaxWidth().padding(CoduxSpace.xl), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = colors.accent)
            }
        } else if (nodes.isEmpty()) {
            Text("Pasta vazia.", Modifier.padding(CoduxSpace.lg), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        } else {
            LazyColumn(Modifier.weight(1f, fill = false).fillMaxWidth()) {
                items(nodes, key = { it.file.uri.toString() }) { node ->
                    val key = node.file.uri.toString()
                    val isOpen = expanded[key] == true
                    TreeRow(node, isOpen, selected = node.file.uri == selectedUri) {
                        if (node.file.isDirectory) {
                            expanded[key] = !isOpen
                            if (!isOpen && key !in children) {
                                scope.launch { children[key] = runCatching { projectFiles.list(node.file.uri) }.getOrDefault(emptyList()) }
                            }
                        } else {
                            onOpenFile(node.parent, node.file)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TreeRow(node: TreeNode, expanded: Boolean, selected: Boolean, onClick: () -> Unit) {
    val colors = Codux.colors
    val file = node.file
    val tint = when {
        file.isDirectory -> colors.accent
        CodeSyntax.forFileName(file.name) != null -> languageBadge(file.name).second
        else -> colors.textSecondary
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(38.dp)
            .background(if (selected) colors.accent.copy(alpha = .16f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(start = (8 + node.depth * 16).dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (file.isDirectory) {
            Icon(if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight, null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
        } else {
            Spacer(Modifier.width(18.dp))
        }
        Icon(
            when {
                file.isDirectory && expanded -> Icons.Default.FolderOpen
                file.isDirectory -> Icons.Default.Folder
                CodeSyntax.forFileName(file.name) != null -> Icons.Default.Code
                else -> Icons.Default.Description
            },
            null,
            tint = tint,
            modifier = Modifier.padding(start = 4.dp).size(18.dp)
        )
        Text(file.name, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium, color = if (selected) colors.text else colors.text.copy(alpha = .9f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ───────────────────────────── Edição ─────────────────────────────

private fun languageBadge(name: String): Pair<String, Color> = when (name.substringAfterLast('.', "").lowercase()) {
    "kt", "kts" -> "K" to Color(0xFF8B6CFF)
    "java" -> "J" to Color(0xFFE76F00)
    "cpp", "cc", "cxx", "hpp", "h" -> "c++" to Color(0xFF3B82F6)
    "c" -> "C" to Color(0xFF3B82F6)
    "js", "jsx" -> "JS" to Color(0xFFD9B100)
    "ts", "tsx" -> "TS" to Color(0xFF3B8EDD)
    "py" -> "PY" to Color(0xFF4B8BBE)
    "xml" -> "XML" to Color(0xFF22C983)
    "json" -> "{}" to Color(0xFFC9B458)
    "html" -> "HTML" to Color(0xFFE34F26)
    "css" -> "CSS" to Color(0xFF3B9BE0)
    "md" -> "MD" to Color(0xFF94A3B8)
    else -> "TXT" to Color(0xFF94A3B8)
}

/** Separador plano: o ativo funde-se com o editor e leva uma linha de realce por baixo. */
@Composable
private fun EditorTabChip(tab: EditorTab, selected: Boolean, colors: EditorColors, onClick: () -> Unit, onClose: () -> Unit) {
    val (badge, badgeColor) = languageBadge(tab.document.name)
    Column(
        Modifier
            .height(40.dp)
            .background(if (selected) colors.background else colors.tabIdle)
            .clickable(onClick = onClick)
    ) {
        Row(Modifier.weight(1f).padding(start = 12.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(badge, color = badgeColor, fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
            Text(
                (if (tab.dirty) "● " else "") + tab.document.name,
                Modifier.padding(horizontal = 8.dp),
                color = if (selected) colors.text else colors.gutter,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(Icons.Default.Close, "Fechar " + tab.document.name, Modifier.size(16.dp).clickable(onClick = onClose), tint = colors.gutter)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
        )
    }
}

@Composable
private fun FindBar(query: String, onQuery: (String) -> Unit, counter: String, onPrevious: () -> Unit, onNext: () -> Unit, onClose: () -> Unit) {
    val colors = Codux.colors
    val focusRequester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Row(
        Modifier.fillMaxWidth().background(colors.backgroundAlt).padding(horizontal = CoduxSpace.md, vertical = CoduxSpace.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier
                .weight(1f)
                .height(38.dp)
                .clip(CoduxShape.small)
                .background(colors.surface)
                .border(1.dp, if (focused) colors.accent else colors.border, CoduxShape.small)
                .padding(horizontal = CoduxSpace.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.text, fontFamily = FontFamily.Monospace),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrect = false, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onNext() }),
                modifier = Modifier.weight(1f).focusRequester(focusRequester).onFocusChanged { focused = it.isFocused },
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) Text("Localizar…", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                        inner()
                    }
                }
            )
            Text(counter, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
        }
        CoduxIconButton(Icons.Default.KeyboardArrowUp, "Resultado anterior", onPrevious)
        CoduxIconButton(Icons.Default.KeyboardArrowDown, "Próximo resultado", onNext)
        CoduxIconButton(Icons.Default.Close, "Fechar pesquisa", onClose)
    }
}

/** Realça no texto todas as ocorrências da pesquisa; a ocorrência atual tem um realce mais forte. */
private class FindTransformation(
    private val base: VisualTransformation,
    private val query: String,
    private val current: Int,
    private val match: Color,
    private val currentMatch: Color
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val transformed = base.filter(text)
        if (query.isEmpty()) return transformed
        val source = text.text
        val styled = buildAnnotatedString {
            append(transformed.text)
            var from = 0
            var count = 0
            while (count < 500) {
                val hit = source.indexOf(query, from, ignoreCase = true)
                if (hit < 0) break
                addStyle(SpanStyle(background = if (hit == current) currentMatch else match), hit, hit + query.length)
                from = hit + query.length
                count++
            }
        }
        return TransformedText(styled, transformed.offsetMapping)
    }
}

@Composable
private fun CodeEditor(
    tab: EditorTab,
    enabled: Boolean,
    colors: EditorColors,
    language: SyntaxLanguage?,
    dark: Boolean,
    findQuery: String,
    currentHit: Int,
    modifier: Modifier
) {
    val p = Codux.colors
    val palette = if (dark) DarkSyntaxPalette else LightSyntaxPalette
    val base = remember(language, dark) { SyntaxTransformation(language, palette) }
    val matchColor = p.warning.copy(alpha = .28f)
    val currentColor = p.warning.copy(alpha = .6f)
    val transformation = remember(base, findQuery, currentHit, matchColor) { FindTransformation(base, findQuery, currentHit, matchColor, currentColor) }
    val textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 21.sp, color = colors.text)
    val lineCount = remember(tab.value.text) { tab.value.text.count { it == '\n' } + 1 }
    val gutter = remember(lineCount) { (1..lineCount).joinToString("\n") }
    val gutterWidth = (lineCount.toString().length * 9 + 14).dp
    val horizontalScroll = rememberScrollState()
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val cursor = tab.value.selection.end
    val lineHighlight = p.accent.copy(alpha = .08f)
    BoxWithConstraints(modifier.background(colors.background)) {
        val viewportHeight = maxHeight
        Row(Modifier.fillMaxSize().verticalScroll(tab.scroll)) {
            Text(
                gutter,
                style = textStyle.copy(color = colors.gutter, textAlign = TextAlign.End),
                softWrap = false,
                modifier = Modifier.width(gutterWidth).heightIn(min = viewportHeight).padding(start = 4.dp, end = 8.dp, top = 12.dp)
            )
            BoxWithConstraints(Modifier.weight(1f)) {
                val viewportWidth = maxWidth
                Box(Modifier.horizontalScroll(horizontalScroll)) {
                    BasicTextField(
                        value = tab.value,
                        onValueChange = { tab.value = applyAutoIndent(tab.value, it, language?.colonIndent == true) },
                        enabled = enabled,
                        textStyle = textStyle,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        visualTransformation = transformation,
                        onTextLayout = { layout = it },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrect = false),
                        modifier = Modifier
                            .widthIn(min = viewportWidth)
                            .heightIn(min = viewportHeight)
                            .padding(top = 12.dp, end = 24.dp)
                            .drawBehind {
                                val result = layout
                                if (result != null) {
                                    val length = result.layoutInput.text.length
                                    val line = result.getLineForOffset(cursor.coerceIn(0, length))
                                    val top = result.getLineTop(line)
                                    drawRect(lineHighlight, Offset(0f, top), Size(size.width, result.getLineBottom(line) - top))
                                }
                            }
                    )
                }
            }
        }
    }
}

/** Barra de estado: linha/coluna, codificação, linguagem, indentação e estado do ficheiro. */
@Composable
private fun EditorStatusBar(tab: EditorTab, language: SyntaxLanguage?, colors: EditorColors, border: Color) {
    val text = tab.value.text
    val cursor = tab.value.selection.end.coerceIn(0, text.length)
    val position = remember(text, cursor) {
        var line = 1
        var lastBreak = -1
        for (index in 0 until cursor) {
            if (text[index] == '\n') {
                line++
                lastBreak = index
            }
        }
        line to (cursor - lastBreak)
    }
    Column(Modifier.fillMaxWidth().background(colors.statusBar)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(border))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Ln " + position.first + ", Col " + position.second, color = colors.statusText, fontSize = 11.sp, maxLines = 1)
            Text("UTF-8", color = colors.statusText, fontSize = 11.sp, maxLines = 1)
            Text(language?.label ?: "Texto simples", color = colors.statusText, fontSize = 11.sp, maxLines = 1)
            Text("Espaços: 4", color = colors.statusText, fontSize = 11.sp, maxLines = 1)
            Text(if (tab.dirty) "● por guardar" else "✓ guardado", color = if (tab.dirty) Codux.colors.warning else Codux.colors.success, fontSize = 11.sp, maxLines = 1)
        }
    }
}

@Composable
private fun SymbolBar(tab: EditorTab, colors: EditorColors, border: Color, onCopy: () -> Unit) {
    val symbols = listOf("{", "}", "(", ")", "[", "]", ";", "=", ".", ",", "/", "\"", "'", "<", ">", ":", "!", "&", "|", "+", "-", "*", "_", "#", "\\")
    Column(Modifier.fillMaxWidth().background(colors.tabBar)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(border))
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            SymbolKey("Tab", colors.text) { tab.value = insertText(tab.value, "    ") }
            SymbolKey("//", colors.text) { tab.value = toggleComment(tab.value, commentPrefix(tab.document.name)) }
            SymbolKey("↑", colors.text) { tab.value = moveLine(tab.value, -1) }
            SymbolKey("↓", colors.text) { tab.value = moveLine(tab.value, 1) }
            IconButton(onClick = onCopy, modifier = Modifier.size(42.dp)) { Icon(Icons.Default.ContentCopy, "Copiar", tint = colors.text, modifier = Modifier.size(20.dp)) }
            Box(Modifier.width(1.dp).height(26.dp).background(colors.gutter))
            symbols.forEach { symbol -> SymbolKey(symbol, colors.text) { tab.value = insertText(tab.value, symbol) } }
        }
    }
}

@Composable
private fun SymbolKey(label: String, tint: Color, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 10.dp), modifier = Modifier.height(42.dp).widthIn(min = 38.dp)) {
        Text(label, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = tint)
    }
}

private fun insertText(value: TextFieldValue, insert: String): TextFieldValue {
    val start = value.selection.min
    val end = value.selection.max
    val newText = value.text.replaceRange(start, end, insert)
    return value.copy(text = newText, selection = TextRange(start + insert.length))
}

/**
 * Ao carregar em Enter, a nova linha herda a indentação da anterior e entra mais um nível
 * depois de "{", "(" ou "[" (e de ":" em Python). Qualquer outra edição passa sem alterações.
 */
private fun applyAutoIndent(old: TextFieldValue, new: TextFieldValue, colonIndent: Boolean): TextFieldValue {
    if (new.text.length != old.text.length + 1 || !new.selection.collapsed) return new
    val cursor = new.selection.start
    if (cursor < 1 || new.text[cursor - 1] != '\n') return new
    val lineStart = new.text.lastIndexOf('\n', cursor - 2) + 1
    val previous = new.text.substring(lineStart, cursor - 1)
    val indent = previous.takeWhile { it == ' ' || it == '\t' }
    val trimmed = previous.trimEnd()
    val opensBlock = trimmed.endsWith("{") || trimmed.endsWith("(") || trimmed.endsWith("[") || (colonIndent && trimmed.endsWith(":"))
    val insert = indent + (if (opensBlock) "    " else "")
    if (insert.isEmpty()) return new
    val text = new.text.substring(0, cursor) + insert + new.text.substring(cursor)
    return new.copy(text = text, selection = TextRange(cursor + insert.length))
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
    val colors = Codux.colors
    var menuOpen by remember { mutableStateOf(false) }
    Surface(
        shape = CoduxShape.medium,
        color = colors.surface,
        border = BorderStroke(1.dp, colors.border),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (file.isDirectory) Icons.Default.Folder else Icons.Default.Code, null, tint = colors.accent, modifier = Modifier.size(21.dp))
            Column(Modifier.weight(1f).padding(horizontal = 11.dp)) {
                Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = colors.text)
                val metadata = buildList {
                    add(if (file.isDirectory) "Pasta" else (file.size?.let { it.toString() + " B" } ?: "Ficheiro"))
                    file.lastModified?.let { add(SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(it))) }
                }.joinToString(" · ")
                Text(metadata, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
            }
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "Ações de " + file.name, tint = colors.textSecondary) }
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
