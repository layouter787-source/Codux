package dev.codux

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.codux.data.AppServerLog
import dev.codux.data.AppState

internal val AccentOptions = listOf("blue", "violet", "mint", "orange", "rose")

internal fun coduxAccent(key: String): Color = when (key) {
    "violet" -> Color(0xFF9B7BF7)
    "mint" -> CoduxGreen
    "orange" -> CoduxOrange
    "rose" -> Color(0xFFE0568A)
    else -> CoduxBlue
}

private fun accentLabel(key: String): String = when (key) {
    "violet" -> "Violeta"
    "mint" -> "Menta"
    "orange" -> "Laranja"
    "rose" -> "Rosa"
    else -> "Azul"
}

@Composable
internal fun SettingsScreen(
    state: AppState,
    onTheme: (String) -> Unit,
    onAccent: (String) -> Unit,
    onTextScale: (Float) -> Unit,
    onTransparency: (Float) -> Unit,
    onWorkspace: (String) -> Unit,
    onTimeout: (Int) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onHelp: () -> Unit
) {
    val colors = Codux.colors
    val context = LocalContext.current
    var path by remember(state.workspacePath) { mutableStateOf(state.workspacePath) }
    val serverLog by AppServerLog.text.collectAsStateWithLifecycle()
    val version = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "—"
    }
    val sliderColors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent, inactiveTrackColor = colors.border)

    Column(Modifier.fillMaxSize()) {
        CoduxTopBar("Definições", subtitle = "Aparência, agente, projeto e terminal", icon = Icons.Default.Tune)
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = 720.dp).fillMaxSize(),
                contentPadding = PaddingValues(CoduxSpace.lg),
                verticalArrangement = Arrangement.spacedBy(CoduxSpace.md)
            ) {
                item {
                    SettingsGroup("Aparência", Icons.Default.Palette) {
                        SettingLabel("Tema")
                        Row(horizontalArrangement = Arrangement.spacedBy(CoduxSpace.sm)) {
                            listOf("system" to "Sistema", "light" to "Claro", "dark" to "Escuro").forEach { (value, label) ->
                                CoduxChip(label, state.themeMode == value, { onTheme(value) })
                            }
                        }
                        SettingLabel("Cor de destaque")
                        Row(horizontalArrangement = Arrangement.spacedBy(CoduxSpace.md), verticalAlignment = Alignment.CenterVertically) {
                            AccentOptions.forEach { key ->
                                val selected = state.accentColor == key
                                Box(
                                    Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(coduxAccent(key))
                                        .border(if (selected) 3.dp else 0.dp, if (selected) colors.text else Color.Transparent, CircleShape)
                                        .selectable(selected = selected, onClick = { onAccent(key) }, role = Role.RadioButton)
                                ) { Text(accentLabel(key), Modifier.size(0.dp)) }
                            }
                        }
                        SettingLabel("Tamanho do texto: " + (state.textScale * 100).toInt() + "%")
                        Slider(value = state.textScale, onValueChange = onTextScale, valueRange = .85f..1.25f, colors = sliderColors)
                        SettingLabel("Transparência dos painéis: " + (state.transparency * 100).toInt() + "%")
                        Slider(value = state.transparency, onValueChange = onTransparency, valueRange = 0f..0.35f, colors = sliderColors)
                    }
                }
                item {
                    SettingsGroup("Agente IA", Icons.Default.AutoAwesome) {
                        StatusIndicator(state.connected, state.connectionMessage)
                        Text(
                            if (state.connected) "Codex App Server ativo" else "Codex CLI ainda não ligado",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.text
                        )
                        if (state.connected) CoduxSecondaryButton("Desligar", onDisconnect)
                        else CoduxPrimaryButton(if (state.connecting) "A ligar…" else "Ligar ao Codex integrado", onConnect, enabled = !state.connecting, color = colors.ai)
                        SettingHint("O runtime Linux e o Codex CLI ARM64 estão incluídos no Codux. Na primeira utilização, abre o Terminal, executa codex login --device-auth, conclui o acesso pelo código no browser e volta aqui para ligar.")
                        if (serverLog.isNotBlank()) {
                            SettingLabel("Registo do Codex")
                            Text(
                                AppServerLog.tail(30),
                                Modifier
                                    .fillMaxWidth()
                                    .clip(CoduxShape.medium)
                                    .background(colors.backgroundAlt)
                                    .border(1.dp, colors.border, CoduxShape.medium)
                                    .heightIn(max = 220.dp)
                                    .verticalScroll(rememberScrollState())
                                    .padding(CoduxSpace.md),
                                color = colors.text,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace
                            )
                            CoduxTextAction("Limpar registo", { AppServerLog.clear() })
                        }
                    }
                }
                item {
                    SettingsGroup("Projetos", Icons.Default.Folder) {
                        SettingLabel("Caminho de trabalho no runtime integrado")
                        CoduxTextField(path, { path = it }, "Caminho absoluto", Modifier.fillMaxWidth())
                        CoduxSecondaryButton("Guardar caminho", { onWorkspace(path) }, Modifier.align(Alignment.End))
                        SettingHint("A pasta visual é escolhida em Projetos através do seletor do sistema. O caminho acima é usado pelo processo do Codex.")
                    }
                }
                item {
                    SettingsGroup("Terminal", Icons.Default.Terminal) {
                        SettingLabel("Limite de execução: " + state.terminalTimeoutSeconds + " segundos")
                        Slider(value = state.terminalTimeoutSeconds.toFloat(), onValueChange = { onTimeout(it.toInt()) }, valueRange = 10f..3600f, steps = 35, colors = sliderColors)
                        SettingHint("Limite usado pelos comandos que o Codex executa em nome do agente.")
                    }
                }
                item {
                    SettingsGroup("Sobre", Icons.Default.Info) {
                        Text("Codux · versão $version", style = MaterialTheme.typography.bodyMedium, color = colors.text)
                        SettingHint("Ambiente de desenvolvimento para Android com agente IA, terminal, editor e gestão de projetos. Construído com Jetpack Compose e Material 3.")
                        CoduxSecondaryButton("Ver ajuda", onHelp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsGroup(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    val colors = Codux.colors
    CoduxCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(CoduxSpace.lg), verticalArrangement = Arrangement.spacedBy(CoduxSpace.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = colors.accent, modifier = Modifier.size(18.dp))
                Text(title, Modifier.padding(start = CoduxSpace.sm), style = MaterialTheme.typography.titleSmall, color = colors.text)
            }
            content()
        }
    }
}

@Composable
private fun SettingLabel(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = Codux.colors.textSecondary)
}

@Composable
private fun SettingHint(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = Codux.colors.textSecondary.copy(alpha = .85f))
}
