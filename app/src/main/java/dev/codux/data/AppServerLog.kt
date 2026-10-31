package dev.codux.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Saída recente do processo `codex app-server`, para diagnosticar falhas ao ligar. Nunca contém o token. */
object AppServerLog {
    private val _text = MutableStateFlow("")
    val text: StateFlow<String> = _text.asStateFlow()

    fun append(line: String) {
        _text.value = (_text.value + line + "\n").takeLast(12_000)
    }

    fun clear() {
        _text.value = ""
    }

    fun tail(maxLines: Int = 20): String =
        _text.value.trim().lines().takeLast(maxLines).joinToString("\n")
}
