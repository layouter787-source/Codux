package dev.codux

/** Texto e seleção; independente de Compose para poder ser testado na JVM. */
data class EditState(val text: String, val start: Int, val end: Int) {
    val collapsed: Boolean get() = start == end
    val min: Int get() = minOf(start, end)
    val max: Int get() = maxOf(start, end)
}

/** Operações de edição ao estilo do VS Code: auto-indentação, pares de símbolos, linhas e comentários. */
object CodeEditing {
    const val INDENT = "    "
    private val openers = mapOf('(' to ')', '[' to ']', '{' to '}')
    private val quotes = setOf('"', '\'', '`')
    private const val closers = ")]}"

    /**
     * Chamado depois de o utilizador escrever: reconhece a inserção ou remoção de um único carácter e aplica
     * a auto-indentação, o fecho automático de pares e o "saltar por cima" do símbolo de fecho.
     */
    fun afterTyping(old: EditState, new: EditState, indentAfterColon: Boolean = false): EditState {
        if (!old.collapsed || !new.collapsed) return new
        val p = old.start
        if (p < 0 || p > old.text.length) return new
        if (new.text.length == old.text.length + 1 && new.start == p + 1 &&
            new.text.regionMatches(0, old.text, 0, p) && new.text.endsWith(old.text.substring(p))
        ) {
            return insertRule(old, new.text[p], indentAfterColon) ?: new
        }
        if (new.text.length == old.text.length - 1 && new.start == p - 1 && p > 0 && new.text == old.text.removeRange(p - 1, p)) {
            val deleted = old.text[p - 1]
            val closer = openers[deleted] ?: if (deleted in quotes) deleted else null
            if (closer != null && p < old.text.length && old.text[p] == closer) {
                return EditState(old.text.removeRange(p - 1, p + 1), p - 1, p - 1)
            }
        }
        return new
    }

    private fun insertRule(old: EditState, typed: Char, indentAfterColon: Boolean): EditState? {
        val text = old.text
        val p = old.start
        val before = text.substring(0, p)
        val after = text.substring(p)
        val next = after.firstOrNull()
        return when {
            typed == '\n' -> {
                val line = before.substring(before.lastIndexOf('\n') + 1)
                val indent = line.takeWhile { it == ' ' || it == '\t' }
                val last = line.trimEnd().lastOrNull()
                val extra = if (last == '{' || last == '(' || last == '[' || (indentAfterColon && last == ':')) INDENT else ""
                val head = before + "\n" + indent + extra
                val splitBlock = extra.isNotEmpty() && next != null && next in closers
                if (splitBlock) EditState(head + "\n" + indent + after, head.length, head.length)
                else EditState(head + after, head.length, head.length)
            }
            openers.containsKey(typed) -> {
                if (next == null || next.isWhitespace() || next in ")]},;") {
                    EditState(before + typed + openers.getValue(typed) + after, p + 1, p + 1)
                } else null
            }
            typed in closers -> if (next == typed) EditState(text, p + 1, p + 1) else null
            typed in quotes -> {
                when {
                    next == typed -> EditState(text, p + 1, p + 1)
                    before.lastOrNull()?.isLetterOrDigit() == true -> null
                    next == null || next.isWhitespace() || next in ")]},;" -> EditState(before + typed + typed + after, p + 1, p + 1)
                    else -> null
                }
            }
            else -> null
        }
    }

    // ---- linhas ----

    /** Início e fim (exclusivo do '\n') do bloco de linhas que cobre a seleção. */
    private fun blockRange(state: EditState): Pair<Int, Int> {
        val text = state.text
        val lo = state.min.coerceIn(0, text.length)
        var hi = state.max.coerceIn(0, text.length)
        if (hi > lo && hi > 0 && text[hi - 1] == '\n') hi -= 1
        val start = text.lastIndexOf('\n', lo - 1) + 1
        val end = text.indexOf('\n', hi).let { if (it < 0) text.length else it }
        return start to end
    }

    fun toggleComment(state: EditState, prefix: String): EditState {
        val text = state.text
        val (a, b) = blockRange(state)
        val block = text.substring(a, b)
        val lines = block.split('\n')
        val allCommented = lines.filter { it.isNotBlank() }.all { it.trimStart().startsWith(prefix) }
        val changed = lines.joinToString("\n") { line ->
            if (line.isBlank()) {
                line
            } else {
                val indent = line.length - line.trimStart().length
                if (allCommented) line.substring(0, indent) + line.substring(indent).removePrefix(prefix).removePrefix(" ")
                else line.substring(0, indent) + prefix + " " + line.substring(indent)
            }
        }
        val newText = text.substring(0, a) + changed + text.substring(b)
        val position = (b + (changed.length - block.length)).coerceIn(0, newText.length)
        return EditState(newText, position, position)
    }

    fun duplicateLines(state: EditState): EditState {
        val text = state.text
        val (a, b) = blockRange(state)
        val block = text.substring(a, b)
        val shift = block.length + 1
        return EditState(text.substring(0, b) + "\n" + block + text.substring(b), state.start + shift, state.end + shift)
    }

    fun deleteLines(state: EditState): EditState {
        val text = state.text
        val (a, b) = blockRange(state)
        val newText: String
        val cursor: Int
        if (b < text.length) {
            newText = text.removeRange(a, b + 1)
            cursor = a
        } else if (a > 0) {
            newText = text.removeRange(a - 1, b)
            cursor = a - 1
        } else {
            newText = ""
            cursor = 0
        }
        return EditState(newText, cursor.coerceIn(0, newText.length), cursor.coerceIn(0, newText.length))
    }

    fun moveLines(state: EditState, delta: Int): EditState {
        val text = state.text
        val (a, b) = blockRange(state)
        val block = text.substring(a, b)
        if (delta < 0) {
            if (a == 0) return state
            val previousStart = text.lastIndexOf('\n', a - 2) + 1
            val previousLine = text.substring(previousStart, a - 1)
            val newText = text.substring(0, previousStart) + block + "\n" + previousLine + text.substring(b)
            val shift = previousLine.length + 1
            return EditState(newText, state.start - shift, state.end - shift)
        }
        if (b >= text.length) return state
        val nextStart = b + 1
        val nextEnd = text.indexOf('\n', nextStart).let { if (it < 0) text.length else it }
        val nextLine = text.substring(nextStart, nextEnd)
        val newText = text.substring(0, a) + nextLine + "\n" + block + text.substring(nextEnd)
        val shift = nextLine.length + 1
        return EditState(newText, state.start + shift, state.end + shift)
    }

    fun indent(state: EditState, outdent: Boolean): EditState {
        val text = state.text
        val (a, b) = blockRange(state)
        val block = text.substring(a, b)
        val lines = block.split('\n')
        val changed = lines.joinToString("\n") { line ->
            if (!outdent) {
                if (line.isEmpty()) line else INDENT + line
            } else {
                when {
                    line.startsWith(INDENT) -> line.substring(INDENT.length)
                    line.startsWith("\t") -> line.substring(1)
                    else -> line.substring(0, line.length - line.trimStart(' ').length).let { line.removePrefix(it) }
                }
            }
        }
        val newText = text.substring(0, a) + changed + text.substring(b)
        val delta = changed.length - block.length
        return if (state.collapsed && lines.size == 1) {
            val position = (state.start + delta).coerceIn(a, a + changed.length)
            EditState(newText, position, position)
        } else {
            EditState(newText, a, (b + delta).coerceIn(a, newText.length))
        }
    }

    fun moveCursorLine(state: EditState, delta: Int): EditState {
        val text = state.text
        val position = state.end.coerceIn(0, text.length)
        val lineStart = text.lastIndexOf('\n', position - 1) + 1
        val column = position - lineStart
        if (delta < 0) {
            if (lineStart == 0) return EditState(text, 0, 0)
            val previousEnd = lineStart - 1
            val previousStart = text.lastIndexOf('\n', previousEnd - 1) + 1
            val target = minOf(previousStart + column, previousEnd)
            return EditState(text, target, target)
        }
        val lineEnd = text.indexOf('\n', position).let { if (it < 0) text.length else it }
        if (lineEnd >= text.length) return EditState(text, text.length, text.length)
        val nextStart = lineEnd + 1
        val nextEnd = text.indexOf('\n', nextStart).let { if (it < 0) text.length else it }
        val target = minOf(nextStart + column, nextEnd)
        return EditState(text, target, target)
    }

    // ---- posição e procura ----

    /** Linha e coluna, ambas a partir de 1. */
    fun lineAndColumn(text: String, offset: Int): Pair<Int, Int> {
        val safe = offset.coerceIn(0, text.length)
        val before = text.substring(0, safe)
        return (before.count { it == '\n' } + 1) to (safe - (before.lastIndexOf('\n') + 1) + 1)
    }

    /** Deslocamento do início da linha [line] (a partir de 1); limita ao fim do texto. */
    fun offsetOfLine(text: String, line: Int): Int {
        var offset = 0
        var current = 1
        while (current < line) {
            val next = text.indexOf('\n', offset)
            if (next < 0) return text.length
            offset = next + 1
            current++
        }
        return offset
    }

    fun findAll(text: String, query: String, caseSensitive: Boolean, limit: Int = 2000): List<IntRange> {
        if (query.isEmpty()) return emptyList()
        val result = ArrayList<IntRange>()
        var index = text.indexOf(query, 0, !caseSensitive)
        while (index >= 0 && result.size < limit) {
            result += index..(index + query.length - 1)
            index = text.indexOf(query, index + query.length, !caseSensitive)
        }
        return result
    }

    fun replaceAll(text: String, query: String, replacement: String, caseSensitive: Boolean): String {
        if (query.isEmpty()) return text
        return text.replace(query, replacement, !caseSensitive)
    }
}
