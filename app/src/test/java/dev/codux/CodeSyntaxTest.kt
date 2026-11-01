package dev.codux

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CodeSyntaxTest {
    private fun typeAt(text: String, language: CodeLanguage, index: Int): TokenType? =
        CodeSyntax.tokenize(text, language).firstOrNull { index >= it.start && index < it.end }?.type

    @Test
    fun detectsLanguageFromFileName() {
        assertEquals(CodeLanguage.KOTLIN, CodeSyntax.languageFor("MainActivity.kt"))
        assertEquals(CodeLanguage.CPP, CodeSyntax.languageFor("Brush.cpp"))
        assertEquals(CodeLanguage.PLAIN, CodeSyntax.languageFor("LICENSE"))
    }

    @Test
    fun highlightsKotlin() {
        val text = "val answer = 42 // note"
        assertEquals(TokenType.KEYWORD, typeAt(text, CodeLanguage.KOTLIN, 0))
        assertNull(typeAt(text, CodeLanguage.KOTLIN, 5))
        assertEquals(TokenType.NUMBER, typeAt(text, CodeLanguage.KOTLIN, 13))
        assertEquals(TokenType.COMMENT, typeAt(text, CodeLanguage.KOTLIN, 16))
    }

    @Test
    fun highlightsFunctionsAndStrings() {
        val text = "fun greet() = \"hi\""
        assertEquals(TokenType.KEYWORD, typeAt(text, CodeLanguage.KOTLIN, 0))
        assertEquals(TokenType.FUNCTION, typeAt(text, CodeLanguage.KOTLIN, 4))
        assertEquals(TokenType.STRING, typeAt(text, CodeLanguage.KOTLIN, 15))
    }

    @Test
    fun highlightsPython() {
        val text = "def f(): return 1  # x"
        assertEquals(TokenType.KEYWORD, typeAt(text, CodeLanguage.PYTHON, 0))
        assertEquals(TokenType.FUNCTION, typeAt(text, CodeLanguage.PYTHON, 4))
        assertEquals(TokenType.CONTROL, typeAt(text, CodeLanguage.PYTHON, 9))
        assertEquals(TokenType.COMMENT, typeAt(text, CodeLanguage.PYTHON, 19))
    }

    @Test
    fun highlightsJson() {
        val text = "{\"a\": 1, \"b\": true}"
        assertEquals(TokenType.PROPERTY, typeAt(text, CodeLanguage.JSON, 1))
        assertEquals(TokenType.NUMBER, typeAt(text, CodeLanguage.JSON, 6))
        assertEquals(TokenType.KEYWORD, typeAt(text, CodeLanguage.JSON, 14))
    }

    @Test
    fun highlightsMarkup() {
        val text = "<div class=\"x\">"
        assertEquals(TokenType.TAG, typeAt(text, CodeLanguage.HTML, 1))
        assertEquals(TokenType.ATTRIBUTE, typeAt(text, CodeLanguage.HTML, 5))
        assertEquals(TokenType.STRING, typeAt(text, CodeLanguage.HTML, 11))
    }

    @Test
    fun plainTextHasNoTokens() {
        assertEquals(emptyList<SyntaxToken>(), CodeSyntax.tokenize("hello", CodeLanguage.PLAIN))
    }
}
