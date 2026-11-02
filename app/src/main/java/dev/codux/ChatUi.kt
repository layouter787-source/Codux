package dev.codux

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.codux.data.AppState
import dev.codux.data.ChatMessage
import dev.codux.data.MessageRole
import kotlinx.coroutines.delay

private val NumberedLine = Regex("^(\\d+)\\.\\s+(.*)")
private val TableSeparatorCell = Regex("^:?-{2,}:?$")

@Composable
internal fun ChatScreen(
    state: AppState,
    prompt: String,
    onPrompt: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onAttach: () -> Unit,
    attachmentName: String?,
    onRemoveAttachment: () -> Unit,
    onVoice: () -> Unit,
    onShowDiff: () -> Unit,
    onHistory: () -> Unit,
    onNewThread: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    val colors = Codux.colors
    val messageListState = rememberLazyListState()
    var menuOpen by remember { mutableStateOf(false) }
    LaunchedEffect(state.messages.lastOrNull()?.id, state.messages.lastOrNull()?.text, state.messages.size, state.isTurnRunning) {
        val target = if (state.isTurnRunning && state.messages.lastOrNull()?.isStreaming != true) state.messages.size else state.messages.lastIndex
        if (state.messages.isNotEmpty() && target >= 0) messageListState.scrollToItem(target)
    }
    val hasInput = prompt.isNotBlank() || attachmentName != null
    Column(Modifier.fillMaxSize()) {
        CoduxTopBar(
            title = "Agente Codux",
            subtitle = when {
                state.connecting -> "Codex · a ligar…"
                state.connected -> "Codex · online"
                else -> "Codex · desligado"
            },
            icon = Icons.Default.AutoAwesome,
            iconTint = colors.ai
        ) {
            CoduxIconButton(Icons.Default.History, "Histórico de conversas", onHistory)
            Box {
                CoduxIconButton(Icons.Default.MoreVert, "Mais opções", { menuOpen = true })
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Nova conversa") }, onClick = { menuOpen = false; onNewThread() })
                    if (state.connected) DropdownMenuItem(text = { Text("Desligar do Codex") }, onClick = { menuOpen = false; onDisconnect() })
                    else DropdownMenuItem(text = { Text("Ligar ao Codex") }, enabled = !state.connecting, onClick = { menuOpen = false; onConnect() })
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 820.dp).fillMaxSize()) {
                AgentStrip(state.connected, state.connecting, onConnect, onDisconnect)
                if (state.messages.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (!state.connected) {
                            CoduxEmptyState(
                                icon = Icons.Default.AutoAwesome,
                                title = "Agente desligado",
                                message = "Liga o Codex para conversar sobre o teu projeto, gerar código e editar ficheiros.",
                                actionLabel = if (state.connecting) null else "Ligar ao Codex",
                                onAction = onConnect
                            )
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CoduxEmptyState(
                                    icon = Icons.Default.AutoAwesome,
                                    title = "Como posso ajudar?",
                                    message = "Pergunta algo ao Codex ou começa por uma destas sugestões."
                                )
                                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = CoduxSpace.lg), horizontalArrangement = Arrangement.spacedBy(CoduxSpace.sm)) {
                                    CoduxChip("Explicar este projeto", false, { onPrompt("Explica a estrutura deste projeto.") })
                                    CoduxChip("Procurar erros", false, { onPrompt("Procura erros e problemas no código deste projeto.") })
                                    CoduxChip("Escrever testes", false, { onPrompt("Escreve testes unitários para o código principal.") })
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        state = messageListState,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = CoduxSpace.lg, vertical = CoduxSpace.lg),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        items(state.messages, key = { it.id }) { message -> MessageRow(message) }
                        if (state.isTurnRunning && state.messages.lastOrNull()?.isStreaming != true) {
                            item { WorkingRow("O Codex está a trabalhar…") }
                        }
                    }
                }
                if (state.currentDiff.isNotBlank()) {
                    val files = remember(state.currentDiff) { changedFiles(state.currentDiff) }
                    ChangesCard(files, onShowDiff)
                }
                Composer(
                    prompt = prompt,
                    onPrompt = onPrompt,
                    hasInput = hasInput,
                    canSend = hasInput && state.connected && !state.isTurnRunning,
                    running = state.isTurnRunning,
                    canStop = state.activeTurnId != null,
                    attachmentName = attachmentName,
                    onSend = onSend,
                    onStop = onStop,
                    onAttach = onAttach,
                    onRemoveAttachment = onRemoveAttachment,
                    onVoice = onVoice
                )
            }
        }
    }
}

private fun changedFiles(diff: String): List<String> = diff.lineSequence()
    .filter { it.startsWith("+++ ") }
    .map { it.removePrefix("+++ ").removePrefix("b/").trim() }
    .filter { it.isNotEmpty() && it != "/dev/null" }
    .distinct()
    .toList()

/** Agente ativo, modelo e ligação numa única faixa compacta. O Codex é o único agente integrado. */
@Composable
private fun AgentStrip(connected: Boolean, connecting: Boolean, onConnect: () -> Unit, onDisconnect: () -> Unit) {
    val colors = Codux.colors
    CoduxCard(Modifier.fillMaxWidth().padding(horizontal = CoduxSpace.lg, vertical = CoduxSpace.xs)) {
        Row(Modifier.padding(horizontal = CoduxSpace.md, vertical = CoduxSpace.sm), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Agente ativo", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(if (connected) colors.success else colors.textSecondary)
                    Text("Codex", Modifier.padding(start = 6.dp), style = MaterialTheme.typography.titleSmall, color = colors.text)
                }
            }
            Box(Modifier.width(1.dp).height(30.dp).background(colors.border))
            Column(Modifier.weight(1f).padding(start = CoduxSpace.md)) {
                Text("Modelo", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                Text("Padrão do Codex", style = MaterialTheme.typography.titleSmall, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (connected) CoduxTextAction("Desligar", onDisconnect)
            else CoduxTextAction(if (connecting) "A ligar…" else "Ligar", onConnect, enabled = !connecting)
        }
    }
}

@Composable
private fun ChangesCard(files: List<String>, onShow: () -> Unit) {
    val colors = Codux.colors
    CoduxCard(Modifier.fillMaxWidth().padding(horizontal = CoduxSpace.lg, vertical = CoduxSpace.xs), onClick = onShow, elevated = true) {
        Row(Modifier.padding(CoduxSpace.md), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(CoduxShape.medium).background(colors.accent.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Code, null, tint = colors.accent, modifier = Modifier.size(18.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal = CoduxSpace.md)) {
                Text("Alterações do Codex", style = MaterialTheme.typography.titleSmall, color = colors.text)
                if (files.isNotEmpty()) {
                    Text(
                        (if (files.size == 1) "1 ficheiro: " else files.size.toString() + " ficheiros: ") + files.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text("Ver", style = MaterialTheme.typography.labelLarge, color = colors.accent)
        }
    }
}

@Composable
private fun WorkingRow(text: String) {
    val colors = Codux.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = colors.ai)
        Text(text, Modifier.padding(start = CoduxSpace.sm), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
    }
}

@Composable
private fun Composer(
    prompt: String,
    onPrompt: (String) -> Unit,
    hasInput: Boolean,
    canSend: Boolean,
    running: Boolean,
    canStop: Boolean,
    attachmentName: String?,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onAttach: () -> Unit,
    onRemoveAttachment: () -> Unit,
    onVoice: () -> Unit
) {
    val colors = Codux.colors
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(20.dp)
    Column(Modifier.fillMaxWidth().padding(horizontal = CoduxSpace.md, vertical = CoduxSpace.sm)) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(colors.surface)
                .border(1.dp, if (focused) colors.ai else colors.border, shape)
                .padding(CoduxSpace.xs)
        ) {
            if (attachmentName != null) {
                Row(Modifier.fillMaxWidth().padding(start = CoduxSpace.md), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AttachFile, null, tint = colors.ai, modifier = Modifier.size(16.dp))
                    Text(attachmentName, Modifier.weight(1f).padding(start = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium, color = colors.text)
                    IconButton(onClick = onRemoveAttachment, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.Close, "Remover anexo", tint = colors.textSecondary, modifier = Modifier.size(16.dp)) }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                IconButton(onClick = onAttach, modifier = Modifier.size(44.dp)) { Icon(Icons.Default.Add, "Anexar ficheiro", tint = colors.textSecondary) }
                BasicTextField(
                    value = prompt,
                    onValueChange = onPrompt,
                    modifier = Modifier.weight(1f).padding(vertical = 12.dp).heightIn(min = 22.dp, max = 160.dp).onFocusChanged { focused = it.isFocused },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.text),
                    cursorBrush = SolidColor(colors.ai),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (prompt.isEmpty()) Text("Pergunte algo ao Codex…", style = MaterialTheme.typography.bodyLarge, color = colors.textSecondary)
                            inner()
                        }
                    }
                )
                val buttonColors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = colors.ai,
                    contentColor = Color.White,
                    disabledContainerColor = colors.surfaceElevated,
                    disabledContentColor = colors.textSecondary
                )
                when {
                    running -> FilledIconButton(
                        onClick = onStop,
                        enabled = canStop,
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = colors.danger, contentColor = Color.White, disabledContainerColor = colors.surfaceElevated, disabledContentColor = colors.textSecondary),
                        modifier = Modifier.padding(4.dp).size(40.dp)
                    ) { Icon(Icons.Default.Stop, "Parar") }
                    hasInput -> FilledIconButton(onClick = onSend, enabled = canSend, colors = buttonColors, modifier = Modifier.padding(4.dp).size(40.dp)) {
                        Icon(Icons.AutoMirrored.Filled.Send, "Enviar")
                    }
                    else -> IconButton(onClick = onVoice, modifier = Modifier.size(44.dp)) { Icon(Icons.Default.Mic, "Ditado por voz", tint = colors.textSecondary) }
                }
            }
        }
        Text(
            "O Codex pode cometer erros. Verifica as alterações antes de as guardar.",
            Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary.copy(alpha = .8f)
        )
    }
}

@Composable
internal fun MessageRow(message: ChatMessage) {
    val colors = Codux.colors
    when (message.role) {
        MessageRole.USER -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Column(
                Modifier
                    .widthIn(max = 560.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp))
                    .background(lerp(colors.surface.copy(alpha = 1f), colors.accent, .22f))
                    .border(1.dp, colors.accent.copy(alpha = .35f), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp))
                    .padding(horizontal = CoduxSpace.lg, vertical = CoduxSpace.md)
            ) {
                Text("Você", style = MaterialTheme.typography.labelMedium, color = colors.accent)
                Text(message.text, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodyLarge, color = colors.text)
            }
        }
        MessageRole.ASSISTANT -> Row(Modifier.fillMaxWidth()) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(colors.ai.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.AutoAwesome, null, tint = colors.ai, modifier = Modifier.size(16.dp))
            }
            Column(Modifier.weight(1f).padding(start = CoduxSpace.md)) {
                Text("Codex", style = MaterialTheme.typography.labelMedium, color = colors.ai)
                MarkdownContent(message.text, Modifier.padding(top = 4.dp))
                if (message.isStreaming) {
                    Spacer(Modifier.height(CoduxSpace.sm))
                    WorkingRow("A responder…")
                }
            }
        }
        MessageRole.SYSTEM -> Row(
            Modifier
                .fillMaxWidth()
                .clip(CoduxShape.medium)
                .background(colors.surface)
                .border(1.dp, colors.border, CoduxShape.medium)
                .padding(CoduxSpace.md),
            verticalAlignment = Alignment.Top
        ) {
            Icon(Icons.Default.Info, null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
            Text(message.text, Modifier.padding(start = CoduxSpace.sm), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        }
    }
}

@Composable
private fun MarkdownContent(text: String, modifier: Modifier = Modifier) {
    val fence = Char(96).toString().repeat(3)
    val parts = remember(text) { text.split(fence) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        parts.forEachIndexed { index, part ->
            if (index % 2 == 0) {
                MarkdownText(part)
            } else {
                val lineBreak = part.indexOf('\n')
                val info = if (lineBreak >= 0) part.substring(0, lineBreak).trim() else ""
                val code = if (lineBreak >= 0) part.substring(lineBreak + 1).trimEnd() else part
                CodeBlock(code, info)
            }
        }
    }
}

@Composable
private fun MarkdownText(part: String) {
    val lines = remember(part) { part.lines() }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        var index = 0
        while (index < lines.size) {
            if (lines[index].trimStart().startsWith("|")) {
                val start = index
                while (index < lines.size && lines[index].trimStart().startsWith("|")) index++
                MarkdownTable(lines.subList(start, index))
            } else {
                MarkdownLine(lines[index])
                index++
            }
        }
    }
}

@Composable
private fun MarkdownLine(line: String) {
    val colors = Codux.colors
    val style = MaterialTheme.typography
    val numbered = NumberedLine.find(line)
    when {
        line.startsWith("### ") -> Text(inlineMarkdown(line.drop(4), colors), style = style.titleSmall, color = colors.text)
        line.startsWith("## ") -> Text(inlineMarkdown(line.drop(3), colors), style = style.titleMedium, color = colors.text)
        line.startsWith("# ") -> Text(inlineMarkdown(line.drop(2), colors), style = style.titleLarge, color = colors.text)
        line.startsWith("- ") || line.startsWith("* ") -> Row {
            Text("•", Modifier.padding(end = 8.dp), color = colors.accent)
            Text(inlineMarkdown(line.drop(2), colors), style = style.bodyLarge, color = colors.text)
        }
        numbered != null -> Row {
            Text(numbered.groupValues[1] + ".", Modifier.padding(end = 8.dp), color = colors.accent, style = style.bodyLarge)
            Text(inlineMarkdown(numbered.groupValues[2], colors), style = style.bodyLarge, color = colors.text)
        }
        line.startsWith("> ") -> Row(Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
            Box(Modifier.width(3.dp).fillMaxSizeHeight().background(colors.border))
            Text(inlineMarkdown(line.drop(2), colors), Modifier.padding(start = CoduxSpace.sm), style = style.bodyLarge, color = colors.textSecondary)
        }
        line.isNotBlank() -> Text(inlineMarkdown(line, colors), style = style.bodyLarge, color = colors.text)
        else -> Spacer(Modifier.height(2.dp))
    }
}

private fun Modifier.fillMaxSizeHeight(): Modifier = this.then(Modifier.heightIn(min = 20.dp))

@Composable
private fun MarkdownTable(rows: List<String>) {
    val colors = Codux.colors
    val cells = remember(rows) {
        rows.map { row -> row.trim().trim('|').split('|').map { it.trim() } }
            .filterNot { cellRow -> cellRow.all { it.matches(TableSeparatorCell) } }
    }
    if (cells.isEmpty()) return
    val columns = cells.maxOf { it.size }
    Column(
        Modifier
            .clip(CoduxShape.medium)
            .border(1.dp, colors.border, CoduxShape.medium)
            .horizontalScroll(rememberScrollState())
    ) {
        cells.forEachIndexed { rowIndex, row ->
            Row(Modifier.background(if (rowIndex == 0) colors.surfaceElevated else Color.Transparent)) {
                for (column in 0 until columns) {
                    Text(
                        row.getOrElse(column) { "" },
                        Modifier.width(140.dp).padding(horizontal = CoduxSpace.md, vertical = CoduxSpace.sm),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (rowIndex == 0) FontWeight.SemiBold else FontWeight.Normal,
                        color = colors.text
                    )
                }
            }
            if (rowIndex < cells.lastIndex) Box(Modifier.width((140 * columns).dp).height(1.dp).background(colors.border))
        }
    }
}

private fun inlineMarkdown(text: String, colors: CoduxPalette): AnnotatedString {
    val tick = Char(96)
    val tokenPattern = Regex("\\*\\*[^*]+\\*\\*|" + tick + "[^" + tick + "]+" + tick)
    return buildAnnotatedString {
        var cursor = 0
        tokenPattern.findAll(text).forEach { match ->
            append(text.substring(cursor, match.range.first))
            val value = match.value
            if (value.startsWith("**")) withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                append(value.removePrefix("**").removeSuffix("**"))
            } else withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, background = colors.accent.copy(alpha = .14f), color = colors.text)) {
                append(value.drop(1).dropLast(1))
            }
            cursor = match.range.last + 1
        }
        append(text.substring(cursor))
    }
}

/** Bloco de código: linguagem, nome do ficheiro (formato "kotlin:Ficheiro.kt"), realce de sintaxe e copiar. */
@Composable
private fun CodeBlock(code: String, info: String) {
    val colors = Codux.colors
    val clipboard = LocalClipboardManager.current
    var expanded by remember(code) { mutableStateOf(false) }
    var copied by remember(code) { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }
    val languageName = info.substringBefore(':').trim()
    val fileName = info.substringAfter(':', "").trim().ifBlank { null }
    val palette = if (colors.dark) DarkSyntaxPalette else LightSyntaxPalette
    val highlighted = remember(code, languageName, colors.dark) { CodeSyntax.highlight(code, CodeSyntax.forLanguageName(languageName), palette) }
    val long = remember(code) { code.count { it == '\n' } > 14 }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(CoduxShape.medium)
            .background(if (colors.dark) colors.backgroundAlt else colors.surfaceElevated)
            .border(1.dp, colors.border, CoduxShape.medium)
    ) {
        Row(
            Modifier.fillMaxWidth().background(colors.surface).padding(start = CoduxSpace.md, end = CoduxSpace.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(languageName.ifBlank { "código" }, style = MaterialTheme.typography.labelMedium, color = colors.ai)
            if (fileName != null) Text(fileName, Modifier.padding(start = CoduxSpace.sm), style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.weight(1f))
            if (long) CoduxTextAction(if (expanded) "Recolher" else "Expandir", { expanded = !expanded })
            IconButton(onClick = { clipboard.setText(AnnotatedString(code)); copied = true }, modifier = Modifier.size(40.dp)) {
                Icon(if (copied) Icons.Default.Check else Icons.Default.ContentCopy, if (copied) "Copiado" else "Copiar código", tint = if (copied) colors.success else colors.textSecondary, modifier = Modifier.size(18.dp))
            }
        }
        Text(
            highlighted,
            Modifier
                .fillMaxWidth()
                .heightIn(max = if (expanded) 640.dp else 280.dp)
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = CoduxSpace.md, vertical = CoduxSpace.md),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 20.sp),
            color = palette.plain
        )
    }
}
