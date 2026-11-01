package dev.codux

import java.util.regex.Pattern

enum class CodeLanguage(val label: String) {
    PLAIN("Texto simples"), KOTLIN("Kotlin"), JAVA("Java"), C("C"), CPP("C++"), CSHARP("C#"),
    JAVASCRIPT("JavaScript"), TYPESCRIPT("TypeScript"), PYTHON("Python"), GO("Go"), RUST("Rust"),
    SWIFT("Swift"), DART("Dart"), PHP("PHP"), RUBY("Ruby"), SHELL("Shell"), XML("XML"), HTML("HTML"),
    CSS("CSS"), JSON("JSON"), YAML("YAML"), TOML("TOML"), MARKDOWN("Markdown"), SQL("SQL")
}

enum class TokenType { KEYWORD, CONTROL, STRING, COMMENT, NUMBER, TYPE, FUNCTION, ANNOTATION, TAG, ATTRIBUTE, PROPERTY }

data class SyntaxToken(val start: Int, val end: Int, val type: TokenType)

/** Realce de sintaxe por expressões regulares, no estilo do VS Code (sem dependências de Compose). */
object CodeSyntax {
    /** Ficheiros maiores que isto ficam sem realce para não pesar a escrita. */
    const val MAX_HIGHLIGHT_CHARS = 200_000

    fun languageFor(fileName: String): CodeLanguage {
        val lower = fileName.lowercase()
        return when (lower.substringAfterLast('.', "")) {
            "kt", "kts", "gradle" -> CodeLanguage.KOTLIN
            "java" -> CodeLanguage.JAVA
            "c" -> CodeLanguage.C
            "cpp", "cc", "cxx", "hpp", "hh", "h" -> CodeLanguage.CPP
            "cs" -> CodeLanguage.CSHARP
            "js", "mjs", "cjs", "jsx" -> CodeLanguage.JAVASCRIPT
            "ts", "tsx" -> CodeLanguage.TYPESCRIPT
            "py" -> CodeLanguage.PYTHON
            "go" -> CodeLanguage.GO
            "rs" -> CodeLanguage.RUST
            "swift" -> CodeLanguage.SWIFT
            "dart" -> CodeLanguage.DART
            "php" -> CodeLanguage.PHP
            "rb" -> CodeLanguage.RUBY
            "sh", "bash", "zsh" -> CodeLanguage.SHELL
            "xml", "svg", "xsd", "plist" -> CodeLanguage.XML
            "html", "htm", "vue" -> CodeLanguage.HTML
            "css", "scss" -> CodeLanguage.CSS
            "json" -> CodeLanguage.JSON
            "yml", "yaml" -> CodeLanguage.YAML
            "toml", "properties", "ini", "cfg" -> CodeLanguage.TOML
            "md", "markdown" -> CodeLanguage.MARKDOWN
            "sql" -> CodeLanguage.SQL
            else -> when (lower) {
                "dockerfile", "makefile", "cmakelists.txt" -> CodeLanguage.SHELL
                else -> CodeLanguage.PLAIN
            }
        }
    }

    fun commentPrefix(language: CodeLanguage): String = when (language) {
        CodeLanguage.PYTHON, CodeLanguage.SHELL, CodeLanguage.YAML, CodeLanguage.TOML, CodeLanguage.RUBY -> "#"
        CodeLanguage.SQL -> "--"
        else -> "//"
    }

    fun tokenize(text: String, language: CodeLanguage): List<SyntaxToken> {
        if (language == CodeLanguage.PLAIN || text.isEmpty() || text.length > MAX_HIGHLIGHT_CHARS) return emptyList()
        return when (language) {
            CodeLanguage.XML, CodeLanguage.HTML -> tokenizeMarkup(text)
            CodeLanguage.JSON -> tokenizeJson(text)
            CodeLanguage.MARKDOWN -> tokenizeMarkdown(text)
            CodeLanguage.CSS -> tokenizeCss(text)
            CodeLanguage.YAML, CodeLanguage.TOML -> tokenizeConfig(text)
            else -> tokenizeCode(text, specFor(language))
        }
    }

    // ---- linguagens de programação ----

    private class Spec(
        val keywords: Set<String>,
        val control: Set<String>,
        val lineComment: String?,
        val blockComment: Boolean,
        val tripleQuotes: Boolean,
        val backtick: Boolean,
        val singleQuote: Boolean,
        val annotations: Boolean,
        val preprocessor: Boolean,
        val dollarVars: Boolean,
        val ignoreCase: Boolean
    ) {
        val kinds = mutableListOf<String>()
        val pattern: Pattern = build()

        private fun build(): Pattern {
            val parts = mutableListOf<String>()
            fun add(kind: String, regex: String) { kinds += kind; parts += "(" + regex + ")" }
            if (blockComment) add("comment", "/\\*[\\s\\S]*?(?:\\*/|\\z)")
            if (lineComment != null) add("comment", Pattern.quote(lineComment) + "[^\\n]*")
            if (tripleQuotes) {
                add("string", "\"\"\"[\\s\\S]*?(?:\"\"\"|\\z)")
                add("string", "'''[\\s\\S]*?(?:'''|\\z)")
            }
            add("string", "\"(?:\\\\.|[^\"\\\\\\n])*\"?")
            if (singleQuote) add("string", "'(?:\\\\.|[^'\\\\\\n])*'?")
            if (backtick) add("string", "`(?:\\\\.|[^`\\\\])*`?")
            if (annotations) add("annotation", "@[A-Za-z_][\\w.]*")
            if (preprocessor) add("control", "(?m:^[ \\t]*#[ \\t]*[A-Za-z_]+)")
            if (dollarVars) add("property", "\\$\\{[^}\\n]*\\}|\\$[A-Za-z_]\\w*")
            add("number", "\\b(?:0[xX][0-9a-fA-F_]+|0[bB][01_]+|\\d[\\d_]*(?:\\.\\d[\\d_]*)?(?:[eE][+-]?\\d+)?)[uUlLfFdD]*\\b")
            add("word", "[A-Za-z_][A-Za-z_0-9]*")
            return Pattern.compile(parts.joinToString("|"))
        }
    }

    private val specCache = HashMap<CodeLanguage, Spec>()

    private fun words(vararg groups: String): Set<String> = groups.flatMap { it.split(' ') }.filter { it.isNotEmpty() }.toSet()

    private fun specFor(language: CodeLanguage): Spec = specCache.getOrPut(language) { createSpec(language) }

    private fun createSpec(language: CodeLanguage): Spec {
        val cControl = words("if else switch case default for while do return break continue goto try catch throw")
        return when (language) {
            CodeLanguage.KOTLIN -> Spec(
                words("val var fun class object interface enum data sealed abstract open override private protected public internal companion import package typealias const lateinit suspend inline crossinline noinline reified vararg this super null true false is as in by get set init constructor out where annotation operator infix tailrec external expect actual def"),
                words("if else when for while do return break continue try catch finally throw"),
                "//", true, true, false, true, true, false, false, false
            )
            CodeLanguage.JAVA -> Spec(
                words("abstract assert boolean byte char class const double enum extends final float implements import instanceof int interface long native new package private protected public short static strictfp super synchronized this throws transient void volatile null true false var record sealed permits default"),
                words("if else switch case for while do return break continue try catch finally throw yield"),
                "//", true, true, false, true, true, false, false, false
            )
            CodeLanguage.C -> Spec(
                words("auto char const double enum extern float inline int long register restrict short signed sizeof static struct typedef union unsigned void volatile bool true false NULL"),
                cControl, "//", true, false, false, true, false, true, false, false
            )
            CodeLanguage.CPP -> Spec(
                words("auto char const double enum extern float inline int long register short signed sizeof static struct typedef union unsigned void volatile bool true false NULL nullptr class namespace template typename using public private protected virtual override final new delete this operator constexpr noexcept explicit friend mutable static_cast dynamic_cast reinterpret_cast const_cast size_t uint8_t uint16_t uint32_t uint64_t int8_t int16_t int32_t int64_t"),
                cControl, "//", true, false, false, true, false, true, false, false
            )
            CodeLanguage.CSHARP -> Spec(
                words("using namespace class struct interface enum public private protected internal static readonly const void int string bool double float long var new this base null true false override virtual abstract sealed async await get set partial record"),
                words("if else switch case default for foreach while do return break continue try catch finally throw yield"),
                "//", true, false, false, true, false, true, false, false
            )
            CodeLanguage.JAVASCRIPT, CodeLanguage.TYPESCRIPT -> Spec(
                words("const let var function class extends new this super import export from as async await typeof instanceof in of delete void null undefined true false static get set interface type enum implements public private protected readonly abstract namespace declare string number boolean any unknown never"),
                words("if else switch case default for while do return break continue try catch finally throw yield"),
                "//", true, false, true, true, language == CodeLanguage.TYPESCRIPT, false, false, false
            )
            CodeLanguage.PYTHON -> Spec(
                words("def class import from as lambda with pass global nonlocal None True False and or not is in async await self"),
                words("if elif else for while return break continue try except finally raise yield assert del"),
                "#", false, true, false, true, true, false, false, false
            )
            CodeLanguage.GO -> Spec(
                words("package import func var const type struct interface map chan go defer nil true false range select iota"),
                words("if else switch case default for return break continue goto fallthrough"),
                "//", true, false, true, false, false, false, false, false
            )
            CodeLanguage.RUST -> Spec(
                words("fn let mut pub struct enum impl trait use mod crate self Self super as where const static type dyn move ref unsafe async await true false extern"),
                words("if else match for while loop return break continue"),
                "//", true, false, false, true, true, false, false, false
            )
            CodeLanguage.SWIFT -> Spec(
                words("let var func class struct enum protocol extension import init deinit self Self super nil true false public private internal fileprivate open static final override lazy weak inout throws rethrows async await typealias where in is as"),
                words("if else guard switch case default for while repeat return break continue defer do try catch throw"),
                "//", true, true, false, false, true, false, false, false
            )
            CodeLanguage.DART -> Spec(
                words("var final const late class extends implements with mixin abstract enum import export library part static void dynamic new this super null true false async await required is as in typedef get set factory operator"),
                cControl, "//", true, true, false, true, true, false, false, false
            )
            CodeLanguage.PHP -> Spec(
                words("function class interface trait extends implements namespace use public private protected static abstract final new this self parent null true false echo print const var array global"),
                words("if else elseif switch case default for foreach while do return break continue try catch finally throw match"),
                "//", true, false, false, true, false, false, true, false
            )
            CodeLanguage.RUBY -> Spec(
                words("def class module end self nil true false require require_relative include extend attr_accessor attr_reader attr_writer private public protected do begin then and or not yield super lambda proc"),
                words("if elsif else unless case when while until for return break next rescue ensure raise"),
                "#", false, false, false, true, false, false, false, false
            )
            CodeLanguage.SHELL -> Spec(
                words("export local readonly function echo cd source alias set unset declare exit in then fi do done esac"),
                words("if elif else for while until case return break continue"),
                "#", false, false, false, true, false, false, true, false
            )
            CodeLanguage.SQL -> Spec(
                words("select from where insert into values update set delete create table alter drop index join left right inner outer on group by order having limit offset as and or not null is in like between distinct union all primary key foreign references default unique constraint view database begin commit rollback"),
                words("case when then else end"),
                "--", true, false, false, true, false, false, false, true
            )
            else -> Spec(emptySet(), emptySet(), null, false, false, false, false, false, false, false, false)
        }
    }

    private fun tokenizeCode(text: String, spec: Spec): List<SyntaxToken> {
        val result = ArrayList<SyntaxToken>()
        val matcher = spec.pattern.matcher(text)
        while (matcher.find()) {
            var group = 1
            var kind = ""
            for (index in spec.kinds.indices) {
                if (matcher.start(index + 1) >= 0) { group = index + 1; kind = spec.kinds[index]; break }
            }
            val start = matcher.start(group)
            val end = matcher.end(group)
            when (kind) {
                "comment" -> result += SyntaxToken(start, end, TokenType.COMMENT)
                "string" -> result += SyntaxToken(start, end, TokenType.STRING)
                "annotation" -> result += SyntaxToken(start, end, TokenType.ANNOTATION)
                "control" -> result += SyntaxToken(start, end, TokenType.CONTROL)
                "property" -> result += SyntaxToken(start, end, TokenType.PROPERTY)
                "number" -> result += SyntaxToken(start, end, TokenType.NUMBER)
                "word" -> {
                    val word = text.substring(start, end)
                    val key = if (spec.ignoreCase) word.lowercase() else word
                    when {
                        key in spec.control -> result += SyntaxToken(start, end, TokenType.CONTROL)
                        key in spec.keywords -> result += SyntaxToken(start, end, TokenType.KEYWORD)
                        nextNonSpace(text, end) == '(' -> result += SyntaxToken(start, end, TokenType.FUNCTION)
                        word.length > 1 && word[0].isUpperCase() -> result += SyntaxToken(start, end, TokenType.TYPE)
                    }
                }
            }
        }
        return result
    }

    private fun nextNonSpace(text: String, from: Int): Char? {
        var index = from
        while (index < text.length && (text[index] == ' ' || text[index] == '\t')) index++
        return text.getOrNull(index)
    }

    // ---- XML / HTML ----

    private val markupPattern = Pattern.compile(
        "(<!--[\\s\\S]*?(?:-->|\\z))|(</?[A-Za-z][\\w:.-]*)|(\\s)([A-Za-z_:@#][\\w:.@#-]*)(?==)|(\"[^\"\\n]*\"|'[^'\\n]*')"
    )

    private fun tokenizeMarkup(text: String): List<SyntaxToken> {
        val result = ArrayList<SyntaxToken>()
        val matcher = markupPattern.matcher(text)
        while (matcher.find()) {
            when {
                matcher.start(1) >= 0 -> result += SyntaxToken(matcher.start(1), matcher.end(1), TokenType.COMMENT)
                matcher.start(2) >= 0 -> result += SyntaxToken(matcher.start(2), matcher.end(2), TokenType.TAG)
                matcher.start(4) >= 0 -> result += SyntaxToken(matcher.start(4), matcher.end(4), TokenType.ATTRIBUTE)
                matcher.start(5) >= 0 -> result += SyntaxToken(matcher.start(5), matcher.end(5), TokenType.STRING)
            }
        }
        return result
    }

    // ---- JSON ----

    private val jsonPattern = Pattern.compile(
        "(\"(?:\\\\.|[^\"\\\\\\n])*\")(\\s*:)?|(-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)|\\b(true|false|null)\\b"
    )

    private fun tokenizeJson(text: String): List<SyntaxToken> {
        val result = ArrayList<SyntaxToken>()
        val matcher = jsonPattern.matcher(text)
        while (matcher.find()) {
            when {
                matcher.start(1) >= 0 -> result += SyntaxToken(
                    matcher.start(1), matcher.end(1),
                    if (matcher.start(2) >= 0) TokenType.PROPERTY else TokenType.STRING
                )
                matcher.start(3) >= 0 -> result += SyntaxToken(matcher.start(3), matcher.end(3), TokenType.NUMBER)
                matcher.start(4) >= 0 -> result += SyntaxToken(matcher.start(4), matcher.end(4), TokenType.KEYWORD)
            }
        }
        return result
    }

    // ---- CSS ----

    private val cssPattern = Pattern.compile(
        "(/\\*[\\s\\S]*?(?:\\*/|\\z))|(\"(?:\\\\.|[^\"\\\\\\n])*\"|'(?:\\\\.|[^'\\\\\\n])*')|(@[\\w-]+)|(#[0-9a-fA-F]{3,8}\\b)|((?<![\\w#-])-?\\d*\\.?\\d+(?:px|em|rem|%|vh|vw|s|ms|deg)?)|([A-Za-z-]+)(?=\\s*:)"
    )

    private fun tokenizeCss(text: String): List<SyntaxToken> {
        val result = ArrayList<SyntaxToken>()
        val matcher = cssPattern.matcher(text)
        while (matcher.find()) {
            when {
                matcher.start(1) >= 0 -> result += SyntaxToken(matcher.start(1), matcher.end(1), TokenType.COMMENT)
                matcher.start(2) >= 0 -> result += SyntaxToken(matcher.start(2), matcher.end(2), TokenType.STRING)
                matcher.start(3) >= 0 -> result += SyntaxToken(matcher.start(3), matcher.end(3), TokenType.KEYWORD)
                matcher.start(4) >= 0 -> result += SyntaxToken(matcher.start(4), matcher.end(4), TokenType.NUMBER)
                matcher.start(5) >= 0 -> result += SyntaxToken(matcher.start(5), matcher.end(5), TokenType.NUMBER)
                matcher.start(6) >= 0 -> result += SyntaxToken(matcher.start(6), matcher.end(6), TokenType.PROPERTY)
            }
        }
        return result
    }

    // ---- YAML / TOML / properties ----

    private val configPattern = Pattern.compile(
        "(#[^\\n]*)|(\"(?:\\\\.|[^\"\\\\\\n])*\"|'[^'\\n]*')|((?m:^\\[[^\\]\\n]+\\]))|(?m:^[ \\t-]*)([A-Za-z_][\\w.\\-/]*)(?=[ \\t]*[:=])|\\b(true|false|null|yes|no)\\b|(-?\\d+(?:\\.\\d+)?)"
    )

    private fun tokenizeConfig(text: String): List<SyntaxToken> {
        val result = ArrayList<SyntaxToken>()
        val matcher = configPattern.matcher(text)
        while (matcher.find()) {
            when {
                matcher.start(1) >= 0 -> result += SyntaxToken(matcher.start(1), matcher.end(1), TokenType.COMMENT)
                matcher.start(2) >= 0 -> result += SyntaxToken(matcher.start(2), matcher.end(2), TokenType.STRING)
                matcher.start(3) >= 0 -> result += SyntaxToken(matcher.start(3), matcher.end(3), TokenType.KEYWORD)
                matcher.start(4) >= 0 -> result += SyntaxToken(matcher.start(4), matcher.end(4), TokenType.PROPERTY)
                matcher.start(5) >= 0 -> result += SyntaxToken(matcher.start(5), matcher.end(5), TokenType.KEYWORD)
                matcher.start(6) >= 0 -> result += SyntaxToken(matcher.start(6), matcher.end(6), TokenType.NUMBER)
            }
        }
        return result
    }

    // ---- Markdown ----

    private val markdownPattern = Pattern.compile(
        "((?m:^#{1,6}[ \\t].*$))|(`[^`\\n]+`)|(\\[[^\\]\\n]+\\]\\([^)\\n]*\\))"
    )

    private fun tokenizeMarkdown(text: String): List<SyntaxToken> {
        val result = ArrayList<SyntaxToken>()
        val matcher = markdownPattern.matcher(text)
        while (matcher.find()) {
            when {
                matcher.start(1) >= 0 -> result += SyntaxToken(matcher.start(1), matcher.end(1), TokenType.KEYWORD)
                matcher.start(2) >= 0 -> result += SyntaxToken(matcher.start(2), matcher.end(2), TokenType.STRING)
                matcher.start(3) >= 0 -> result += SyntaxToken(matcher.start(3), matcher.end(3), TokenType.ATTRIBUTE)
            }
        }
        return result
    }
}
