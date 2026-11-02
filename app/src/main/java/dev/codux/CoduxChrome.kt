package dev.codux

import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ───────────────────────────── Tokens ─────────────────────────────

/** Paleta do Codux. As cores de acento são fixas; o resto muda entre tema escuro e claro. */
@Immutable
internal class CoduxPalette(
    val dark: Boolean,
    val background: Color,
    val backgroundAlt: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val border: Color,
    val text: Color,
    val textSecondary: Color,
    /** Cor principal de interação (azul por omissão; o utilizador pode trocar nas Definições). */
    val accent: Color,
    /** Elementos ligados à inteligência artificial. */
    val ai: Color,
    /** Conexão estabelecida, execução concluída, compilação bem-sucedida. */
    val success: Color,
    val warning: Color,
    val danger: Color
)

internal val CoduxBlue = Color(0xFF2878F0)
internal val CoduxPurple = Color(0xFF8055F5)
internal val CoduxGreen = Color(0xFF22C983)
internal val CoduxOrange = Color(0xFFF59E42)
internal val CoduxRed = Color(0xFFEF5350)

internal fun coduxPalette(dark: Boolean, accent: Color, transparency: Float): CoduxPalette {
    // A transparência dos painéis deixa o fundo transparecer ligeiramente; nunca abaixo de 70 % de opacidade.
    val panelAlpha = (1f - transparency).coerceIn(.7f, 1f)
    return if (dark) {
        CoduxPalette(
            dark = true,
            background = Color(0xFF080D16),
            backgroundAlt = Color(0xFF0D1420),
            surface = Color(0xFF111B2A).copy(alpha = panelAlpha),
            surfaceElevated = Color(0xFF172235).copy(alpha = panelAlpha),
            border = Color(0xFF243248),
            text = Color(0xFFF1F5FC),
            textSecondary = Color(0xFF94A3B8),
            accent = accent,
            ai = CoduxPurple,
            success = CoduxGreen,
            warning = CoduxOrange,
            danger = CoduxRed
        )
    } else {
        CoduxPalette(
            dark = false,
            background = Color(0xFFF4F6FA),
            backgroundAlt = Color(0xFFEBEFF6),
            surface = Color(0xFFFFFFFF).copy(alpha = panelAlpha),
            surfaceElevated = Color(0xFFF1F4F9).copy(alpha = panelAlpha),
            border = Color(0xFFD8DFEA),
            text = Color(0xFF0F172A),
            textSecondary = Color(0xFF5B6B82),
            accent = accent,
            ai = CoduxPurple,
            success = Color(0xFF16A06C),
            warning = Color(0xFFD9822B),
            danger = Color(0xFFD94745)
        )
    }
}

internal val LocalCoduxPalette = staticCompositionLocalOf { coduxPalette(true, CoduxBlue, 0f) }

internal object Codux {
    val colors: CoduxPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalCoduxPalette.current
}

/** Escala de espaçamento. */
internal object CoduxSpace {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
}

/** Cantos moderados. */
internal object CoduxShape {
    val small = RoundedCornerShape(8.dp)
    val medium = RoundedCornerShape(12.dp)
    val large = RoundedCornerShape(16.dp)
}

/** Largura a partir da qual a navegação passa a ser lateral e os painéis se dividem. */
internal val CoduxWideBreakpoint = 600.dp

internal fun coduxColorScheme(p: CoduxPalette): ColorScheme {
    val accentContainer = lerp(p.surface.copy(alpha = 1f), p.accent, if (p.dark) .28f else .16f)
    return if (p.dark) {
        darkColorScheme(
            primary = p.accent,
            onPrimary = Color.White,
            primaryContainer = accentContainer,
            onPrimaryContainer = p.text,
            secondary = p.textSecondary,
            onSecondary = p.background,
            secondaryContainer = p.surfaceElevated.copy(alpha = 1f),
            onSecondaryContainer = p.text,
            tertiary = p.ai,
            onTertiary = Color.White,
            error = p.danger,
            onError = Color.White,
            errorContainer = lerp(p.background, p.danger, .22f),
            onErrorContainer = p.text,
            background = p.background,
            onBackground = p.text,
            surface = p.surface.copy(alpha = 1f),
            onSurface = p.text,
            surfaceVariant = p.surfaceElevated.copy(alpha = 1f),
            onSurfaceVariant = p.textSecondary,
            outline = p.border,
            outlineVariant = p.border,
            surfaceContainerLowest = p.background,
            surfaceContainerLow = p.backgroundAlt,
            surfaceContainer = p.surfaceElevated.copy(alpha = 1f),
            surfaceContainerHigh = p.surfaceElevated.copy(alpha = 1f),
            surfaceContainerHighest = p.surfaceElevated.copy(alpha = 1f)
        )
    } else {
        lightColorScheme(
            primary = p.accent,
            onPrimary = Color.White,
            primaryContainer = accentContainer,
            onPrimaryContainer = p.text,
            secondary = p.textSecondary,
            onSecondary = Color.White,
            secondaryContainer = p.surfaceElevated.copy(alpha = 1f),
            onSecondaryContainer = p.text,
            tertiary = p.ai,
            onTertiary = Color.White,
            error = p.danger,
            onError = Color.White,
            errorContainer = lerp(p.background, p.danger, .14f),
            onErrorContainer = p.text,
            background = p.background,
            onBackground = p.text,
            surface = p.surface.copy(alpha = 1f),
            onSurface = p.text,
            surfaceVariant = p.surfaceElevated.copy(alpha = 1f),
            onSurfaceVariant = p.textSecondary,
            outline = p.border,
            outlineVariant = p.border,
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = p.backgroundAlt,
            surfaceContainer = p.surfaceElevated.copy(alpha = 1f),
            surfaceContainerHigh = p.surfaceElevated.copy(alpha = 1f),
            surfaceContainerHighest = p.surfaceElevated.copy(alpha = 1f)
        )
    }
}

/** Hierarquia tipográfica: títulos, secções, subtítulos, texto, auxiliar. O texto técnico usa FontFamily.Monospace. */
internal fun coduxTypography(): Typography = Typography(
    headlineMedium = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium)
)

// ─────────────────────── Movimento e acessibilidade ───────────────────────

/** Verdadeiro quando o utilizador desativou as animações do sistema (redução de movimento). */
@Composable
internal fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

@Composable
internal fun <T> coduxAnimation(millis: Int = 160): AnimationSpec<T> =
    if (rememberReducedMotion()) snap() else tween(millis)

// ───────────────────────────── Componentes ─────────────────────────────

/** Cabeçalho compacto de cada setor: ícone, título, subtítulo opcional e ações à direita. */
@Composable
internal fun CoduxTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = Codux.colors.accent,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val colors = Codux.colors
    Row(
        modifier.fillMaxWidth().padding(horizontal = CoduxSpace.lg, vertical = CoduxSpace.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                Modifier.size(36.dp).clip(CoduxShape.medium).background(iconTint.copy(alpha = .14f)),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = iconTint, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.width(CoduxSpace.md))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        actions()
    }
}

@Composable
internal fun CoduxIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Codux.colors.textSecondary
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = modifier.size(40.dp)) {
        Icon(icon, description, tint = if (enabled) tint else tint.copy(alpha = .38f), modifier = Modifier.size(22.dp))
    }
}

@Composable
internal fun CoduxPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    color: Color = Codux.colors.accent
) {
    val colors = Codux.colors
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 44.dp),
        shape = CoduxShape.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = Color.White,
            disabledContainerColor = colors.surfaceElevated,
            disabledContentColor = colors.textSecondary
        ),
        contentPadding = PaddingValues(horizontal = CoduxSpace.lg, vertical = CoduxSpace.sm)
    ) {
        if (icon != null) {
            Icon(icon, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(CoduxSpace.sm))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
internal fun CoduxSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    val colors = Codux.colors
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 44.dp),
        shape = CoduxShape.medium,
        border = BorderStroke(1.dp, colors.border),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.text, disabledContentColor = colors.textSecondary),
        contentPadding = PaddingValues(horizontal = CoduxSpace.lg, vertical = CoduxSpace.sm)
    ) {
        if (icon != null) {
            Icon(icon, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(CoduxSpace.sm))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
internal fun CoduxTextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    TextButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (enabled) Codux.colors.accent else Codux.colors.textSecondary)
    }
}

/** Campo de pesquisa compacto, com ícone, limpar e realce quando em foco. */
@Composable
internal fun CoduxSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    val colors = Codux.colors
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(CoduxShape.medium)
            .background(colors.surface)
            .border(1.dp, if (focused) colors.accent else colors.border, CoduxShape.medium)
            .padding(start = CoduxSpace.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Search, null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.text),
            cursorBrush = SolidColor(colors.accent),
            modifier = Modifier.weight(1f).padding(horizontal = CoduxSpace.md).onFocusChanged { focused = it.isFocused },
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary, maxLines = 1)
                    inner()
                }
            }
        )
        if (value.isNotEmpty()) {
            IconButton(onClick = { onValueChange("") }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Close, "Limpar pesquisa", tint = colors.textSecondary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
internal fun CoduxTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true
) {
    val colors = Codux.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        modifier = modifier,
        shape = CoduxShape.medium,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.accent,
            unfocusedBorderColor = colors.border,
            focusedLabelColor = colors.accent,
            unfocusedLabelColor = colors.textSecondary,
            cursorColor = colors.accent,
            focusedTextColor = colors.text,
            unfocusedTextColor = colors.text
        )
    )
}

/** Cartão base. Com [onClick] torna-se clicável (com feedback de toque). */
@Composable
internal fun CoduxCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    elevated: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = Codux.colors
    val color = if (elevated) colors.surfaceElevated else colors.surface
    val border = BorderStroke(1.dp, colors.border)
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = CoduxShape.large, color = color, border = border) {
            Column(content = content)
        }
    } else {
        Surface(modifier = modifier, shape = CoduxShape.large, color = color, border = border) {
            Column(content = content)
        }
    }
}

/** Filtro / etiqueta selecionável. */
@Composable
internal fun CoduxChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    val colors = Codux.colors
    val container by animateColorAsState(if (selected) colors.accent.copy(alpha = .18f) else colors.surface, coduxAnimation(), label = "chipContainer")
    val outline by animateColorAsState(if (selected) colors.accent else colors.border, coduxAnimation(), label = "chipBorder")
    Row(
        modifier
            .height(36.dp)
            .clip(CoduxShape.medium)
            .background(container)
            .border(1.dp, outline, CoduxShape.medium)
            .selectable(selected = selected, onClick = onClick, role = Role.Tab)
            .padding(horizontal = CoduxSpace.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (selected) colors.accent else colors.textSecondary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) colors.text else colors.textSecondary, maxLines = 1)
    }
}

/** Pequena etiqueta informativa (stack, linguagem). */
@Composable
internal fun CoduxTag(label: String, color: Color = Codux.colors.accent) {
    Text(
        label,
        modifier = Modifier.clip(CoduxShape.small).background(color.copy(alpha = .14f)).padding(horizontal = 8.dp, vertical = 3.dp),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        maxLines = 1
    )
}

@Composable
internal fun StatusDot(color: Color, size: Dp = 8.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

@Composable
internal fun StatusIndicator(connected: Boolean, label: String) {
    val colors = Codux.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusDot(if (connected) colors.success else colors.textSecondary)
        Text(label, Modifier.padding(start = CoduxSpace.sm), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
    }
}

@Composable
internal fun CoduxSectionHeader(title: String, modifier: Modifier = Modifier, actionLabel: String? = null, onAction: () -> Unit = {}) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = Codux.colors.text)
        if (actionLabel != null) CoduxTextAction(actionLabel, onAction)
    }
}

/** Estado vazio: ilustração minimalista, mensagem e ações. */
@Composable
internal fun CoduxEmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    secondaryLabel: String? = null,
    onSecondary: () -> Unit = {}
) {
    val colors = Codux.colors
    Column(modifier.fillMaxWidth().padding(CoduxSpace.xl), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(84.dp).clip(CircleShape).background(colors.accent.copy(alpha = .08f)).border(1.dp, colors.border, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(colors.accent.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = colors.accent, modifier = Modifier.size(28.dp))
            }
        }
        Text(title, Modifier.padding(top = CoduxSpace.lg), style = MaterialTheme.typography.titleMedium, color = colors.text, textAlign = TextAlign.Center)
        Text(message, Modifier.padding(top = CoduxSpace.xs, bottom = CoduxSpace.lg), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary, textAlign = TextAlign.Center)
        if (actionLabel != null) CoduxPrimaryButton(actionLabel, onAction)
        if (secondaryLabel != null) {
            Spacer(Modifier.height(CoduxSpace.sm))
            CoduxSecondaryButton(secondaryLabel, onSecondary)
        }
    }
}

@Composable
internal fun ErrorBanner(text: String, onDismiss: () -> Unit) {
    val colors = Codux.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.danger.copy(alpha = .14f))
            .padding(start = CoduxSpace.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.ErrorOutline, null, tint = colors.danger, modifier = Modifier.size(20.dp))
        Text(text, Modifier.weight(1f).padding(horizontal = CoduxSpace.md, vertical = CoduxSpace.md), color = colors.text, style = MaterialTheme.typography.bodySmall)
        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Fechar aviso", tint = colors.textSecondary) }
    }
}

// ───────────────────────────── Navegação ─────────────────────────────

internal class CoduxNavItem(val label: String, val icon: ImageVector)

/** Barra inferior compacta para smartphones. */
@Composable
internal fun CoduxBottomBar(items: List<CoduxNavItem>, selected: Int, onSelect: (Int) -> Unit) {
    val colors = Codux.colors
    Column(Modifier.fillMaxWidth().background(colors.backgroundAlt)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
        Row(Modifier.fillMaxWidth().padding(horizontal = CoduxSpace.xs)) {
            items.forEachIndexed { index, item ->
                NavBarItem(item, index == selected, Modifier.weight(1f)) { onSelect(index) }
            }
        }
    }
}

@Composable
private fun NavBarItem(item: CoduxNavItem, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = Codux.colors
    val tint by animateColorAsState(if (selected) colors.accent else colors.textSecondary, coduxAnimation(), label = "navTint")
    val indicator by animateColorAsState(if (selected) colors.accent.copy(alpha = .16f) else Color.Transparent, coduxAnimation(), label = "navIndicator")
    Column(
        modifier.selectable(selected = selected, onClick = onClick, role = Role.Tab).padding(top = 8.dp, bottom = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(width = 52.dp, height = 28.dp).clip(RoundedCornerShape(14.dp)).background(indicator), contentAlignment = Alignment.Center) {
            Icon(item.icon, null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Text(
            item.label,
            Modifier.padding(top = 2.dp),
            color = tint,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1
        )
    }
}

/** Navegação lateral para tablets e ecrãs largos. */
@Composable
internal fun CoduxNavRail(
    items: List<CoduxNavItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    logo: ImageVector
) {
    val colors = Codux.colors
    Row(Modifier.fillMaxHeight().background(colors.backgroundAlt)) {
        Column(Modifier.width(84.dp).fillMaxHeight().padding(vertical = CoduxSpace.md), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(40.dp).clip(CoduxShape.medium).background(colors.accent.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
                Icon(logo, null, tint = colors.accent, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.height(CoduxSpace.lg))
            items.forEachIndexed { index, item ->
                NavBarItem(item, index == selected, Modifier.fillMaxWidth().padding(vertical = 2.dp)) { onSelect(index) }
            }
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(colors.border))
    }
}
