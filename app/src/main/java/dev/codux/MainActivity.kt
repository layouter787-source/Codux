package dev.codux

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.codux.data.AppServerLog
import dev.codux.data.AppState
import dev.codux.data.CodexThread
import dev.codux.data.CoduxViewModel
import dev.codux.runtime.EmbeddedTermuxRuntime
import dev.codux.runtime.EmbeddedTermuxTerminal
import dev.codux.runtime.TerminalHolder
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class TextAttachment(val name: String, val content: String)

private enum class Destination(val label: String) {
    AGENT("Agente"), PROJECTS("Projetos"), EDITOR("Editor"), TERMINAL("Terminal"), SETTINGS("Definições")
}

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

@Composable
private fun CoduxApp(vm: CoduxViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val serverLog by AppServerLog.text.collectAsStateWithLifecycle()
    val view = LocalView.current
    val context = LocalContext.current
    var destination by remember { mutableStateOf(Destination.AGENT) }
    var prompt by remember { mutableStateOf("") }
    var showDiff by remember { mutableStateOf(false) }
    var attachment by remember { mutableStateOf<TextAttachment?>(null) }
    var showHelp by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val runtime = remember(context) { EmbeddedTermuxRuntime(context) }
    // Sessão do terminal e ficheiros abertos vivem aqui para sobreviver à navegação entre ecrãs.
    val terminalHolder = remember { TerminalHolder() }
    val editorSession = remember { EditorSession() }
    DisposableEffect(Unit) { onDispose { terminalHolder.finish() } }

    val openDrawer: () -> Unit = { scope.launch { drawerState.open() } }
    val goTo: (Destination) -> Unit = { target -> destination = target; scope.launch { drawerState.close() } }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { vm.selectWorkspaceTree(it); destination = Destination.EDITOR }
    }
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
    BackHandler(enabled = destination != Destination.AGENT) { destination = Destination.AGENT }

    // A mensagem genérica do ViewModel sugere fazer login; o que realmente ajuda é ver o que o Codex escreveu.
    val bannerText = state.error?.let { message ->
        if (message.startsWith("O servidor não respondeu")) {
            "O servidor do Codex não respondeu.\n" + if (serverLog.isBlank()) {
                "O processo não escreveu nada. Abre o Terminal e executa codex --version para ver se o Codex arranca."
            } else {
                "Saída do Codex:\n" + AppServerLog.tail(14)
            }
        } else message
    }

    val darkSystemBars = when (state.themeMode) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    CoduxTheme(state.themeMode, state.accentColor, state.textScale) {
        SideEffect {
            val window = (view.context as? ComponentActivity)?.window ?: return@SideEffect
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkSystemBars
                isAppearanceLightNavigationBars = !darkSystemBars
            }
        }
        ModalNavigationDrawer(drawerState = drawerState, drawerContent = {
            ModalDrawerSheet(modifier = Modifier.widthIn(max = 340.dp)) {
                Column(Modifier.fillMaxSize().padding(18.dp)) {
                    Text("CODUX_", fontFamily = FontFamily.Monospace, fontStyle = FontStyle.Italic, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.headlineMedium)
                    Text("O teu espaço de desenvolvimento", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f))
                    Spacer(Modifier.height(22.dp))
                    Button(onClick = { vm.createThread(); goTo(Destination.AGENT) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Nova conversa")
                    }
                    DrawerDestinationRow("Agente", Icons.Default.ChatBubbleOutline, destination == Destination.AGENT) { goTo(Destination.AGENT) }
                    DrawerDestinationRow("Projetos", Icons.Default.Folder, destination == Destination.PROJECTS) { goTo(Destination.PROJECTS) }
                    DrawerDestinationRow("Editor", Icons.Default.Code, destination == Destination.EDITOR) { goTo(Destination.EDITOR) }
                    DrawerDestinationRow("Terminal", Icons.Default.Terminal, destination == Destination.TERMINAL) { goTo(Destination.TERMINAL) }
                    DrawerDestinationRow("Definições", Icons.Default.Tune, destination == Destination.SETTINGS) { goTo(Destination.SETTINGS) }
                    DrawerDestinationRow("Ajuda", Icons.Default.HelpOutline, false) { showHelp = true; scope.launch { drawerState.close() } }
                    DrawerDestinationRow("Sobre", Icons.Default.Info, false) { showAbout = true; scope.launch { drawerState.close() } }
                    Spacer(Modifier.height(16.dp)); Text("Conversas recentes", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f))
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        items(state.threads, key = { it.id }) { thread ->
                            ThreadRow(thread, state.activeThreadId == thread.id) { vm.openThread(thread); goTo(Destination.AGENT) }
                        }
                        if (state.threadCursor != null) item {
                            TextButton(onClick = vm::loadMoreThreads, enabled = !state.isLoadingThreads, modifier = Modifier.fillMaxWidth()) {
                                Text(if (state.isLoadingThreads) "A carregar…" else "Carregar conversas anteriores")
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }) {
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
                bannerText?.let { ErrorBanner(it, vm::clearError) }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (destination) {
                        Destination.AGENT -> ChatScreen(
                            state = state,
                            prompt = prompt,
                            onPrompt = { prompt = it },
                            onSend = {
                                val attached = attachment
                                val message = if (attached == null) prompt else prompt + "\n\nFicheiro: " + attached.name + "\n\n" + attached.content
                                vm.sendMessage(message); prompt = ""; attachment = null
                            },
                            onStop = vm::cancelTurn,
                            onAttach = { attachLauncher.launch(arrayOf("text/*", "application/json", "application/xml", "application/javascript")) },
                            attachmentName = attachment?.name,
                            onRemoveAttachment = { attachment = null },
                            onVoice = {
                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                                runCatching { speechLauncher.launch(intent) }
                                    .onFailure { vm.showError("O ditado por voz não está disponível neste dispositivo.") }
                            },
                            onShowDiff = { showDiff = true },
                            onMenu = openDrawer,
                            onConnect = connectToTermux,
                            onDisconnect = vm::disconnect
                        )
                        Destination.PROJECTS -> ProjectsScreen(
                            state = state,
                            onMenu = openDrawer,
                            onNewProject = { folderPicker.launch(null) },
                            onOpenProject = { project -> vm.openProject(project); destination = Destination.EDITOR },
                            onRemoveProject = vm::removeProject
                        )
                        Destination.EDITOR -> EditorScreen(
                            state = state,
                            session = editorSession,
                            onMenu = openDrawer,
                            onGoProjects = { destination = Destination.PROJECTS },
                            onList = { uri -> state.files.firstOrNull { it.uri == uri }?.let(vm::openFile) },
                            onUp = vm::navigateUp,
                            onCreate = vm::createProjectItem,
                            onRename = vm::renameProjectItem,
                            onDelete = vm::deleteProjectItem,
                            onImport = { importPicker.launch(arrayOf("*/*")) },
                            onCopy = vm::copyProjectItem,
                            onMove = vm::moveProjectItem,
                            onError = vm::showError,
                            onSaved = {}
                        )
                        Destination.TERMINAL -> Column(Modifier.fillMaxSize()) {
                            CoduxHeader("Terminal Codux", openDrawer, cursor = false)
                            EmbeddedTermuxTerminal(runtime, terminalHolder, modifier = Modifier.weight(1f).fillMaxWidth())
                        }
                        Destination.SETTINGS -> SettingsScreen(
                            state, openDrawer, vm::setThemeMode, vm::setAccentColor, vm::setTextScale, vm::setTransparency,
                            vm::setWorkspacePath, vm::setTerminalTimeout, connectToTermux, vm::disconnect
                        )
                    }
                }
            }
        }
        if (showHelp) AlertDialog(onDismissRequest = { showHelp = false }, title = { Text("Ajuda") },
            text = { Text("Escolhe uma pasta em Projetos, abre-a no Editor e liga o Codex em Agente. Para entrar na conta, abre o Terminal e executa codex login --device-auth.") },
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
        if (showDiff) {
            val diff = state.diffPreview ?: state.currentDiff
            AlertDialog(onDismissRequest = { showDiff = false }, title = { Text("Alterações") },
                text = { LazyColumn(Modifier.heightIn(max = 520.dp)) { item { Text(diff.ifBlank { "Ainda não há alterações para mostrar." }, style = MaterialTheme.typography.bodySmall) } } },
                confirmButton = { TextButton(onClick = { showDiff = false }) { Text("Fechar") } })
        }
    }
}

@Composable
private fun DrawerDestinationRow(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
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
private fun SettingSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .38f))) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .72f))
            content()
        }
    }
}

@Composable
private fun SettingsScreen(
    state: AppState,
    onMenu: () -> Unit,
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
    val serverLog by AppServerLog.text.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        CoduxHeader("Definições", onMenu)
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                                border = if (state.accentColor == key) BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .7f)) else null) { }
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
                    Text("O runtime Linux e o Codex CLI ARM64 estão incluídos no Codux. Na primeira utilização, abre o Terminal, executa codex login --device-auth, conclui o acesso pelo código no browser e volta aqui para ligar.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f))
                    if (serverLog.isNotBlank()) {
                        Text("Registo do Codex", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f))
                        Surface(color = Color(0xFF111722), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                            Text(
                                AppServerLog.tail(30),
                                Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()).padding(10.dp),
                                color = Color(0xFFD8E4F3),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        TextButton(onClick = { AppServerLog.clear() }) { Text("Limpar registo") }
                    }
                }
            }
            item {
                SettingSection("TERMINAL") {
                    Text("Limite de execução: " + state.terminalTimeoutSeconds + " segundos", style = MaterialTheme.typography.bodySmall)
                    Slider(value = state.terminalTimeoutSeconds.toFloat(), onValueChange = { onTimeout(it.toInt()) }, valueRange = 10f..3600f, steps = 35)
                    Text("Limite usado pelos comandos que o Codex executa em nome do agente.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f))
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
}
