package dev.codux

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Ícone de barra lateral dos desenhos: retângulo com a parte esquerda preenchida. */
@Composable
internal fun SidebarToggle(onClick: () -> Unit) {
    val color = MaterialTheme.colorScheme.onSurface
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(width = 28.dp, height = 18.dp)) {
            val stroke = 2.dp.toPx()
            drawRoundRect(
                color = color,
                topLeft = Offset(stroke / 2f, stroke / 2f),
                size = Size(size.width - stroke, size.height - stroke),
                cornerRadius = CornerRadius(4.dp.toPx()),
                style = Stroke(width = stroke)
            )
            drawRoundRect(
                color = color,
                topLeft = Offset(stroke * 2f, stroke * 2f),
                size = Size(size.width * .16f, size.height - stroke * 4f),
                cornerRadius = CornerRadius(1.dp.toPx())
            )
        }
    }
}

/** Pílula com o título em itálico, como nos desenhos ("PROJETOS CODUX_"). */
@Composable
internal fun TitlePill(text: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(22.dp)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.background,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface),
        modifier = modifier.height(46.dp)
    ) {
        Box(Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            Text(text, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Pílula dividida do editor: "Editor Codux" à esquerda e o nome do projeto à direita. */
@Composable
internal fun SplitTitlePill(left: String, right: String, modifier: Modifier = Modifier, onRightClick: () -> Unit) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.background,
        border = BorderStroke(2.dp, onSurface),
        modifier = modifier.height(46.dp)
    ) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.background,
                border = BorderStroke(2.dp, onSurface),
                modifier = Modifier.weight(1f).fillMaxHeight()
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(left, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight().clickable(onClick = onRightClick), contentAlignment = Alignment.Center) {
                Text(right, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 8.dp))
            }
        }
    }
}

/** Cabeçalho comum: botão da barra lateral e pílula de título. [cursor] acrescenta o "_" dos desenhos. */
@Composable
internal fun CoduxHeader(title: String, onMenu: () -> Unit, cursor: Boolean = true, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SidebarToggle(onMenu)
        TitlePill(title.uppercase() + if (cursor) "_" else "", Modifier.weight(1f))
        trailing()
    }
}

@Composable
internal fun StatusIndicator(connected: Boolean, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (connected) Color(0xFF32A879) else MaterialTheme.colorScheme.outline))
        Text(label, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .68f))
    }
}

@Composable
internal fun EmptyStateCard(title: String, message: String, action: String, onAction: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .42f))
    ) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
            Text(title, Modifier.padding(top = 10.dp), style = MaterialTheme.typography.titleMedium)
            Text(message, Modifier.padding(top = 5.dp, bottom = 14.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f))
            Button(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
internal fun ErrorBanner(text: String, onDismiss: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, Modifier.weight(1f).padding(vertical = 10.dp), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Fechar aviso", tint = MaterialTheme.colorScheme.onErrorContainer) }
        }
    }
}
