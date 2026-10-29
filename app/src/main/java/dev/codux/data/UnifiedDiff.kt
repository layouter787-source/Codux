package dev.codux.data

object UnifiedDiff {
    private const val MAX_LCS_CELLS = 400_000L
    private const val MAX_DIFF_LINES = 100_000

    private enum class Kind { EQUAL, ADD, REMOVE }
    private data class TextLine(val value: String, val newlineTerminated: Boolean)
    private data class Line(val kind: Kind, val text: TextLine)

    fun create(before: String, after: String, context: Int = 3): String {
        if (before == after) return "Sem alterações."
        val oldCount = countLines(before)
        val newCount = countLines(after)
        if (oldCount > MAX_DIFF_LINES || newCount > MAX_DIFF_LINES || oldCount.toLong() * newCount.toLong() > MAX_LCS_CELLS) {
            return fallback(before, after)
        }
        val oldLines = splitLines(before)
        val newLines = splitLines(after)
        val safeContext = context.coerceAtLeast(0)

        val lcs = Array(oldLines.size + 1) { IntArray(newLines.size + 1) }
        for (i in oldLines.indices.reversed()) {
            for (j in newLines.indices.reversed()) {
                lcs[i][j] = if (oldLines[i] == newLines[j]) lcs[i + 1][j + 1] + 1
                else maxOf(lcs[i + 1][j], lcs[i][j + 1])
            }
        }
        val lines = mutableListOf<Line>()
        var i = 0
        var j = 0
        while (i < oldLines.size && j < newLines.size) {
            if (oldLines[i] == newLines[j]) {
                lines += Line(Kind.EQUAL, oldLines[i]); i++; j++
            } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
                lines += Line(Kind.REMOVE, oldLines[i++])
            } else {
                lines += Line(Kind.ADD, newLines[j++])
            }
        }
        while (i < oldLines.size) lines += Line(Kind.REMOVE, oldLines[i++])
        while (j < newLines.size) lines += Line(Kind.ADD, newLines[j++])

        val changed = lines.indices.filter { lines[it].kind != Kind.EQUAL }
        if (changed.isEmpty()) return "Sem alterações."
        val start = maxOf(0, changed.first() - safeContext)
        val end = minOf(lines.lastIndex, changed.last() + safeContext)
        val hunkLines = lines.subList(start, end + 1)
        val oldHunkCount = hunkLines.count { it.kind != Kind.ADD }
        val newHunkCount = hunkLines.count { it.kind != Kind.REMOVE }
        val oldLinesBefore = lines.subList(0, start).count { it.kind != Kind.ADD }
        val newLinesBefore = lines.subList(0, start).count { it.kind != Kind.REMOVE }
        val oldStart = if (oldHunkCount == 0) oldLinesBefore else oldLinesBefore + 1
        val newStart = if (newHunkCount == 0) newLinesBefore else newLinesBefore + 1
        val body = hunkLines.joinToString("\n") { render(it) }
        return "--- original\n+++ editado\n@@ -$oldStart,$oldHunkCount +$newStart,$newHunkCount @@\n$body"
    }

    private fun countLines(text: String): Int {
        if (text.isEmpty()) return 0
        var count = 0
        for (char in text) if (char == '\n') count++
        return count + if (text.last() == '\n') 0 else 1
    }

    private fun splitLines(text: String): List<TextLine> {
        val lines = mutableListOf<TextLine>()
        forEachLine(text) { lines += it }
        return lines
    }

    private inline fun forEachLine(text: String, consume: (TextLine) -> Unit) {
        var start = 0
        for (index in text.indices) {
            if (text[index] == '\n') {
                consume(TextLine(text.substring(start, index), newlineTerminated = true))
                start = index + 1
            }
        }
        if (start < text.length) consume(TextLine(text.substring(start), newlineTerminated = false))
    }

    private fun render(line: Line): String {
        val prefix = when (line.kind) {
            Kind.EQUAL -> " "
            Kind.ADD -> "+"
            Kind.REMOVE -> "-"
        }
        return prefix + line.text.value + if (line.text.newlineTerminated) "" else "\n\\ No newline at end of file"
    }

    private fun fallback(before: String, after: String): String = buildString {
        appendLine("--- original")
        appendLine("+++ editado")
        appendLine("@@ -${if (countLines(before) == 0) 0 else 1},${countLines(before)} +${if (countLines(after) == 0) 0 else 1},${countLines(after)} @@")
        forEachLine(before) { appendFallbackLine('-', it) }
        forEachLine(after) { appendFallbackLine('+', it) }
    }.trimEnd()

    private fun StringBuilder.appendFallbackLine(prefix: Char, line: TextLine) {
        append(prefix).append(line.value).append('\n')
        if (!line.newlineTerminated) appendLine("\\ No newline at end of file")
    }
}
