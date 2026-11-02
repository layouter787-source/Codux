package dev.codux

import android.net.Uri
import android.webkit.WebView
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
import androidx.compose.material.icons.filled.PhoneIphone
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
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.viewinterop.AndroidView
import dev.codux.data.AppState
import dev.codux.data.ProjectFiles
import dev.codux.data.ProjectWorkspace
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ignoredDirectories = setOf("node_modules", ".git", "build", ".gradle", "dist", "venv", ".venv", ".idea", "__pycache__", "target", ".next", "pods", ".dart_tool")
private val platformFilters = listOf("Todos", "Android", "iOS", "PC", "Web", "Outras")

/** Percorre o projeto (poucas pastas, sem entrar em dependências) e deduz plataforma e stacks. */
private suspend fun scanProject(files: ProjectFiles, root: Uri): ProjectProfile {
    val paths = mutableListOf<String>()
    val queue = ArrayDeque<Pair<Uri, String>>()
    queue.add(root to "")
    var visited = 0
    while (queue.isNotEmpty() && visited < 16 && paths.size < 700) {
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

/**
 * HTML do index.html do projeto para o mini preview (só projetos web). Os CSS locais ao lado do index
 * são incorporados e os scripts removidos: o preview é uma imagem estática, leve e sem rede.
 */
private suspend fun loadPreviewHtml(files: ProjectFiles, root: Uri): String? {
    val folders = listOf<String?>(null, "public", "dist", "build", "www", "web", "docs", "src")
    for (folder in folders) {
        val directoryUri = if (folder == null) root else runCatching { files.list(root) }.getOrDefault(emptyList())
            .firstOrNull { it.isDirectory && it.name.equals(folder, true) }?.uri ?: continue
        val children = runCatching { files.list(directoryUri) }.getOrDefault(emptyList())
        val index = children.firstOrNull { !it.isDirectory && it.name.equals("index.html", true) } ?: continue
        val document = runCatching { files.read(directoryUri, index.uri, index.name) }.getOrNull() ?: continue
        var html = document.editedText.take(200_000)
        val stylesheetLink = Regex("<link[^>]+href=[\"']([^\"':]+\\.css)[\"'][^>]*>", RegexOption.IGNORE_CASE)
        for (match in stylesheetLink.findAll(html).toList()) {
            val name = match.groupValues[1].substringAfterLast('/')
            val cssFile = children.firstOrNull { !it.isDirectory && it.name.equals(name, true) } ?: continue
            val css = runCatching { files.read(directoryUri, cssFile.uri, cssFile.name).editedText }.getOrNull() ?: continue
            html = html.replace(match.value, "<style>" + css.take(100_000) + "</style>")
        }
        return html.replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
    }
    return null
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
        val matchesFilter = when (filter) {
            "Todos" -> true
            "Outras" -> profile != null && profile.platforms.isEmpty()
            else -> profile?.platforms.orEmpty().any { it.equals(filter, true) }
        }
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
                        ProjectCard(project, profiles[project.uri.toString()], files, onClick = { onOpenProject(project) }, onRemove = { onRemoveProject(project) })
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

/** Mini preview de um site: um WebView pequeno, sem JavaScript nem rede, coberto por uma camada que recebe o toque. */
@Composable
private fun WebPreview(html: String, onClick: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext ->
                WebView(viewContext).apply {
                    settings.javaScriptEnabled = false
                    settings.blockNetworkLoads = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false
                    isFocusable = false
                    setBackgroundColor(android.graphics.Color.WHITE)
                    loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
                }
            },
            onRelease = { it.destroy() }
        )
        Box(Modifier.fillMaxSize().clickable(onClick = onClick))
    }
}

@Composable
private fun ProjectCard(project: ProjectWorkspace, profile: ProjectProfile?, files: ProjectFiles, onClick: () -> Unit, onRemove: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    val onSurface = MaterialTheme.colorScheme.onSurface
    val primaryPlatform = profile?.platforms?.firstOrNull()
    // Preview só para web; Android, iOS e PC mostram o ícone (mais leve).
    val previewHtml by produceState<String?>(null, project.uri, primaryPlatform) {
        value = if (primaryPlatform == "Web") loadPreviewHtml(files, project.uri) else null
    }
    val icon = when (primaryPlatform) {
        "Android" -> Icons.Default.Android
        "iOS" -> Icons.Default.PhoneIphone
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
                    modifier = Modifier.size(104.dp).clip(RoundedCornerShape(24.dp))
                ) {
                    val html = previewHtml
                    if (html != null) WebPreview(html, onClick)
                    else Box(contentAlignment = Alignment.Center) { Icon(icon, null, modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary) }
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
