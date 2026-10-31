package dev.codux

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.codux.data.AppState
import dev.codux.data.ProjectFiles
import dev.codux.data.ProjectWorkspace
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ignoredDirectories = setOf("node_modules", ".git", "build", ".gradle", "dist", "venv", ".venv", ".idea", "__pycache__", "target", ".next")
private val platformFilters = listOf("Todos", "Android", "PC", "Web")

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

@Composable
internal fun ProjectsScreen(
    state: AppState,
    onMenu: () -> Unit,
    onNewProject: () -> Unit,
    onOpenProject: (ProjectWorkspace) -> Unit,
    onRemoveProject: (ProjectWorkspace) -> Unit
) {
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
        val matchesFilter = filter == "Todos" || profile?.platforms.orEmpty().any { it.equals(filter, true) }
        matchesSearch && matchesFilter
    }.let { list -> if (sortByName) list.sortedBy { it.name.lowercase() } else list.sortedByDescending { it.lastOpenedAt } }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            CoduxHeader("Projetos Codux", onMenu)
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(26.dp),
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    placeholder = { Text("pesquisar projetos", fontStyle = FontStyle.Italic, fontWeight = FontWeight.Bold) }
                )
                Box {
                    IconButton(onClick = { sortMenu = true }) { Icon(Icons.Default.FilterList, "Ordenar") }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        DropdownMenuItem(text = { Text("Mais recentes") }, onClick = { sortByName = false; sortMenu = false })
                        DropdownMenuItem(text = { Text("Nome (A–Z)") }, onClick = { sortByName = true; sortMenu = false })
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                platformFilters.forEach { name -> FilterPill(name, selected = filter == name) { filter = name } }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f))
            if (state.projects.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(16.dp)) {
                    EmptyStateCard("Começa por um projeto", "Escolhe uma pasta para dar ao Codux acesso aos ficheiros.", "Novo projeto", onNewProject)
                }
            } else {
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 96.dp)) {
                    items(visible, key = { it.uri.toString() }) { project ->
                        ProjectCard(project, profiles[project.uri.toString()], onClick = { onOpenProject(project) }, onRemove = { onRemoveProject(project) })
                    }
                }
            }
        }
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.background,
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp).width(112.dp).height(48.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onNewProject)
        ) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Add, "Novo projeto", modifier = Modifier.size(30.dp)) }
        }
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Surface(
        shape = shape,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface),
        modifier = Modifier.height(36.dp).clip(shape).clickable(onClick = onClick)
    ) {
        Box(Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            Text(label, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
private fun ProjectCard(project: ProjectWorkspace, profile: ProjectProfile?, onClick: () -> Unit, onRemove: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    val onSurface = MaterialTheme.colorScheme.onSurface
    val icon = when (profile?.platforms?.firstOrNull()) {
        "Android" -> Icons.Default.Android
        "Web" -> Icons.Default.Language
        "PC" -> Icons.Default.DesktopWindows
        else -> Icons.Default.Folder
    }
    val platform = when {
        profile == null -> "a analisar…"
        profile.platforms.isEmpty() -> "—"
        else -> profile.platforms.joinToString(", ")
    }
    val stacks = when {
        profile == null -> "a analisar…"
        profile.stacks.isEmpty() -> "—"
        else -> profile.stacks.joinToString("+")
    }
    val shape = RoundedCornerShape(30.dp)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.background,
        border = BorderStroke(2.dp, onSurface),
        modifier = Modifier.fillMaxWidth().clip(shape).clickable(onClick = onClick)
    ) {
        Box {
            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(2.dp, onSurface),
                    modifier = Modifier.size(104.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) { Icon(icon, null, modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary) }
                }
                Column(Modifier.weight(1f).padding(start = 14.dp, end = 8.dp)) {
                    Text("Nome: " + project.name, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Plataforma: $platform", fontStyle = FontStyle.Italic, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                    Text("Stacks: $stacks", fontStyle = FontStyle.Italic, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                    Text(
                        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(project.lastOpenedAt)),
                        fontStyle = FontStyle.Italic,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.align(Alignment.End).padding(top = 10.dp, end = 6.dp)
                    )
                }
            }
            Box(Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 10.dp)) {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(30.dp)) { Icon(Icons.Default.MoreVert, "Ações do projeto", modifier = Modifier.size(18.dp)) }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Abrir no editor") }, onClick = { menuOpen = false; onClick() })
                    DropdownMenuItem(text = { Text("Remover da lista") }, onClick = { menuOpen = false; onRemove() })
                }
            }
        }
    }
}
