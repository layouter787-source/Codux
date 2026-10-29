package dev.codux

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private data class Message(val author: String, val body: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CoduxApp() }
    }
}

@Composable
private fun CoduxApp() {
    var prompt by remember { mutableStateOf("") }
    var connected by remember { mutableStateOf(false) }
    val messages = remember { mutableStateListOf(Message("Codux", "Interface inicial. A ligação ao Codex requer autorizar o acesso RUN_COMMAND do Termux.")) }
    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF82D9C2), background = Color(0xFF101411), surface = Color(0xFF1A211D))) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("codux", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
                    Text("O teu espaço de desenvolvimento", style = MaterialTheme.typography.bodyMedium, color = Color.LightGray)
                }
                Text(if (connected) "● online" else "○ offline", modifier = Modifier.padding(top = 14.dp), color = if (connected) MaterialTheme.colorScheme.primary else Color.LightGray)
            }
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Chat", "Projetos", "Terminal").forEach { label -> AssistChip(onClick = {}, label = { Text(label) }) }
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(messages) { msg ->
                    Surface(color = if (msg.author == "Tu") Color(0xFF18382E) else MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(msg.author, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Text(msg.body, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
            }
            OutlinedTextField(value = prompt, onValueChange = { prompt = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Pede ao Codex…") }, minLines = 2, maxLines = 5)
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                if (prompt.isNotBlank()) { messages.add(Message("Tu", prompt.trim())); messages.add(Message("Codux", "Não enviado: a ponte autorizada para o Codex ainda não está configurada.")); prompt = "" }
            }, modifier = Modifier.fillMaxWidth()) { Text("Enviar") }
        }
    }
}
