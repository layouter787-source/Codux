package dev.codux

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
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
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.codux.data.AppState
import dev.codux.data.ChatMessage
import dev.codux.data.CodexThread
import dev.codux.data.CoduxViewModel
import dev.codux.data.MessageRole
import dev.codux.data.WorkspaceFile
import kotlinx.coroutines.launch
import java.util.Locale

private enum class Destination(val label: String) { CHAT("Chat"), PROJECTS("Projetos"), TERMINAL("Terminal"), SETTINGS("Definições") }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CoduxApp() }
    }
}

@Composable
private fun CoduxTheme(mode: String, content: @Composable () -> Unit) {
    val dark = when (mode) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    val colors = if (dark) darkColorScheme(
        primary = Color(0xFF10A37F), onPrimary = Color.White, primaryContainer = Color(0xFF173B32),
        background = Color(0xFF212121), surface = Color(0xFF212121), surfaceVariant = Color(0xFF2F2F2F),
        onSurface = Color(0xFFECECEC), onBackground = Color(0xFFECECEC), outline = Color(0xFF484848)
    ) else lightColorScheme(
        primary = Color(0xFF0D8A6F), onPrimary = Color.White, primaryContainer = Color(0xFFE1F3EC),
        background = Color(0xFFFFFFFF), surface = Color(0xFFFFFFFF), surfaceVariant = Color(0xFFF4F4F4),
        onSurface = Color(0xFF202123), onBackground = Color(0xFF202123), outline = Color(0xFFE2E2E2)
    )
    MaterialTheme(colorScheme = colors, content = content)
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
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let(vm::selectWorkspaceTree) }
    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val words = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!words.isNullOrBlank()) prompt = listOf(prompt, words).filter(String::isNotBlank).joinToString(" ")
    }
    val termuxPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.connectToTermux() else vm.showError("Permite a autorização RUN_COMMAND do Termux nas definições do Android para ligar o Codux.")
    }
    val requestTermuxPermission = {
        if (ContextCompat.checkSelfPermission(context, "com.termux.permission.RUN_COMMAND") == PackageManager.PERMISSION_GRANTED) {
            vm.connectToTermux()
        } else {
            termuxPermissionLauncher.launch("com.termux.permission.RUN_COMMAND")
        }
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        requestTermuxPermission()
    }
    val connectToTermux = {
        val termuxInstalled = runCatching { context.packageManager.getPackageInfo("com.termux", 0) }.isSuccess
        when {
            !termuxInstalled -> vm.showError("Instala e abre o Termux oficial antes de ligar o Codux.")
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED -> notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            else -> requestTermuxPermission()
        }
    }
    LaunchedEffect(Unit) { vm.restoreWorkspaceTree() }
    val requestCloseDocument = {
        if (!state.isSaving) {
            val document = state.openDocument
            if (document != null && editorText != document.originalText) showDiscardDocument = true else vm.closeDocument()
        }
    }
    if (destination == Destination.PROJECTS && state.openDocument != null) BackHandler { requestCloseDocument() }
    val darkSystemBars = when (state.themeMode) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    CoduxTheme(state.themeMode) {
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
                    Spacer(Modifier.height(18.dp)); Text("Recentes", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f))
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
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { destination = Destination.SETTINGS; scope.launch { drawerState.close() } }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Settings, null); Spacer(Modifier.width(12.dp)); Text("Definições")
                    }
                }
            }
        }) {
            Scaffold(
                topBar = {
                    TopAppBar(title = {
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
                    NavigationBar {
                        Destination.values().forEach { page ->
                            val icon = when (page) { Destination.CHAT -> Icons.AutoMirrored.Filled.Send; Destination.PROJECTS -> Icons.Default.Folder; Destination.TERMINAL -> Icons.Default.Terminal; Destination.SETTINGS -> Icons.Default.Tune }
                            NavigationBarItem(selected = destination == page, onClick = { destination = page }, icon = { Icon(icon, page.label) }, label = { Text(page.label) })
                        }
                    }
                }
            ) { padding ->
                Column(Modifier.fillMaxSize().padding(padding)) {
                    state.error?.let { ErrorBanner(it, vm::clearError) }
                    when (destination) {
                        Destination.CHAT -> ChatScreen(state, prompt, { prompt = it }, { vm.sendMessage(prompt); prompt = "" }, vm::cancelTurn, {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                            runCatching { speechLauncher.launch(intent) }
                                .onFailure { vm.showError("O ditado por voz não está disponível neste dispositivo.") }
                        }, { showDiff = true })
                        Destination.PROJECTS -> ProjectsScreen(state, editorText, { editorText = it; vm.editDocument(it) }, { folderPicker.launch(null) }, vm::openFile, vm::navigateUp, requestCloseDocument, vm::saveDocument, { vm.previewDocumentDiff(); showDiff = true })
                        Destination.TERMINAL -> TerminalScreen(state, terminalDraft, { terminalDraft = it }, vm::startTerminalCommand, vm::cancelTerminal, vm::sendTerminalInput)
                        Destination.SETTINGS -> SettingsScreen(state, vm::setThemeMode, vm::setWorkspacePath, vm::setTerminalTimeout, connectToTermux, vm::disconnect)
                    }
                }
            }
        }
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
            AlertDialog(onDismissRequest = vm::dismissTerminalConfirmation, title = { Text("Executar comando no Termux?") },
                text = { Text("O comando corre no diretório de trabalho fora do sandbox do Codex. Confirma apenas comandos que reconheces.\n\n${state.terminalCommand}") },
                confirmButton = { TextButton(onClick = vm::confirmTerminalCommand) { Text("Executar") } },
                dismissButton = { TextButton(onClick = vm::dismissTerminalConfirmation) { Text("Cancelar") } })
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
private fun ChatScreen(state: AppState, prompt: String, onPrompt: (String) -> Unit, onSend: () -> Unit, onStop: () -> Unit, onVoice: () -> Unit, onShowDiff: () -> Unit) {
    val messageListState = rememberLazyListState()
    LaunchedEffect(state.messages.lastOrNull()?.id, state.messages.lastOrNull()?.text, state.messages.size, state.isTurnRunning) {
        val target = if (state.isTurnRunning && state.messages.lastOrNull()?.isStreaming != true) state.messages.size else state.messages.lastIndex
        if (state.messages.isNotEmpty() && target >= 0) messageListState.scrollToItem(target)
    }
    Column(Modifier.fillMaxSize()) {
        if (state.messages.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Em que posso ajudar?", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp)); Text("Trabalha no teu projeto com o Codex.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f))
                Spacer(Modifier.height(24.dp))
                Column(Modifier.widthIn(max = 560.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Explica a estrutura deste projeto", "Ajuda-me a corrigir um erro", "Implementa uma funcionalidade").forEach { suggestion ->
                        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().clickable { onPrompt(suggestion) }, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .65f))) {
                            Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Code, null, tint = MaterialTheme.colorScheme.primary.copy(alpha = .8f), modifier = Modifier.size(18.dp))
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
            Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(26.dp), modifier = Modifier.fillMaxWidth(), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .75f))) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    BasicTextField(value = prompt, onValueChange = onPrompt, modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp).heightIn(min = 36.dp, max = 150.dp),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface), cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { if (prompt.isNotBlank() && state.connected && !state.isTurnRunning) onSend() }),
                        decorationBox = { inner -> Box { if (prompt.isBlank()) Text("Mensagem para o Codex", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .45f)); inner() } })
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onVoice, modifier = Modifier.size(38.dp)) { Icon(Icons.Default.Mic, "Ditado por voz", modifier = Modifier.size(20.dp)) }
                        FilledIconButton(onClick = if (state.isTurnRunning) onStop else onSend, enabled = if (state.isTurnRunning) state.activeTurnId != null else (prompt.isNotBlank() && state.connected), modifier = Modifier.size(38.dp)) {
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
            Spacer(Modifier.width(12.dp)); Text(message.text, Modifier.weight(1f).padding(top = 3.dp), style = MaterialTheme.typography.bodyLarge, lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.2f)
        }
        MessageRole.SYSTEM -> Text(message.text, Modifier.fillMaxWidth().padding(horizontal = 40.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f))
    }
}

@Composable
private fun ProjectsScreen(state: AppState, editorText: String, onEditorText: (String) -> Unit, onPick: () -> Unit, onOpen: (WorkspaceFile) -> Unit, onUp: () -> Unit, onClose: () -> Unit, onSave: () -> Unit, onDiff: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp)) {
        if (state.openDocument != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose, enabled = !state.isSaving) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
                Text(state.openDocument.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onDiff) { Text("Diff") }
                TextButton(onClick = onSave, enabled = !state.isSaving && editorText != state.openDocument.originalText) { Text(if (state.isSaving) "A guardar…" else "Guardar") }
            }
            OutlinedTextField(value = editorText, onValueChange = onEditorText, modifier = Modifier.fillMaxSize(), enabled = !state.isSaving, textStyle = MaterialTheme.typography.bodySmall, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrect = false))
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Ficheiros", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                OutlinedButton(onClick = onPick) { Icon(Icons.Default.Folder, null); Spacer(Modifier.width(6.dp)); Text(if (state.workspaceUri == null) "Escolher pasta" else "Mudar pasta") }
            }
            Text(state.workspaceUri?.lastPathSegment?.substringAfterLast(':') ?: "Escolhe uma pasta para começar", Modifier.padding(top = 5.dp, bottom = 10.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f), style = MaterialTheme.typography.bodySmall)
            if (state.workspaceUri != null) {
                OutlinedButton(onClick = onUp, modifier = Modifier.padding(bottom = 8.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null); Text("Pasta anterior") }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) { items(state.files, key = { it.uri.toString() }) { FileRow(it) { onOpen(it) } } }
            }
        }
    }
}

@Composable
private fun FileRow(file: WorkspaceFile, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .6f), modifier = Modifier.clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (file.isDirectory) Icons.Default.Folder else Icons.Default.Code, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp)); Text(file.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!file.isDirectory && file.size != null) Text("${file.size} B", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
        }
    }
}

@Composable
private fun TerminalScreen(state: AppState, command: String, onCommand: (String) -> Unit, onRun: (String) -> Unit, onCancel: () -> Unit, onInput: (String, Boolean) -> Unit) {
    var stdin by remember { mutableStateOf("") }
    val outputScrollState = rememberScrollState()
    LaunchedEffect(state.terminalOutput) {
        withFrameNanos { }
        outputScrollState.scrollTo(outputScrollState.maxValue)
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Terminal", style = MaterialTheme.typography.titleLarge)
        Text("Os comandos são executados no Termux após confirmação.", Modifier.padding(top = 4.dp, bottom = 12.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f), style = MaterialTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = command, onValueChange = onCommand, modifier = Modifier.weight(1f), placeholder = { Text("Comando…") }, singleLine = true, enabled = !state.terminalRunning)
            Spacer(Modifier.width(8.dp)); FilledIconButton(onClick = if (state.terminalRunning) onCancel else ({ onRun(command) }), enabled = state.terminalRunning || (command.isNotBlank() && state.connected)) { Icon(if (state.terminalRunning) Icons.Default.Stop else Icons.Default.Terminal, if (state.terminalRunning) "Parar" else "Executar") }
        }
        Surface(color = Color(0xFF101310), shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 14.dp)) {
            Column(Modifier.fillMaxSize().verticalScroll(outputScrollState).padding(14.dp)) { Text(state.terminalOutput.ifBlank { "Saída do terminal aparecerá aqui." }, color = Color(0xFFD2E6D9), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
        }
        if (state.terminalRunning) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = stdin, onValueChange = { stdin = it }, modifier = Modifier.weight(1f), placeholder = { Text("Entrada do processo") }, singleLine = true)
            TextButton(onClick = { onInput(stdin + "\n", false); stdin = "" }) { Text("Enviar") }
        }
    }
}

@Composable
private fun SettingsScreen(state: AppState, onTheme: (String) -> Unit, onWorkspace: (String) -> Unit, onTimeout: (Int) -> Unit, onConnect: () -> Unit, onDisconnect: () -> Unit) {
    var path by remember(state.workspacePath) { mutableStateOf(state.workspacePath) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            Text("Aparência", style = MaterialTheme.typography.titleLarge); Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("system" to "Sistema", "light" to "Claro", "dark" to "Escuro").forEach { (value, label) ->
                    AssistChip(onClick = { onTheme(value) }, label = { Text(if (state.themeMode == value) "✓ $label" else label) }, leadingIcon = { Icon(when (value) { "light" -> Icons.Default.LightMode; "dark" -> Icons.Default.DarkMode; else -> Icons.Default.Tune }, null) })
                }
            }
        }
        item {
            Text("Ligação", style = MaterialTheme.typography.titleLarge)
            Text(state.connectionMessage, Modifier.padding(top = 4.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f))
            Spacer(Modifier.height(6.dp))
            if (state.connected) OutlinedButton(onClick = onDisconnect) { Text("Desligar") } else Button(onClick = onConnect, enabled = !state.connecting) { Text(if (state.connecting) "A ligar…" else "Ligar ao Termux") }
            Text("Requer o Termux oficial, Codex CLI instalado e autenticado, e allow-external-apps=true.", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f))
        }
        item {
            Text("Projeto de trabalho", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(value = path, onValueChange = { path = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Caminho no Termux") }, singleLine = true)
            TextButton(onClick = { onWorkspace(path) }) { Text("Guardar caminho") }
        }
        item {
            Text("Limite de execução", style = MaterialTheme.typography.titleLarge)
            Text("${state.terminalTimeoutSeconds} segundos")
            Slider(value = state.terminalTimeoutSeconds.toFloat(), onValueChange = { onTimeout(it.toInt()) }, valueRange = 10f..3600f, steps = 35)
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
