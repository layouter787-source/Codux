package dev.codux

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.codux.data.AppState
import dev.codux.data.ChatMessage
import dev.codux.data.CodexThread
import dev.codux.runtime.EmbeddedTermuxRuntime
import dev.codux.runtime.EmbeddedTermuxTerminal
import dev.codux.data.CoduxViewModel
import dev.codux.data.MessageRole
import dev.codux.data.ProjectWorkspace
import dev.codux.data.WorkspaceFile
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import java.text.SimpleDateFormat
import java.util.Date

private data class TextAttachment(val name: String, val content: String)

private enum class Destination(val label: String) { CHAT("Chat"), PROJECTS("Projetos"), TERMINAL("Terminal"), SETTINGS("Definições") }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CoduxApp() }
    }
}

@Composable
private fun CoduxTheme(mode: String, accent: String, textScale: Float, content: @Composable () -> Unit) {
    val dark = when (mode) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    val accentColor = when (accent) {
        "violet" -> Color(0xFF8164D8)
        "mint" -> Color(0xFF149B83)
        "orange" -> Color(0xFFE18036)
        "rose" -> Color(0xFFD95782)
        else -> Color(0xFF2878E5)
    }
    val colors = if (dark) darkColorScheme(
        primary = accentColor, onPrimary = Color.White, primaryContainer = accentColor.copy(alpha = .24f),
        background = Color(0xFF151922), surface = Color(0xFF191F2A), surfaceVariant = Color(0xFF252D3A),
        onSurface = Color(0xFFE8ECF3), onBackground = Color(0xFFE8ECF3), outline = Color(0xFF3A4658)
    ) else lightColorScheme(
        primary = accentColor, onPrimary = Color.White, primaryContainer = accentColor.copy(alpha = .12f),
        background = Color(0xFFF7F9FC), surface = Color(0xFFFFFFFF), surfaceVariant = Color(0xFFF0F4FA),
        onSurface = Color(0xFF172033), onBackground = Color(0xFF172033), outline = Color(0xFFE1E7F0)
    )
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * textScale.coerceIn(.85f, 1.25f))) {
        MaterialTheme(colorScheme = colors, content = content)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CoduxApp(vm: CoduxViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val view = LocalView.current
    val context = LocalContext.current
    var destination by remember { mutableStateOf(Destination.CHAT) }
    var prompt by remember { mutableStateOf("") }
    var terminalDraft by remember { mutableStateOf("") }
    var editorText by remember(state.openDocument?.uri) { mutableStateOf(state.openDocument?.editedText.orEmpty()) }
    var showDiff by remember { mutableStateOf(false) }
    var showDiscardDocument by remember { mutableStateOf(false) }
    var showProjectDetails by remember { mutableStateOf(false) }
    var projectSearch by remember { mutableStateOf("") }
    var attachment by remember { mutableStateOf<TextAttachment?>(null) }
    var showHelp by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let { vm.selectWorkspaceTree(it); showProjectDetails = true } }
    val attachLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val input = context.contentResolver.openInputStream(uri) ?: error("Não foi possível ler este ficheiro.")
                    val bytes = input.use { stream ->
                        val buffer = java.io.ByteArrayOutputStream()
                        val chunk = ByteArray(8192)
                        var total = 0
                        while (true) {
                            val count = stream.read(chunk)
                            if (count < 0) break
                            total += count
                            require(total <= 2 * 1024 * 1024) { "O limite para anexos de texto é 2 MB." }
                            buffer.write(chunk, 0, count)
                        }
                        buffer.toByteArray()
                    }
                    val text = Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString()
                    TextAttachment(uri.lastPathSegment?.substringAfterLast('/') ?: "ficheiro", text)
                }
            }.onSuccess { attachment = it }.onFailure { vm.showError(it.message ?: "Não foi possível anexar o ficheiro.") }
        }
    }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::importProjectFile) }
    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val words = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!words.isNullOrBlank()) prompt = listOf(prompt, words).filter(String::isNotBlank).joinToString(" ")
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.connectToTermux() }
    val connectToTermux = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        else vm.connectToTermux()
    }
    LaunchedEffect(Unit) { vm.restoreWorkspaceTree() }
    val requestCloseDocument = {
        if (!state.isSaving) {
            val document = state.openDocument
            if (document != null && editorText != document.originalText) showDiscardDocument = true else vm.closeDocument()
        }
    }
    if (destination == Destination.PROJECTS && state.openDocument != null) BackHandler { requestCloseDocument() }
    else if (destination == Destination.PROJECTS && showProjectDetails) BackHandler { showProjectDetails = false }
    val darkSystemBars = when (state.themeMode) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    CoduxTheme(state.themeMode, state.accentColor, state.textScale) {
        SideEffect {
            val window = (view.context as? ComponentActivity)?.window ?: return@SideEffect
            val dark = darkSystemBars
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
        ModalNavigationDrawer(drawerState = drawerState, drawerContent = {
            ModalDrawerSheet(modifier = Modifier.widthIn(max = 340.dp)) {
                Column(Modifier.fillMaxSize().padding(18.dp)) {
                    Text("codux", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                    Text("O teu espaço de desenvolvimento", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f))
                    Spacer(Modifier.height(22.dp))
                    Button(onClick = { vm.createThread(); destination = Destination.CHAT; scope.launch { drawerState.close() } }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Nova conversa")
                    }
                    DrawerDestinationRow("Projetos", Icons.Default.Folder, destination == Destination.PROJECTS) { destination = Destination.PROJECTS; showProjectDetails = false; scope.launch { drawerState.close() } }
                    DrawerDestinationRow("Terminal", Icons.Default.Terminal, destination == Destination.TERMINAL) { destination = Destination.TERMINAL; scope.launch { drawerState.close() } }
                    DrawerDestinationRow("Definições", Icons.Default.Tune, destination == Destination.SETTINGS) { destination = Destination.SETTINGS; scope.launch { drawerState.close() } }
                    DrawerDestinationRow("Ajuda", Icons.Default.HelpOutline, false) { showHelp = true; scope.launch { drawerState.close() } }
                    DrawerDestinationRow("Sobre", Icons.Default.Info, false) { showAbout = true; scope.launch { drawerState.close() } }
                    Spacer(Modifier.height(16.dp)); Text("Conversas recentes", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f))
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        items(state.threads, key = { it.id }) { thread ->
                            ThreadRow(thread, state.activeThreadId == thread.id) { vm.openThread(thread); destination = Destination.CHAT; scope.launch { drawerState.close() } }
                        }
                        if (state.threadCursor != null) item {
                            TextButton(onClick = vm::loadMoreThreads, enabled = !state.isLoadingThreads, modifier = Modifier.fillMaxWidth()) {
                                Text(if (state.isLoadingThreads) "A carregar…" else "Carregar conversas anteriores")
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f)); Divider()
                }
            }
        }) {
            Scaffold(
                topBar = {
                    TopAppBar(colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background.copy(alpha = 1f - state.transparency * .15f)), title = {
                        Column {
                            Text(if (destination == Destination.CHAT) state.activeThreadName else destination.label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(7.dp).clip(CircleShape).background(if (state.connected) Color(0xFF38A87A) else MaterialTheme.colorScheme.outline))
                                Spacer(Modifier.width(6.dp)); Text(if (state.connected) "Codex ligado" else "Desligado", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f))
                            }
                        }
                    }, navigationIcon = { IconButton(onClick = { scope.launch { drawerState.open() } }) { Icon(Icons.Default.Menu, "Histórico") } }, actions = {
                        if (!state.connected) TextButton(onClick = connectToTermux, enabled = !state.connecting) { Text(if (state.connecting) "A ligar…" else "Ligar") }
                        else IconButton(onClick = vm::disconnect) { Icon(Icons.Default.Close, "Desligar") }
                    })
                },
                bottomBar = {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 1f - state.transparency * .15f), tonalElevation = 0.dp) {
                        Destination.values().forEach { page ->
                            val icon = when (page) { Destination.CHAT -> Icons.Default.ChatBubbleOutline; Destination.PROJECTS -> Icons.Default.Folder; Destination.TERMINAL -> Icons.Default.Terminal; Destination.SETTINGS -> Icons.Default.Tune }
                            NavigationBarItem(selected = destination == page, onClick = { destination = page }, icon = { Icon(icon, page.label) }, label = { Text(page.label) })
                        }
                    }
                }
            ) { padding ->
                Column(Modifier.fillMaxSize().padding(padding)) {
                    state.error?.let { ErrorBanner(it, vm::clearError) }
                    AnimatedContent(targetState = destination, label = "navigation") { page ->
                        when (page) {
                        Destination.CHAT -> ChatScreen(state, prompt, { prompt = it }, {
                            val attached = attachment
                            val message = if (attached == null) prompt else prompt + "\n\nFicheiro: " + attached.name + "\n\n" + attached.content
                            vm.sendMessage(message); prompt = ""; attachment = null
                        }, vm::cancelTurn, { attachLauncher.launch(arrayOf("text/*", "application/json", "application/xml", "application/javascript")) }, attachment?.name, { attachment = null }, {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                            runCatching { speechLauncher.launch(intent) }
                                .onFailure { vm.showError("O ditado por voz não está disponível neste dispositivo.") }
                        }, { showDiff = true })
                        Destination.PROJECTS -> ProjectsScreen(
                            state = state, editorText = editorText, onEditorText = { editorText = it; vm.editDocument(it) },
                            showDetails = showProjectDetails, search = projectSearch, onSearch = { projectSearch = it },
                            onPick = { folderPicker.launch(null) }, onProjectClick = { vm.openProject(it); showProjectDetails = true },
                            onProjectBack = { showProjectDetails = false }, onRemoveProject = vm::removeProject,
                            onCreateItem = vm::createProjectItem, onRename = vm::renameProjectItem, onDelete = vm::deleteProjectItem,
                            onImport = { importPicker.launch(arrayOf("*/*")) }, onCopy = vm::copyProjectItem, onMove = vm::moveProjectItem,
                            onOpen = vm::openFile, onUp = vm::navigateUp, onClose = requestCloseDocument,
                            onSave = vm::saveDocument, onDiff = { vm.previewDocumentDiff(); showDiff = true },
                            terminalCommand = terminalDraft, onTerminalCommand = { terminalDraft = it },
                            onRunCommand = vm::startTerminalCommand, onCancelCommand = vm::cancelTerminal, onTerminalInput = vm::sendTerminalInput
                        )
                        Destination.TERMINAL -> TerminalScreen(state, terminalDraft, { terminalDraft = it }, vm::startTerminalCommand, vm::cancelTerminal, vm::sendTerminalInput)
                        Destination.SETTINGS -> SettingsScreen(state, vm::setThemeMode, vm::setAccentColor, vm::setTextScale, vm::setTransparency, vm::setWorkspacePath, vm::setTerminalTimeout, connectToTermux, vm::disconnect)
                        }
                    }
                }
            }
        }
        if (showHelp) AlertDialog(onDismissRequest = { showHelp = false }, title = { Text("Ajuda") },
            text = { Text("Escolhe uma pasta em Projetos, configura o caminho correspondente no ambiente Codex e liga o App Server nas Definições.") },
            confirmButton = { TextButton(onClick = { showHelp = false }) { Text("Fechar") } })
        if (showAbout) AlertDialog(onDismissRequest = { showAbout = false }, title = { Text("Codux") },
            text = { Text("Cliente Android de desenvolvimento com IA, construído com Jetpack Compose Material 3.") },
            confirmButton = { TextButton(onClick = { showAbout = false }) { Text("Fechar") } })
        state.pendingApproval?.let { approval ->
            val permissionRequest = approval.method == "item/permissions/requestApproval"
            val offered = if (permissionRequest) listOf("accept", "acceptForSession", "decline")
                else approval.availableDecisions.filter { it in setOf("accept", "acceptForSession", "decline", "cancel") }
            AlertDialog(onDismissRequest = {}, title = { Text(approval.title) }, text = { Text(approval.description) },
                confirmButton = {
                    if ("accept" in offered) TextButton(onClick = { vm.answerApproval("accept") }) { Text(if (permissionRequest) "Permitir" else "Aprovar") }
                },
                dismissButton = { Row {
                    if ("acceptForSession" in offered) TextButton(onClick = { vm.answerApproval("acceptForSession") }) { Text(if (permissionRequest) "Permitir sessão" else "Nesta sessão") }
                    if ("decline" in offered) TextButton(onClick = { vm.answerApproval("decline") }) { Text("Recusar") }
                    if ("cancel" in offered) TextButton(onClick = { vm.answerApproval("cancel") }) { Text("Cancelar") }
                } })
        }
        if (showDiscardDocument) {
            AlertDialog(onDismissRequest = { showDiscardDocument = false }, title = { Text("Descartar alterações?") },
                text = { Text("As alterações ainda não foram guardadas.") },
                confirmButton = { TextButton(onClick = { showDiscardDocument = false; vm.closeDocument() }, enabled = !state.isSaving) { Text("Descartar") } },
                dismissButton = { TextButton(onClick = { showDiscardDocument = false }) { Text("Continuar a editar") } })
        }
        if (showDiff) {
            val diff = state.diffPreview ?: state.currentDiff
            AlertDialog(onDismissRequest = { showDiff = false }, title = { Text("Alterações") },
                text = { LazyColumn(Modifier.heightIn(max = 520.dp)) { item { Text(diff.ifBlank { "Ainda não há alterações para mostrar." }, style = MaterialTheme.typography.bodySmall) } } },
                confirmButton = { TextButton(onClick = { showDiff = false }) { Text("Fechar") } })
        }
        if (state.showTerminalConfirmation) {
            AlertDialog(onDismissRequest = vm::dismissTerminalConfirmation, title = { Text("Executar comando no ambiente integrado?") },
                text = { Text("O comando corre no diretório de trabalho fora do sandbox do Codex. Confirma apenas comandos que reconheces.\n\n${state.terminalCommand}") },
                confirmButton = { TextButton(onClick = vm::confirmTerminalCommand) { Text("Executar") } },
                dismissButton = { TextButton(onClick = vm::dismissTerminalConfirmation) { Text("Cancelar") } })
        }
    }
}

@Composable
private fun DrawerDestinationRow(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(13.dp), color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).clickable(onClick = onClick)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = .72f), modifier = Modifier.size(20.dp))
            Text(label, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ThreadRow(thread: CodexThread, selected: Boolean, onClick: () -> Unit) {
    Surface(modifier = Modifier.clickable(onClick = onClick), shape = RoundedCornerShape(12.dp), color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(thread.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            if (thread.preview.isNotBlank()) Text(thread.preview, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
        }
    }
}

@Composable
private fun ChatScreen(state: AppState, prompt: String, onPrompt: (String) -> Unit, onSend: () -> Unit, onStop: () -> Unit, onAttach: () -> Unit, attachmentName: String?, onRemoveAttachment: () -> Unit, onVoice: () -> Unit, onShowDiff: () -> Unit) {
    val messageListState = rememberLazyListState()
    LaunchedEffect(state.messages.lastOrNull()?.id, state.messages.lastOrNull()?.text, state.messages.size, state.isTurnRunning) {
        val target = if (state.isTurnRunning && state.messages.lastOrNull()?.isStreaming != true) state.messages.size else state.messages.lastIndex
        if (state.messages.isNotEmpty() && target >= 0) messageListState.scrollToItem(target)
    }
    Column(Modifier.fillMaxSize()) {
        if (state.messages.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Olá,\nComo posso ajudar hoje?", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp)); Text("Trabalha no teu projeto com o Codex.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f))
                Spacer(Modifier.height(24.dp))
                Column(Modifier.widthIn(max = 560.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Explica a estrutura deste projeto" to Icons.Default.Folder, "Ajuda-me a corrigir um erro" to Icons.Default.BugReport, "Implementa uma funcionalidade" to Icons.Default.Build).forEach { (suggestion, icon) ->
                        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().animateContentSize().clickable { onPrompt(suggestion) }, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .45f))) {
                            Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(icon, null, tint = MaterialTheme.colorScheme.primary.copy(alpha = .86f), modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(12.dp)); Text(suggestion, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(state = messageListState, modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(vertical = 22.dp), verticalArrangement = Arrangement.spacedBy(26.dp)) {
                items(state.messages, key = { it.id }) { message ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Box(Modifier.widthIn(max = 760.dp).fillMaxWidth().padding(horizontal = 20.dp)) { MessageRow(message) }
                    }
                }
                if (state.isTurnRunning && state.messages.lastOrNull()?.isStreaming != true) item {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("A pensar…", Modifier.widthIn(max = 760.dp).fillMaxWidth().padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if (state.currentDiff.isNotBlank()) TextButton(onClick = onShowDiff, modifier = Modifier.align(Alignment.CenterHorizontally)) { Icon(Icons.Default.Code, null); Text("Ver alterações") }
        Column(Modifier.widthIn(max = 800.dp).fillMaxWidth().align(Alignment.CenterHorizontally).padding(horizontal = 12.dp, vertical = 8.dp)) {
            Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(26.dp), modifier = Modifier.fillMaxWidth().animateContentSize(), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .75f))) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    if (attachmentName != null) Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AttachFile, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Text(attachmentName, Modifier.weight(1f).padding(start = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                        IconButton(onClick = onRemoveAttachment, modifier = Modifier.size(34.dp)) { Icon(Icons.Default.Close, "Remover anexo", modifier = Modifier.size(16.dp)) }
                    }
                    BasicTextField(value = prompt, onValueChange = onPrompt, modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp).heightIn(min = 36.dp, max = 150.dp),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface), cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { if (prompt.isNotBlank() && state.connected && !state.isTurnRunning) onSend() }),
                        decorationBox = { inner -> Box { if (prompt.isBlank()) Text("Mensagem para o Codex", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .45f)); inner() } })
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onAttach, modifier = Modifier.size(38.dp)) { Icon(Icons.Default.AttachFile, "Anexar ficheiro", modifier = Modifier.size(20.dp)) }
                            IconButton(onClick = onVoice, modifier = Modifier.size(38.dp)) { Icon(Icons.Default.Mic, "Ditado por voz", modifier = Modifier.size(20.dp)) }
                        }
                        FilledIconButton(onClick = if (state.isTurnRunning) onStop else onSend, enabled = if (state.isTurnRunning) state.activeTurnId != null else ((prompt.isNotBlank() || attachmentName != null) && state.connected), modifier = Modifier.size(38.dp)) {
                            Icon(if (state.isTurnRunning) Icons.Default.Stop else Icons.AutoMirrored.Filled.Send, if (state.isTurnRunning) "Parar" else "Enviar")
                        }
                    }
                }
            }
            Text("O Codex pode cometer erros. Verifica as alterações antes de as guardar.", Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp, bottom = 2.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f))
        }
    }
}

@Composable
private fun MessageRow(message: ChatMessage) {
    when (message.role) {
        MessageRole.USER -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(20.dp), modifier = Modifier.widthIn(max = 540.dp)) { Text(message.text, Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) }
        }
        MessageRole.ASSISTANT -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            Box(Modifier.padding(top = 5.dp).size(26.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) { Text("C", style = MaterialTheme.typography.labelMedium, color = Color.White, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(12.dp)); MarkdownContent(message.text, Modifier.weight(1f).padding(top = 3.dp))
        }
        MessageRole.SYSTEM -> Text(message.text, Modifier.fillMaxWidth().padding(horizontal = 40.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f))
    }
}

@Composable
private fun MarkdownContent(text: String, modifier: Modifier = Modifier) {
    val fence = Char(96).toString().repeat(3)
    val parts = remember(text) { text.split(fence) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        parts.forEachIndexed { index, part ->
            if (index % 2 == 0) {
                part.lineSequence().forEach { line ->
                    when {
                        line.startsWith("### ") -> Text(inlineMarkdown(line.drop(4)), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        line.startsWith("## ") -> Text(inlineMarkdown(line.drop(3)), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        line.startsWith("# ") -> Text(inlineMarkdown(line.drop(2)), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        line.startsWith("- ") || line.startsWith("* ") -> Row {
                            Text("•", Modifier.padding(end = 8.dp), color = MaterialTheme.colorScheme.primary)
                            Text(inlineMarkdown(line.drop(2)), style = MaterialTheme.typography.bodyLarge)
                        }
                        line.isNotBlank() -> Text(inlineMarkdown(line), style = MaterialTheme.typography.bodyLarge, lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.2f)
                        else -> Spacer(Modifier.height(4.dp))
                    }
                }
            } else {
                val lineBreak = part.indexOf('\n')
                val language = if (lineBreak >= 0) part.substring(0, lineBreak).trim() else ""
                val code = if (lineBreak >= 0) part.substring(lineBreak + 1).trimEnd() else part
                CodeBlock(code, language)
            }
        }
    }
}

private fun inlineMarkdown(text: String): AnnotatedString {
    val tick = Char(96)
    val tokenPattern = Regex("\\*\\*[^*]+\\*\\*|" + tick + "[^" + tick + "]+" + tick)
    return buildAnnotatedString {
        var cursor = 0
        tokenPattern.findAll(text).forEach { match ->
            append(text.substring(cursor, match.range.first))
            val value = match.value
            if (value.startsWith("**")) withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                append(value.removePrefix("**").removeSuffix("**"))
            } else withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontStyle = FontStyle.Italic)) {
                append(value.drop(1).dropLast(1))
            }
            cursor = match.range.last + 1
        }
        append(text.substring(cursor))
    }
}

@Composable
private fun CodeBlock(code: String, language: String) {
    val clipboard = LocalClipboardManager.current
    var expanded by remember(code) { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(15.dp), color = if (isSystemInDarkTheme()) Color(0xFF111722) else Color(0xFFF0F3F8),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .35f))) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(language.ifBlank { "Código" }, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f))
                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Recolher" else "Expandir") }
                IconButton(onClick = { clipboard.setText(AnnotatedString(code)) }, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.ContentCopy, "Copiar código", modifier = Modifier.size(16.dp)) }
            }
            Text(code, Modifier.fillMaxWidth().heightIn(max = if (expanded) 620.dp else 220.dp).verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
                fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun ProjectsScreen(
    state: AppState,
    editorText: String,
    onEditorText: (String) -> Unit,
    showDetails: Boolean,
    search: String,
    onSearch: (String) -> Unit,
    onPick: () -> Unit,
    onProjectClick: (ProjectWorkspace) -> Unit,
    onProjectBack: () -> Unit,
    onRemoveProject: (ProjectWorkspace) -> Unit,
    onCreateItem: (String, Boolean) -> Unit,
    onRename: (WorkspaceFile, String) -> Unit,
    onDelete: (WorkspaceFile) -> Unit,
    onImport: () -> Unit,
    onCopy: (WorkspaceFile) -> Unit,
    onMove: (WorkspaceFile, android.net.Uri) -> Unit,
    onOpen: (WorkspaceFile) -> Unit,
    onUp: () -> Unit,
    onClose: () -> Unit,
    onSave: () -> Unit,
    onDiff: () -> Unit,
    terminalCommand: String,
    onTerminalCommand: (String) -> Unit,
    onRunCommand: (String) -> Unit,
    onCancelCommand: () -> Unit,
    onTerminalInput: (String, Boolean) -> Unit
) {
    var tab by remember(state.workspaceUri) { mutableIntStateOf(0) }
    var createKind by remember { mutableStateOf<Boolean?>(null) }
    var createName by remember { mutableStateOf("") }
    var renameFile by remember { mutableStateOf<WorkspaceFile?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteFile by remember { mutableStateOf<WorkspaceFile?>(null) }
    var moveFile by remember { mutableStateOf<WorkspaceFile?>(null) }

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 10.dp)) {
        if (state.openDocument != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose, enabled = !state.isSaving) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
                Column(Modifier.weight(1f)) {
                    Text(state.openDocument.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                    Text("Pré-visualização de texto", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f))
                }
                TextButton(onClick = onDiff) { Text("Diff") }
                TextButton(onClick = onSave, enabled = !state.isSaving && editorText != state.openDocument.originalText) { Text(if (state.isSaving) "A guardar…" else "Guardar") }
            }
            OutlinedTextField(value = editorText, onValueChange = onEditorText, modifier = Modifier.fillMaxSize().padding(top = 8.dp), enabled = !state.isSaving,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrect = false))
        } else if (!showDetails || state.workspaceUri == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Projetos", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text("Os teus espaços de trabalho", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f))
                }
                FilledIconButton(onClick = onPick, modifier = Modifier.size(46.dp)) { Icon(Icons.Default.Add, "Novo projeto") }
            }
            OutlinedTextField(value = search, onValueChange = onSearch, modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 14.dp),
                singleLine = true, shape = RoundedCornerShape(18.dp), placeholder = { Text("Pesquisar projetos…") }, leadingIcon = { Icon(Icons.Default.Search, null) })
            if (state.projects.isEmpty()) {
                EmptyStateCard(title = "Começa por um projeto", message = "Escolhe uma pasta para dar ao Codux acesso aos ficheiros.", action = "Novo projeto", onAction = onPick)
            } else {
                Text("Meus projetos  ·  " + state.projects.size, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(state.projects.filter { it.name.contains(search, true) || it.termuxPath.contains(search, true) }, key = { it.uri.toString() }) { project ->
                        ProjectCard(project, onClick = { onProjectClick(project) }, onRemove = { onRemoveProject(project) })
                    }
                }
            }
        } else {
            val project = state.projects.firstOrNull { it.uri == state.workspaceUri }
            val technologies = remember(state.files) {
                buildList {
                    val names = state.files.map { it.name.lowercase() }
                    if (names.any { it.endsWith(".kt") || it.endsWith(".kts") || it == "gradlew" }) add("Kotlin / Gradle")
                    if ("package.json" in names) add("Node.js")
                    if (names.any { it.endsWith(".py") || it == "pyproject.toml" }) add("Python")
                    if (names.any { it.endsWith(".java") || it == "androidmanifest.xml" }) add("Android")
                    if (isEmpty()) add("Ainda sem deteção")
                }
            }
            val newestFile = state.files.filter { !it.isDirectory }.maxByOrNull { it.lastModified ?: 0L }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onProjectBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar aos projetos") }
                Column(Modifier.weight(1f)) {
                    Text(project?.name ?: "Projeto", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(project?.termuxPath ?: state.workspacePath, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f))
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Visão geral", "Arquivos", "Terminal").forEachIndexed { index, label ->
                    FilterChip(selected = tab == index, onClick = { tab = index }, label = { Text(label) }, leadingIcon = {
                        Icon(when (index) { 0 -> Icons.Default.Code; 1 -> Icons.Default.Folder; else -> Icons.Default.Terminal }, null, modifier = Modifier.size(17.dp))
                    })
                }
            }
            when (tab) {
                0 -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                    item {
                        Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .45f))) {
                            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                                Text("Visão geral", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text("Pasta selecionada através do seletor seguro do Android.", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f))
                                Text("Tecnologias detetadas", Modifier.padding(top = 14.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f))
                                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    technologies.forEach { AssistChip(onClick = {}, label = { Text(it) }) }
                                }
                                Text("Atividade recente", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f))
                                Text(newestFile?.let { "Ficheiro recente: " + it.name + (it.lastModified?.let { time -> " · " + SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(time)) } ?: "") } ?: "Sem alterações detetadas na pasta aberta.", style = MaterialTheme.typography.bodySmall)
                                Text("Último build: sem registo nesta sessão", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f))
                                Text(state.files.size.toString() + " itens na pasta atual", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { tab = 1 }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Folder, null); Spacer(Modifier.width(6.dp)); Text("Arquivos") }
                            OutlinedButton(onClick = { tab = 2 }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Terminal, null); Spacer(Modifier.width(6.dp)); Text("Terminal") }
                        }
                    }
                    item {
                        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .72f)) {
                            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                Text("Ligação", style = MaterialTheme.typography.titleSmall)
                                StatusIndicator(state.connected, state.connectionMessage)
                            }
                        }
                    }
                }
                1 -> Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (state.directoryStack.size > 1) TextButton(onClick = onUp) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null); Text("Pasta anterior") }
                        else Text(state.files.size.toString() + " itens", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f))
                        if (state.directoryStack.size > 1) Spacer(Modifier.weight(1f))
                        var menuOpen by remember { mutableStateOf(false) }
                        Box {
                            TextButton(onClick = { menuOpen = true }) { Icon(Icons.Default.Add, null); Text("Novo") }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(text = { Text("Novo arquivo") }, onClick = { menuOpen = false; createKind = false; createName = "" })
                                DropdownMenuItem(text = { Text("Nova pasta") }, onClick = { menuOpen = false; createKind = true; createName = "" })
                                DropdownMenuItem(text = { Text("Carregar ficheiro") }, onClick = { menuOpen = false; onImport() })
                            }
                        }
                    }
                    if (state.files.isEmpty()) EmptyStateCard("Pasta vazia", "Cria um ficheiro ou uma pasta para começar.", "Criar ficheiro") { createKind = false; createName = "" }
                    else LazyColumn(verticalArrangement = Arrangement.spacedBy(5.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
                        items(state.files, key = { it.uri.toString() }) { file ->
                            ManagedFileRow(file, onClick = { onOpen(file) }, onRename = { renameFile = file; renameText = file.name }, onDelete = { deleteFile = file }, onCopy = { onCopy(file) }, onMove = { moveFile = file })
                        }
                    }
                }
                else -> TerminalScreen(state, terminalCommand, onTerminalCommand, onRunCommand, onCancelCommand, onTerminalInput)
            }
        }
    }
    createKind?.let { directory ->
        AlertDialog(onDismissRequest = { createKind = null }, title = { Text(if (directory) "Nova pasta" else "Novo arquivo") },
            text = { OutlinedTextField(value = createName, onValueChange = { createName = it }, singleLine = true, label = { Text("Nome") }) },
            confirmButton = { TextButton(onClick = { if (createName.isNotBlank()) onCreateItem(createName, directory); createKind = null }) { Text("Criar") } },
            dismissButton = { TextButton(onClick = { createKind = null }) { Text("Cancelar") } })
    }
    renameFile?.let { file ->
        AlertDialog(onDismissRequest = { renameFile = null }, title = { Text("Renomear") },
            text = { OutlinedTextField(value = renameText, onValueChange = { renameText = it }, singleLine = true, label = { Text("Novo nome") }) },
            confirmButton = { TextButton(onClick = { if (renameText.isNotBlank()) onRename(file, renameText); renameFile = null }) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = { renameFile = null }) { Text("Cancelar") } })
    }
    moveFile?.let { file ->
        val directories = state.files.filter { it.isDirectory && it.uri != file.uri }
        AlertDialog(onDismissRequest = { moveFile = null }, title = { Text("Mover " + file.name) },
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
            confirmButton = { TextButton(onClick = { moveFile = null }) { Text("Fechar") } })
    }
    deleteFile?.let { file ->
        AlertDialog(onDismissRequest = { deleteFile = null }, title = { Text("Apagar " + file.name + "?") }, text = { Text("Esta ação remove o item da pasta selecionada.") },
            confirmButton = { TextButton(onClick = { onDelete(file); deleteFile = null }) { Text("Apagar") } },
            dismissButton = { TextButton(onClick = { deleteFile = null }) { Text("Cancelar") } })
    }
}

@Composable
private fun ProjectCard(project: ProjectWorkspace, onClick: () -> Unit, onRemove: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .45f)),
        modifier = Modifier.fillMaxWidth().animateContentSize().clickable(onClick = onClick)) {
        Row(Modifier.padding(start = 14.dp, top = 13.dp, bottom = 13.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(project.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(project.termuxPath, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f))
                Text("Aberto " + SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(project.lastOpenedAt)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f))
            }
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "Ações do projeto") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Abrir") }, onClick = { menuOpen = false; onClick() })
                    DropdownMenuItem(text = { Text("Remover da lista") }, onClick = { menuOpen = false; onRemove() })
                }
            }
        }
    }
}

@Composable
private fun EmptyStateCard(title: String, message: String, action: String, onAction: () -> Unit) {
    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .42f))) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
            Text(title, Modifier.padding(top = 10.dp), style = MaterialTheme.typography.titleMedium)
            Text(message, Modifier.padding(top = 5.dp, bottom = 14.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f))
            Button(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun StatusIndicator(connected: Boolean, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (connected) Color(0xFF32A879) else MaterialTheme.colorScheme.outline))
        Text(label, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .68f))
    }
}

@Composable
private fun ManagedFileRow(file: WorkspaceFile, onClick: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit, onCopy: () -> Unit, onMove: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(15.dp), color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .32f)),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
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

@Composable
private fun TerminalScreen(state: AppState, command: String, onCommand: (String) -> Unit, onRun: (String) -> Unit, onCancel: () -> Unit, onInput: (String, Boolean) -> Unit) {
    var stdin by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    val history = remember { mutableStateListOf<String>() }
    val outputScrollState = rememberScrollState()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val runtime = remember(context) { EmbeddedTermuxRuntime(context) }
    var localShell by remember { mutableStateOf(false) }
    if (localShell) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Terminal integrado", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { localShell = false }) { Text("App Server") }
            }
            EmbeddedTermuxTerminal(runtime, modifier = Modifier.weight(1f).fillMaxWidth())
        }
        return
    }
    LaunchedEffect(state.terminalOutput) {
        withFrameNanos { }
        outputScrollState.scrollTo(outputScrollState.maxValue)
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Terminal", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = { localShell = true }) { Text("Terminal integrado") }
                StatusIndicator(state.terminalRunning, if (state.terminalRunning) "Comando em execução" else "Pronto")
            }
            TextButton(onClick = { clipboard.setText(AnnotatedString(state.terminalOutput)) }, enabled = state.terminalOutput.isNotBlank()) {
                Icon(Icons.Default.ContentCopy, "Copiar saída", modifier = Modifier.size(17.dp)); Text("Copiar")
            }
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Reduzir" else "Expandir") }
        }
        if (history.isNotEmpty()) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            history.takeLast(6).asReversed().forEach { recent -> AssistChip(onClick = { onCommand(recent) }, label = { Text(recent, maxLines = 1) }) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            OutlinedTextField(value = command, onValueChange = onCommand, modifier = Modifier.weight(1f), placeholder = { Text("Comando…") }, singleLine = true, enabled = !state.terminalRunning)
            Spacer(Modifier.width(8.dp))
            FilledIconButton(onClick = if (state.terminalRunning) onCancel else ({
                if (command.isNotBlank()) { history.add(command); onRun(command) }
            }), enabled = state.terminalRunning || (command.isNotBlank() && state.connected)) {
                Icon(if (state.terminalRunning) Icons.Default.Stop else Icons.Default.Terminal, if (state.terminalRunning) "Parar" else "Executar")
            }
        }
        Surface(color = Color(0xFF111722), shape = RoundedCornerShape(18.dp), modifier = Modifier.weight(if (expanded) 1.5f else 1f).fillMaxWidth().padding(top = 12.dp)) {
            Column(Modifier.fillMaxSize().verticalScroll(outputScrollState).padding(14.dp)) {
                Text(state.terminalOutput.ifBlank { "A saída dos comandos aparecerá aqui." }, color = Color(0xFFD8E4F3), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
        }
        if (state.terminalRunning) Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = stdin, onValueChange = { stdin = it }, modifier = Modifier.weight(1f), placeholder = { Text("Entrada do processo") }, singleLine = true)
            TextButton(onClick = { onInput(stdin + "\n", false); stdin = "" }, enabled = stdin.isNotEmpty()) { Text("Enviar") }
            TextButton(onClick = { onInput("", true) }) { Text("Fechar entrada") }
        }
    }
}

@Composable
private fun SettingSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .38f))) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .72f))
            content()
        }
    }
}

@Composable
private fun SettingsScreen(
    state: AppState,
    onTheme: (String) -> Unit,
    onAccent: (String) -> Unit,
    onTextScale: (Float) -> Unit,
    onTransparency: (Float) -> Unit,
    onWorkspace: (String) -> Unit,
    onTimeout: (Int) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    var path by remember(state.workspacePath) { mutableStateOf(state.workspacePath) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Definições", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold) }
        item {
            SettingSection("APARÊNCIA") {
                Text("Tema", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("system" to "Sistema", "light" to "Claro", "dark" to "Escuro").forEach { (value, label) ->
                        FilterChip(selected = state.themeMode == value, onClick = { onTheme(value) }, label = { Text(label) })
                    }
                }
                Text("Cor de destaque", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    listOf("blue" to Color(0xFF2878E5), "violet" to Color(0xFF8164D8), "mint" to Color(0xFF149B83), "orange" to Color(0xFFE18036), "rose" to Color(0xFFD95782)).forEach { (key, color) ->
                        Surface(shape = CircleShape, color = color, modifier = Modifier.size(if (state.accentColor == key) 34.dp else 28.dp).clickable { onAccent(key) },
                            border = if (state.accentColor == key) androidx.compose.foundation.BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .7f)) else null) { }
                    }
                }
                Text("Tamanho do texto", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f))
                Slider(value = state.textScale, onValueChange = onTextScale, valueRange = .85f..1.25f)
                Text("Transparência dos painéis", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f))
                Slider(value = state.transparency, onValueChange = onTransparency, valueRange = 0f..0.35f)
            }
        }
        item {
            SettingSection("PROJETO") {
                Text("Caminho de trabalho no runtime integrado", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f))
                OutlinedTextField(value = path, onValueChange = { path = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    shape = RoundedCornerShape(16.dp), label = { Text("Caminho absoluto") })
                OutlinedButton(onClick = { onWorkspace(path) }, modifier = Modifier.align(Alignment.End)) { Text("Guardar caminho") }
                Text("A pasta visual é selecionada em Projetos através do seletor SAF. O caminho acima é usado pelo processo Codex.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f))
            }
        }
        item {
            SettingSection("TERMUX E CODEX") {
                StatusIndicator(state.connected, state.connectionMessage)
                Text(if (state.connected) "Codex App Server ativo" else "Codex CLI ainda não ligado", style = MaterialTheme.typography.bodyMedium)
                if (state.connected) OutlinedButton(onClick = onDisconnect) { Text("Desligar") }
                else Button(onClick = onConnect, enabled = !state.connecting) { Text(if (state.connecting) "A ligar…" else "Ligar ao Codex integrado") }
                Text("O runtime Linux e o Codex CLI ARM64 estão incluídos no Codux. Na primeira utilização, abre Terminal integrado, executa codex login --device-auth, conclui o acesso pelo código no browser e volta aqui para ligar.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f))
            }
        }
        item {
            SettingSection("TERMINAL") {
                Text("Limite de execução: " + state.terminalTimeoutSeconds + " segundos", style = MaterialTheme.typography.bodySmall)
                Slider(value = state.terminalTimeoutSeconds.toFloat(), onValueChange = { onTimeout(it.toInt()) }, valueRange = 10f..3600f, steps = 35)
                Text("A saída do terminal mantém cores e tipografia próprias para leitura de comandos.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f))
            }
        }
        item {
            SettingSection("SOBRE") {
                Text("Codux · versão 0.1.0", style = MaterialTheme.typography.bodyMedium)
                Text("Cliente Android em Jetpack Compose Material 3.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f))
            }
        }
    }
}

@Composable
private fun ErrorBanner(text: String, onDismiss: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, Modifier.weight(1f).padding(vertical = 10.dp), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Fechar aviso", tint = MaterialTheme.colorScheme.onErrorContainer) }
        }
    }
}
