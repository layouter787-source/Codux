package dev.codux

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.codux.data.AppState
import dev.codux.data.ProjectFiles
import dev.codux.data.ProjectWorkspace
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val ignoredDirectories = setOf("node_modules", ".git", "build", ".gradle", "dist", "venv", ".venv", ".idea", "__pycache__", "target", ".next")

/** Rótulo mostrado e chave de filtro. "Outros" agrupa os projetos sem plataforma reconhecida. */
private val platformFilters = listOf(
    "Todos" to "Todos",
    "Android" to "Android",
    "Web" to "Web",
    "PC / Desktop" to "PC",
    "Outros" to "Outros"
)

/** Percorre o projeto (poucas pastas, sem entrar em dependências) e deduz plataforma e stacks. */
private suspend fun scanProject(files: ProjectFiles, root: Uri): ProjectProfile {
    val paths = mutableListOf<String>()
    val queue = ArrayDeque<Pair<Uri, String>>()
    queue.add(root to "")
    var visited = 0
    while (queue.isNotEmpty() && visited < 14 && paths.size < 600) {
        val (uri, prefix) = queue.removeFirst()
        visited++
        val children = runCatching { files.list(uri) }.getOrDefault(emptyList())
        for (child in children) {
            val path = prefix + child.name
            if (child.isDirectory) {
                if (child.name.lowercase() !in ignoredDirectories && prefix.count { it == '/' } < 3) queue.add(child.uri to "$path/")
            } else {
                paths += path
            }
        }
    }
    return ProjectProfile.detect(paths)
}

private fun sameDay(a: Calendar, b: Calendar): Boolean =
    a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

/** "Hoje, 16:42", "Ontem, 21:15" ou a data completa. */
private fun formatActivity(time: Long): String {
    if (time <= 0L) return "—"
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = time }
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    val hour = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(time))
    return when {
        sameDay(now, then) -> "Hoje, $hour"
        sameDay(yesterday, then) -> "Ontem, $hour"
        else -> SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(time))
    }
}

@Composable
internal fun ProjectsScreen(
    state: AppState,
    onNewProject: () -> Unit,
    onOpenProject: (ProjectWorkspace) -> Unit,
    onRemoveProject: (ProjectWorkspace) -> Unit
) {
    val colors = Codux.colors
    val context = LocalContext.current
    val files = remember(context) { ProjectFiles(context.applicationContext) }
    val profiles = remember { mutableStateMapOf<String, ProjectProfile>() }
    LaunchedEffect(state.projects) {
        state.projects.forEach { project ->
            val key = project.uri.toString()
            if (key !in profiles) profiles[key] = scanProject(files, project.uri)
        }
    }
    var search by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("Todos") }
    var sortByName by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    val visible = state.projects.filter { project ->
        val profile = profiles[project.uri.toString()]
        val matchesSearch = search.isBlank() || project.name.contains(search, true) || profile?.stacks.orEmpty().any { it.contains(search, true) }
        val matchesFilter = when (filter) {
            "Todos" -> true
            "Outros" -> profile != null && profile.platforms.isEmpty()
            else -> profile?.platforms.orEmpty().any { it.equals(filter, true) }
        }
        matchesSearch && matchesFilter
    }.let { list -> if (sortByName) list.sortedBy { it.name.lowercase() } else list.sortedByDescending { it.lastOpenedAt } }
    val recent = remember(state.projects) { state.projects.sortedByDescending { it.lastOpenedAt }.take(5) }

    Column(Modifier.fillMaxSize()) {
        CoduxTopBar(
            title = "Projetos Codux",
            subtitle = when (state.projects.size) {
                0 -> "Nenhum projeto"
                1 -> "1 projeto"
                else -> state.projects.size.toString() + " projetos"
            },
            icon = Icons.Default.Folder
        ) {
            Box {
                CoduxIconButton(Icons.Default.FilterList, "Ordenar projetos", { sortMenu = true })
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    DropdownMenuItem(text = { Text("Mais recentes") }, onClick = { sortByName = false; sortMenu = false })
                    DropdownMenuItem(text = { Text("Nome (A–Z)") }, onClick = { sortByName = true; sortMenu = false })
                }
            }
        }
        if (state.projects.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CoduxEmptyState(
                    icon = Icons.Default.Folder,
                    title = "Ainda não tens projetos",
                    message = "Escolhe uma pasta do teu dispositivo para dar ao Codux acesso aos ficheiros do projeto.",
                    actionLabel = "Novo projeto",
                    onAction = onNewProject,
                    secondaryLabel = "Abrir pasta existente",
                    onSecondary = onNewProject
                )
            }
        } else {
            CoduxSearchField(search, { search = it }, "Pesquisar projetos…", Modifier.padding(horizontal = CoduxSpace.lg))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = CoduxSpace.lg, vertical = CoduxSpace.md),
                horizontalArrangement = Arrangement.spacedBy(CoduxSpace.sm)
            ) {
                platformFilters.forEach { (label, key) ->
                    val icon = when (key) {
                        "Android" -> Icons.Default.Android
                        "Web" -> Icons.Default.Language
                        "PC" -> Icons.Default.DesktopWindows
                        "Outros" -> Icons.Default.MoreHoriz
                        else -> null
                    }
                    CoduxChip(label, filter == key, { filter = key }, icon = icon)
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(340.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = CoduxSpace.lg, vertical = CoduxSpace.xs),
                horizontalArrangement = Arrangement.spacedBy(CoduxSpace.md),
                verticalArrangement = Arrangement.spacedBy(CoduxSpace.md)
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    CoduxSectionHeader(if (filter == "Todos" && search.isBlank()) "Projetos recentes" else "Resultados (" + visible.size + ")")
                }
                if (visible.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        CoduxEmptyState(
                            icon = Icons.Default.FilterList,
                            title = "Nenhum projeto encontrado",
                            message = "Ajusta a pesquisa ou o filtro para ver outros projetos.",
                            actionLabel = "Limpar filtros",
                            onAction = { search = ""; filter = "Todos" }
                        )
                    }
                } else {
                    items(visible, key = { it.uri.toString() }) { project ->
                        ProjectCard(project, profiles[project.uri.toString()], onClick = { onOpenProject(project) }, onRemove = { onRemoveProject(project) })
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    CoduxSectionHeader("Atividade recente", Modifier.padding(top = CoduxSpace.md))
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    CoduxCard(Modifier.fillMaxWidth()) {
                        recent.forEachIndexed { index, project ->
                            Row(Modifier.fillMaxWidth().padding(CoduxSpace.md), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(34.dp).clip(CoduxShape.medium).background(colors.accent.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Folder, null, tint = colors.accent, modifier = Modifier.size(18.dp))
                                }
                                Column(Modifier.weight(1f).padding(horizontal = CoduxSpace.md)) {
                                    Text("Projeto aberto", style = MaterialTheme.typography.titleSmall, color = colors.text)
                                    Text(project.name, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text(formatActivity(project.lastOpenedAt), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                            }
                            if (index < recent.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(CoduxSpace.lg), horizontalArrangement = Arrangement.End) {
                CoduxPrimaryButton("Novo projeto", onNewProject, icon = Icons.Default.Add)
            }
        }
    }
}

@Composable
private fun ProjectCard(project: ProjectWorkspace, profile: ProjectProfile?, onClick: () -> Unit, onRemove: () -> Unit) {
    val colors = Codux.colors
    var menuOpen by remember { mutableStateOf(false) }
    val platform = profile?.platforms?.firstOrNull()
    val icon: ImageVector = when (platform) {
        "Android" -> Icons.Default.Android
        "Web" -> Icons.Default.Language
        "PC" -> Icons.Default.DesktopWindows
        else -> Icons.Default.Folder
    }
    val tint: Color = when (platform) {
        "Android" -> colors.success
        "Web" -> colors.accent
        "PC" -> colors.ai
        else -> colors.textSecondary
    }
    CoduxCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.padding(CoduxSpace.md)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.size(48.dp).clip(CoduxShape.medium).background(tint.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = tint, modifier = Modifier.size(26.dp))
                }
                Column(Modifier.weight(1f).padding(horizontal = CoduxSpace.md)) {
                    Text(project.name, style = MaterialTheme.typography.titleSmall, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        project.termuxPath,
                        Modifier.padding(top = 2.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = colors.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box {
                    CoduxIconButton(Icons.Default.MoreVert, "Ações do projeto", { menuOpen = true }, Modifier)
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Abrir no editor") }, onClick = { menuOpen = false; onClick() })
                        DropdownMenuItem(text = { Text("Remover da lista") }, onClick = { menuOpen = false; onRemove() })
                    }
                }
            }
            Row(Modifier.padding(top = CoduxSpace.md), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                when {
                    profile == null -> Text("A analisar…", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                    else -> {
                        profile.platforms.forEach { CoduxTag(it, tint) }
                        profile.stacks.take(3).forEach { CoduxTag(it, colors.accent) }
                        if (profile.platforms.isEmpty() && profile.stacks.isEmpty()) Text("Tipo não identificado", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                    }
                }
            }
            Box(Modifier.padding(vertical = CoduxSpace.md).fillMaxWidth().height(1.dp).background(colors.border))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Schedule, null, tint = colors.textSecondary, modifier = Modifier.size(15.dp))
                Text(formatActivity(project.lastOpenedAt), Modifier.padding(start = 6.dp), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            }
        }
    }
}
