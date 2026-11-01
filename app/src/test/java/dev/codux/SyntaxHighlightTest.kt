package dev.codux

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyntaxHighlightTest {
    private val palette = DarkSyntaxPalette

    private fun colorAt(text: String, language: SyntaxLanguage?, needle: String): Color? {
        val annotated = CodeSyntax.highlight(text, language, palette)
        val start = text.indexOf(needle)
        return annotated.spanStyles.firstOrNull { it.start <= start && it.end >= start + needle.length }?.item?.color
    }

    @Test
    fun resolvesLanguageFromFileName() {
        assertEquals("Kotlin", CodeSyntax.forFileName("MainActivity.kt")?.label)
        assertEquals("Kotlin", CodeSyntax.forFileName("build.gradle.kts")?.label)
        assertEquals("Python", CodeSyntax.forFileName("script.py")?.label)
        assertNull(CodeSyntax.forFileName("notas.txt"))
        assertNotNull(CodeSyntax.forLanguageName("bash"))
        assertNotNull(CodeSyntax.forLanguageName("typescript"))
    }

    @Test
    fun highlightsKotlinKeywordsStringsCommentsAndNumbers() {
        val language = CodeSyntax.forFileName("a.kt")
        val code = "val name = \"Codux\" // nota\nfun main() { println(42) }"
        assertEquals(palette.keyword, colorAt(code, language, "val"))
        assertEquals(palette.string, colorAt(code, language, "\"Codux\""))
        assertEquals(palette.comment, colorAt(code, language, "// nota"))
        assertEquals(palette.number, colorAt(code, language, "42"))
        assertEquals(palette.function, colorAt(code, language, "println"))
    }

    @Test
    fun slashesInsideStringsAreNotComments() {
        val language = CodeSyntax.forFileName("a.kt")
        val code = "val url = \"http://exemplo.com\""
        assertEquals(palette.string, colorAt(code, language, "http://exemplo.com"))
    }

    @Test
    fun highlightsJsonKeysDifferentlyFromValues() {
        val language = CodeSyntax.forFileName("a.json")
        val code = "{\"nome\": \"Codux\", \"versao\": 1}"
        assertEquals(palette.property, colorAt(code, language, "\"nome\""))
        assertEquals(palette.string, colorAt(code, language, "\"Codux\""))
        assertEquals(palette.number, colorAt(code, language, "1"))
    }

    @Test
    fun plainTextAndHugeInputAreLeftUnstyled() {
        assertTrue(CodeSyntax.highlight("texto", null, palette).spanStyles.isEmpty())
        val huge = "val x = 1\n".repeat(30_000)
        assertTrue(CodeSyntax.highlight(huge, CodeSyntax.forFileName("a.kt"), palette).spanStyles.isEmpty())
    }
}
