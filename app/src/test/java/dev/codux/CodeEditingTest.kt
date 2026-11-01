package dev.codux

import org.junit.Assert.assertEquals
import org.junit.Test

class CodeEditingTest {
    private fun state(text: String, start: Int, end: Int = start) = EditState(text, start, end)

    @Test
    fun enterAfterOpeningBraceIndents() {
        val result = CodeEditing.afterTyping(state("fun f() {", 9), state("fun f() {\n", 10))
        assertEquals(state("fun f() {\n    ", 14), result)
    }

    @Test
    fun enterBetweenBracesOpensBlock() {
        val result = CodeEditing.afterTyping(state("{}", 1), state("{\n}", 2))
        assertEquals(state("{\n    \n}", 6), result)
    }

    @Test
    fun enterKeepsCurrentIndentation() {
        val result = CodeEditing.afterTyping(state("    x = 1", 9), state("    x = 1\n", 10))
        assertEquals(state("    x = 1\n    ", 14), result)
    }

    @Test
    fun typingOpeningBracketAddsClosingOne() {
        assertEquals(state("foo()", 4), CodeEditing.afterTyping(state("foo", 3), state("foo(", 4)))
    }

    @Test
    fun typingClosingBracketSkipsOverExistingOne() {
        assertEquals(state("foo()", 5), CodeEditing.afterTyping(state("foo()", 4), state("foo())", 5)))
    }

    @Test
    fun apostropheAfterLetterIsNotPaired() {
        val typed = state("don'", 4)
        assertEquals(typed, CodeEditing.afterTyping(state("don", 3), typed))
    }

    @Test
    fun backspaceRemovesEmptyPair() {
        assertEquals(state("", 0), CodeEditing.afterTyping(state("()", 1), state(")", 0)))
    }

    @Test
    fun togglesLineComments() {
        val commented = CodeEditing.toggleComment(state("a\nb", 0, 3), "//")
        assertEquals("// a\n// b", commented.text)
        val restored = CodeEditing.toggleComment(state(commented.text, 0, commented.text.length), "//")
        assertEquals("a\nb", restored.text)
    }

    @Test
    fun movesLineDown() {
        assertEquals(state("b\na\nc", 2), CodeEditing.moveLines(state("a\nb\nc", 0), 1))
    }

    @Test
    fun movesLineUp() {
        assertEquals(state("b\na\nc", 0), CodeEditing.moveLines(state("a\nb\nc", 2), -1))
    }

    @Test
    fun duplicatesLine() {
        assertEquals("a\na\nb", CodeEditing.duplicateLines(state("a\nb", 0)).text)
    }

    @Test
    fun deletesLine() {
        assertEquals("a\nc", CodeEditing.deleteLines(state("a\nb\nc", 2)).text)
    }

    @Test
    fun findsMatchesIgnoringCase() {
        assertEquals(listOf(0..2, 3..5), CodeEditing.findAll("abcABC", "abc", false))
        assertEquals(listOf(0..2), CodeEditing.findAll("abcABC", "abc", true))
    }

    @Test
    fun computesLineAndColumn() {
        assertEquals(2 to 2, CodeEditing.lineAndColumn("ab\ncd", 4))
        assertEquals(3, CodeEditing.offsetOfLine("a\nb\nc", 2) + 1)
    }
}
