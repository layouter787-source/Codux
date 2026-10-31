package dev.codux

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
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
    onMenu: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    val messageListState = rememberLazyListState()
    LaunchedEffect(state.messages.lastOrNull()?.id, state.messages.lastOrNull()?.text, state.messages.size, state.isTurnRunning) {
        val target = if (state.isTurnRunning && state.messages.lastOrNull()?.isStreaming != true) state.messages.size else state.messages.lastIndex
        if (state.messages.isNotEmpty() && target >= 0) messageListState.scrollToItem(target)
    }
    Column(Modifier.fillMaxSize()) {
        CoduxHeader("Agente Codux", onMenu)
        AgentCard(state.connected, state.connecting, onConnect, onDisconnect)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (state.messages.isNotEmpty()) {
                LazyColumn(state = messageListState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(26.dp)) {
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
        }
        if (state.currentDiff.isNotBlank()) {
            TextButton(onClick = onShowDiff, modifier = Modifier.align(Alignment.CenterHorizontally)) { Icon(Icons.Default.Code, null); Text("Ver alterações") }
        }
        val hasInput = prompt.isNotBlank() || attachmentName != null
        Column(Modifier.widthIn(max = 800.dp).fillMaxWidth().align(Alignment.CenterHorizontally).padding(horizontal = 12.dp, vertical = 8.dp)) {
            Surface(
                color = MaterialTheme.colorScheme.background,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface)
            ) {
                Column(Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
                    if (attachmentName != null) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AttachFile, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Text(attachmentName, Modifier.weight(1f).padding(start = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                            IconButton(onClick = onRemoveAttachment, modifier = Modifier.size(34.dp)) { Icon(Icons.Default.Close, "Remover anexo", modifier = Modifier.size(16.dp)) }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onAttach, modifier = Modifier.size(44.dp)) { Icon(Icons.Default.Add, "Anexar ficheiro") }
                        BasicTextField(
                            value = prompt,
                            onValueChange = onPrompt,
                            modifier = Modifier.weight(1f).padding(vertical = 10.dp).heightIn(min = 24.dp, max = 150.dp),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface, fontStyle = FontStyle.Italic),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { if (hasInput && state.connected && !state.isTurnRunning) onSend() }),
                            decorationBox = { inner ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (prompt.isBlank()) Text("Programe com o codux_", fontStyle = FontStyle.Italic, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
                                    inner()
                                }
                            }
                        )
                        if (state.isTurnRunning || hasInput) {
                            FilledIconButton(
                                onClick = if (state.isTurnRunning) onStop else onSend,
                                enabled = if (state.isTurnRunning) state.activeTurnId != null else (hasInput && state.connected),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(if (state.isTurnRunning) Icons.Default.Stop else Icons.AutoMirrored.Filled.Send, if (state.isTurnRunning) "Parar" else "Enviar")
                            }
                            Spacer(Modifier.width(2.dp))
                        } else {
                            IconButton(onClick = onVoice, modifier = Modifier.size(44.dp)) { Icon(Icons.Default.Mic, "Ditado por voz") }
                        }
                    }
                }
            }
            Text(
                "O Codex pode cometer erros. Verifica as alterações antes de as guardar.",
                Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp, bottom = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f)
            )
        }
    }
}

/**
 * Cartão "Agente escolhido / Modelo" do desenho. O Codux só liga hoje ao Codex; o Claude Code aparece
 * na lista como "em breve" para não fingir uma integração que ainda não existe.
 */
@Composable
private fun AgentCard(connected: Boolean, connecting: Boolean, onConnect: () -> Unit, onDisconnect: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp)) {
        Surface(
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.background,
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).clickable { menuOpen = true }
        ) {
            Row(Modifier.padding(start = 22.dp, end = 8.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Agente escolhido: Codex", fontStyle = FontStyle.Italic, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("Modelo: padrão do Codex", fontStyle = FontStyle.Italic, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                }
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (connected) Color(0xFF38A87A) else MaterialTheme.colorScheme.outline))
                if (connected) TextButton(onClick = onDisconnect) { Text("Desligar") }
                else TextButton(onClick = onConnect, enabled = !connecting) { Text(if (connecting) "A ligar…" else "Ligar") }
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(text = { Text("Codex (ativo)") }, onClick = { menuOpen = false })
            DropdownMenuItem(text = { Text("Claude Code — em breve") }, onClick = { menuOpen = false }, enabled = false)
        }
    }
}

@Composable
internal fun MessageRow(message: ChatMessage) {
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
    Surface(
        shape = RoundedCornerShape(15.dp),
        color = if (isSystemInDarkTheme()) Color(0xFF111722) else Color(0xFFF0F3F8),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .35f))
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(language.ifBlank { "Código" }, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f))
                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Recolher" else "Expandir") }
                IconButton(onClick = { clipboard.setText(AnnotatedString(code)) }, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.ContentCopy, "Copiar código", modifier = Modifier.size(16.dp)) }
            }
            Text(
                code,
                Modifier.fillMaxWidth().heightIn(max = if (expanded) 620.dp else 220.dp).verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
