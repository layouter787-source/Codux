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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.codux.data.AppServerLog
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

/** Os cinco setores principais, pela ordem em que aparecem na navegação. */
private enum class Destination(val label: String, val icon: ImageVector) {
    AGENT("Agente", Icons.Default.AutoAwesome),
    TERMINAL("Terminal", Icons.Default.Terminal),
    EDITOR("Editor", Icons.Default.Code),
    PROJECTS("Projetos", Icons.Default.Folder),
    SETTINGS("Definições", Icons.Default.Settings)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CoduxApp() }
    }
}

@Composable
private fun CoduxTheme(mode: String, accent: String, textScale: Float, transparency: Float, content: @Composable () -> Unit) {
    val dark = when (mode) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    val palette = remember(dark, accent, transparency) { coduxPalette(dark, coduxAccent(accent), transparency) }
    val scheme = remember(palette) { coduxColorScheme(palette) }
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalCoduxPalette provides palette,
        LocalDensity provides Density(density.density, density.fontScale * textScale.coerceIn(.85f, 1.25f))
    ) {
        MaterialTheme(colorScheme = scheme, typography = coduxTypography(), content = content)
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
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val runtime = remember(context) { EmbeddedTermuxRuntime(context) }
    // Sessão do terminal e ficheiros abertos vivem aqui para sobreviver à navegação entre ecrãs.
    val terminalHolder = remember { TerminalHolder() }
    val editorSession = remember { EditorSession() }
    DisposableEffect(Unit) { onDispose { terminalHolder.finish() } }

    val openHistory: () -> Unit = { scope.launch { drawerState.open() } }
    val closeHistory: () -> Unit = { scope.launch { drawerState.close() } }

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
    CoduxTheme(state.themeMode, state.accentColor, state.textScale, state.transparency) {
        val colors = Codux.colors
        SideEffect {
            val window = (view.context as? ComponentActivity)?.window ?: return@SideEffect
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkSystemBars
                isAppearanceLightNavigationBars = !darkSystemBars
            }
        }
        val reducedMotion = rememberReducedMotion()
        val navItems = remember { Destination.values().map { CoduxNavItem(it.label, it.icon) } }
        val density = LocalDensity.current

        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = destination == Destination.AGENT,
            drawerContent = {
                ModalDrawerSheet(modifier = Modifier.widthIn(max = 340.dp)) {
                    Column(Modifier.fillMaxSize().padding(CoduxSpace.lg)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.Icon(Icons.Default.History, null, tint = colors.ai)
                            Text("Conversas", Modifier.padding(start = CoduxSpace.sm), style = MaterialTheme.typography.titleMedium, color = colors.text)
                        }
                        Spacer(Modifier.height(CoduxSpace.lg))
                        CoduxPrimaryButton(
                            "Nova conversa",
                            { vm.createThread(); destination = Destination.AGENT; closeHistory() },
                            Modifier.fillMaxWidth(),
                            icon = Icons.Default.Add,
                            color = colors.ai
                        )
                        Spacer(Modifier.height(CoduxSpace.lg))
                        Text("Recentes", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
                        Spacer(Modifier.height(CoduxSpace.sm))
                        if (state.threads.isEmpty() && !state.isLoadingThreads) {
                            Text("Ainda não há conversas.", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                        }
                        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            items(state.threads, key = { it.id }) { thread ->
                                ThreadRow(thread, state.activeThreadId == thread.id) {
                                    vm.openThread(thread); destination = Destination.AGENT; closeHistory()
                                }
                            }
                            if (state.threadCursor != null) item {
                                CoduxTextAction(
                                    if (state.isLoadingThreads) "A carregar…" else "Carregar conversas anteriores",
                                    vm::loadMoreThreads,
                                    Modifier.fillMaxWidth(),
                                    enabled = !state.isLoadingThreads
                                )
                            }
                        }
                    }
                }
            }
        ) {
            BoxWithConstraints(Modifier.fillMaxSize().background(colors.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
                val wide = maxWidth >= CoduxWideBreakpoint
                val imeVisible = WindowInsets.ime.getBottom(density) > 0
                Row(Modifier.fillMaxSize()) {
                    if (wide) CoduxNavRail(navItems, destination.ordinal, { destination = Destination.values()[it] }, Icons.Default.Code)
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        bannerText?.let { ErrorBanner(it, vm::clearError) }
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            AnimatedContent(
                                targetState = destination,
                                transitionSpec = {
                                    if (reducedMotion) EnterTransition.None togetherWith ExitTransition.None
                                    else fadeIn(tween(160)) togetherWith fadeOut(tween(100))
                                },
                                label = "destination"
                            ) { target ->
                                when (target) {
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
                                        onHistory = openHistory,
                                        onNewThread = { vm.createThread() },
                                        onConnect = connectToTermux,
                                        onDisconnect = vm::disconnect
                                    )
                                    Destination.TERMINAL -> Column(Modifier.fillMaxSize()) {
                                        CoduxTopBar(
                                            title = "Terminal Codux",
                                            subtitle = "bash · runtime integrado",
                                            icon = Icons.Default.Terminal,
                                            iconTint = colors.success
                                        )
                                        EmbeddedTermuxTerminal(runtime, terminalHolder, modifier = Modifier.weight(1f).fillMaxWidth())
                                    }
                                    Destination.EDITOR -> EditorScreen(
                                        state = state,
                                        session = editorSession,
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
                                    Destination.PROJECTS -> ProjectsScreen(
                                        state = state,
                                        onNewProject = { folderPicker.launch(null) },
                                        onOpenProject = { project -> vm.openProject(project); destination = Destination.EDITOR },
                                        onRemoveProject = vm::removeProject
                                    )
                                    Destination.SETTINGS -> SettingsScreen(
                                        state = state,
                                        onTheme = vm::setThemeMode,
                                        onAccent = vm::setAccentColor,
                                        onTextScale = vm::setTextScale,
                                        onTransparency = vm::setTransparency,
                                        onWorkspace = vm::setWorkspacePath,
                                        onTimeout = vm::setTerminalTimeout,
                                        onConnect = connectToTermux,
                                        onDisconnect = vm::disconnect,
                                        onHelp = { showHelp = true }
                                    )
                                }
                            }
                        }
                        if (!wide && !imeVisible) CoduxBottomBar(navItems, destination.ordinal) { destination = Destination.values()[it] }
                    }
                }
            }
        }

        if (showHelp) AlertDialog(onDismissRequest = { showHelp = false }, title = { Text("Ajuda") },
            text = { Text("Escolhe uma pasta em Projetos, abre-a no Editor e liga o Codex em Agente. Para entrar na conta, abre o Terminal e executa codex login --device-auth.") },
            confirmButton = { TextButton(onClick = { showHelp = false }) { Text("Fechar") } })
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
                text = { LazyColumn(Modifier.heightIn(max = 520.dp)) { item { Text(diff.ifBlank { "Ainda não há alterações para mostrar." }, style = MaterialTheme.typography.bodySmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace) } } },
                confirmButton = { TextButton(onClick = { showDiff = false }) { Text("Fechar") } })
        }
    }
}

@Composable
private fun ThreadRow(thread: CodexThread, selected: Boolean, onClick: () -> Unit) {
    val colors = Codux.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(CoduxShape.medium)
            .background(if (selected) colors.accent.copy(alpha = .14f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = CoduxSpace.md, vertical = 10.dp)
    ) {
        Text(thread.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = colors.text)
        if (thread.preview.isNotBlank()) Text(thread.preview, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
    }
}
