package dev.codux

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/** Cores de sintaxe no estilo VS Code (Dark+ / Light+). */
internal class SyntaxPalette(
    val plain: Color,
    val keyword: Color,
    val string: Color,
    val comment: Color,
    val number: Color,
    val type: Color,
    val function: Color,
    val annotation: Color,
    val property: Color
)

internal val DarkSyntaxPalette = SyntaxPalette(
    plain = Color(0xFFD4D4D4),
    keyword = Color(0xFF569CD6),
    string = Color(0xFFCE9178),
    comment = Color(0xFF6A9955),
    number = Color(0xFFB5CEA8),
    type = Color(0xFF4EC9B0),
    function = Color(0xFFDCDCAA),
    annotation = Color(0xFFC586C0),
    property = Color(0xFF9CDCFE)
)

internal val LightSyntaxPalette = SyntaxPalette(
    plain = Color(0xFF1F1F1F),
    keyword = Color(0xFF0000FF),
    string = Color(0xFFA31515),
    comment = Color(0xFF008000),
    number = Color(0xFF098658),
    type = Color(0xFF267F99),
    function = Color(0xFF795E26),
    annotation = Color(0xFFAF00DB),
    property = Color(0xFF001080)
)

/** Superfícies do editor: fundo, texto, margem de números, barra de separadores e barra de estado. */
internal class EditorColors(
    val background: Color,
    val text: Color,
    val gutter: Color,
    val tabBar: Color,
    val tabIdle: Color,
    val statusBar: Color,
    val statusText: Color
)

internal fun editorColors(dark: Boolean): EditorColors =
    if (dark) {
        EditorColors(
            background = Color(0xFF1E1E1E),
            text = Color(0xFFD4D4D4),
            gutter = Color(0xFF858585),
            tabBar = Color(0xFF252526),
            tabIdle = Color(0xFF2D2D2D),
            statusBar = Color(0xFF007ACC),
            statusText = Color.White
        )
    } else {
        EditorColors(
            background = Color.White,
            text = Color(0xFF1F1F1F),
            gutter = Color(0xFF6E7681),
            tabBar = Color(0xFFF3F3F3),
            tabIdle = Color(0xFFECECEC),
            statusBar = Color(0xFF007ACC),
            statusText = Color.White
        )
    }

/** Verdadeiro quando o tema atual da app é escuro. */
@Composable
internal fun isDarkSurface(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

/**
 * Uma linguagem para realce. O regex junta cinco grupos, por esta ordem:
 * 1 comentário, 2 texto entre aspas, 3 anotação/etiqueta, 4 número, 5 identificador.
 */
internal class SyntaxLanguage(
    val label: String,
    commentPattern: String,
    stringPattern: String,
    annotationPattern: String,
    numberPattern: String,
    identifierPattern: String,
    val keywords: Set<String>,
    /** Se o identificador seguido de um destes caracteres é uma propriedade/chave (ex.: ":" em YAML). */
    val propertySeparators: String = "",
    /** Identificador seguido de "(" é uma chamada de função. */
    val calls: Boolean = true,
    /** Identificadores com maiúscula inicial são tipos. */
    val typeByCase: Boolean = true,
    /** O grupo 3 são etiquetas de marcação (HTML/XML) e usa a cor de palavra-chave. */
    val tagAnnotation: Boolean = false,
    /** Textos seguidos de ":" são chaves de objeto (JSON). */
    val jsonKeys: Boolean = false,
    /** Depois de ":" no fim da linha, a próxima linha entra com mais um nível de indentação (Python). */
    val colonIndent: Boolean = false
) {
    val regex: Regex = Regex(
        "($commentPattern)|($stringPattern)|($annotationPattern)|($numberPattern)|($identifierPattern)"
    )
}

private const val MAX_HIGHLIGHT_CHARS = 200_000

private const val NEVER = "(?!)"
private const val CLIKE_COMMENT = """//[^\n]*|/\*[\s\S]*?(?:\*/|$)"""
private const val BLOCK_COMMENT = """/\*[\s\S]*?(?:\*/|$)"""
private const val HASH_COMMENT = """#[^\n]*"""
private const val MARKUP_COMMENT = """<!--[\s\S]*?(?:-->|$)"""

private const val STR_C = """(?:"{3}[\s\S]*?(?:"{3}|$))|(?:"(?:\\.|[^"\\\n])*"?)|(?:'(?:\\.|[^'\\\n])*'?)"""
private const val STR_JS = STR_C + """|(?:`(?:\\.|[^`\\])*`?)"""
private const val STR_PY = """(?:"{3}[\s\S]*?(?:"{3}|$))|(?:'{3}[\s\S]*?(?:'{3}|$))|(?:"(?:\\.|[^"\\\n])*"?)|(?:'(?:\\.|[^'\\\n])*'?)"""
private const val STR_JSON = """(?:"(?:\\.|[^"\\\n])*"?)"""
private const val STR_CFG = """(?:"(?:\\.|[^"\\\n])*"?)|(?:'[^'\n]*'?)"""
private const val STR_MARKUP = """(?<==)(?:"[^"\n]*"?|'[^'\n]*'?)"""

private const val ANN_AT = """@[A-Za-z_][A-Za-z0-9_.]*"""
private const val ANN_PREPROCESSOR = """#[ \t]*[A-Za-z_]+"""
private const val ANN_SHELL_VAR = """\$[A-Za-z_][A-Za-z0-9_]*|\$\{[^}\n]*\}"""
private const val ANN_TAG = """</?[A-Za-z_][A-Za-z0-9_:.\-]*|/?>|<\?[A-Za-z]*|\?>|<!DOCTYPE"""
private const val ANN_CSS = """@[A-Za-z-]+"""

private const val NUM_C = """\b0[xX][0-9a-fA-F_]+[uUlL]*\b|\b\d[\d_]*(?:\.\d+)?(?:[eE][+-]?\d+)?[fFdDlLuU]*\b"""
private const val NUM_JSON = """-?\b\d+(?:\.\d+)?(?:[eE][+-]?\d+)?"""
private const val NUM_CSS = """#[0-9a-fA-F]{3,8}\b|\b\d+(?:\.\d+)?[a-zA-Z%]*"""

private const val ID_CODE = """[A-Za-z_$][A-Za-z0-9_$]*"""
private const val ID_DASH = """[A-Za-z_][A-Za-z0-9_\-]*"""
private const val ID_MARKUP = """[A-Za-z_][A-Za-z0-9_:.\-]*"""
private const val ID_CFG = """[A-Za-z_][A-Za-z0-9_.\-]*"""

private fun words(list: String): Set<String> = list.split(' ').filter { it.isNotEmpty() }.toSet()

internal object CodeSyntax {
    private val kotlin = SyntaxLanguage(
        "Kotlin", CLIKE_COMMENT, STR_C, ANN_AT, NUM_C, ID_CODE,
        words(
            "package import class interface object fun val var if else when for while do return break continue try catch " +
                "finally throw is in as null true false this super override open abstract sealed data enum companion private " +
                "public protected internal suspend inline lateinit const typealias by init constructor where out vararg " +
                "reified crossinline noinline operator infix tailrec external annotation value"
        )
    )

    private val java = SyntaxLanguage(
        "Java", CLIKE_COMMENT, STR_C, ANN_AT, NUM_C, ID_CODE,
        words(
            "abstract assert boolean break byte case catch char class const continue default do double else enum extends " +
                "final finally float for goto if implements import instanceof int interface long native new package private " +
                "protected public return short static strictfp super switch synchronized this throw throws transient try " +
                "void volatile while var record null true false"
        )
    )

    private val cpp = SyntaxLanguage(
        "C/C++", CLIKE_COMMENT, STR_C, ANN_PREPROCESSOR, NUM_C, ID_CODE,
        words(
            "auto break case char const continue default do double else enum extern float for goto if inline int long " +
                "register return short signed sizeof static struct switch typedef union unsigned void volatile while class " +
                "namespace template typename public private protected virtual override new delete using bool true false " +
                "nullptr this operator constexpr noexcept"
        )
    )

    private val rust = SyntaxLanguage(
        "Rust", CLIKE_COMMENT, STR_C, ANN_AT, NUM_C, ID_CODE,
        words(
            "fn let mut pub struct impl enum match use mod crate self Self trait where for in while loop if else return " +
                "as ref move async await const static type dyn unsafe true false break continue"
        )
    )

    private val javascript = SyntaxLanguage(
        "JavaScript/TypeScript", CLIKE_COMMENT, STR_JS, ANN_AT, NUM_C, ID_CODE,
        words(
            "async await break case catch class const continue debugger default delete do else export extends finally for " +
                "function if import in instanceof let new of return static super switch this throw try typeof var void while " +
                "with yield null undefined true false interface type enum implements readonly private public protected " +
                "abstract as from namespace declare"
        )
    )

    private val python = SyntaxLanguage(
        "Python", HASH_COMMENT, STR_PY, ANN_AT, NUM_C, ID_CODE,
        words(
            "and as assert async await break class continue def del elif else except finally for from global if import in " +
                "is lambda None nonlocal not or pass raise return try while with yield True False self match case"
        ),
        colonIndent = true
    )

    private val shell = SyntaxLanguage(
        "Shell", HASH_COMMENT, STR_CFG, ANN_SHELL_VAR, NUM_C, ID_DASH,
        words(
            "if then else elif fi for while do done case esac in function return exit export local echo cd source set " +
                "unset readonly shift test"
        ),
        calls = false,
        typeByCase = false
    )

    private val json = SyntaxLanguage(
        "JSON", NEVER, STR_JSON, NEVER, NUM_JSON, ID_DASH,
        words("true false null"),
        calls = false,
        typeByCase = false,
        jsonKeys = true
    )

    private val markup = SyntaxLanguage(
        "XML/HTML", MARKUP_COMMENT, STR_MARKUP, ANN_TAG, NEVER, ID_MARKUP,
        emptySet(),
        propertySeparators = "=",
        calls = false,
        typeByCase = false,
        tagAnnotation = true
    )

    private val css = SyntaxLanguage(
        "CSS", BLOCK_COMMENT, STR_CFG, ANN_CSS, NUM_CSS, ID_DASH,
        emptySet(),
        propertySeparators = ":",
        calls = true,
        typeByCase = false
    )

    private val config = SyntaxLanguage(
        "YAML/TOML", HASH_COMMENT, STR_CFG, NEVER, NUM_C, ID_CFG,
        words("true false null yes no"),
        propertySeparators = ":=",
        calls = false,
        typeByCase = false
    )

    fun forFileName(name: String): SyntaxLanguage? = forExtension(name.substringAfterLast('.', "").lowercase())

    /** Aceita o nome da linguagem de um bloco de código ("kotlin", "js", "bash"...). */
    fun forLanguageName(name: String): SyntaxLanguage? {
        val key = name.trim().lowercase()
        return forExtension(
            when (key) {
                "kotlin" -> "kt"
                "javascript" -> "js"
                "typescript" -> "ts"
                "python" -> "py"
                "bash", "shell", "zsh" -> "sh"
                "c++" -> "cpp"
                "rust" -> "rs"
                "yml" -> "yaml"
                else -> key
            }
        )
    }

    private fun forExtension(extension: String): SyntaxLanguage? = when (extension) {
        "kt", "kts", "gradle" -> kotlin
        "java" -> java
        "c", "h", "cpp", "cc", "cxx", "hpp" -> cpp
        "rs" -> rust
        "js", "jsx", "mjs", "ts", "tsx" -> javascript
        "py" -> python
        "sh", "bash", "zsh" -> shell
        "json" -> json
        "xml", "html", "htm", "svg", "xhtml" -> markup
        "css" -> css
        "yml", "yaml", "toml", "properties", "ini", "conf" -> config
        else -> null
    }

    fun highlight(text: String, language: SyntaxLanguage?, palette: SyntaxPalette): AnnotatedString {
        if (language == null || text.length > MAX_HIGHLIGHT_CHARS) return AnnotatedString(text)
        return buildAnnotatedString {
            append(text)
            for (match in language.regex.findAll(text)) {
                val groups = match.groups
                val color: Color? = when {
                    groups[1] != null -> palette.comment
                    groups[2] != null -> stringColor(language, text, match, palette)
                    groups[3] != null -> if (language.tagAnnotation) palette.keyword else palette.annotation
                    groups[4] != null -> palette.number
                    else -> identifierColor(language, text, match, palette)
                }
                if (color != null) addStyle(SpanStyle(color = color), match.range.first, match.range.last + 1)
            }
        }
    }

    private fun stringColor(language: SyntaxLanguage, text: String, match: MatchResult, palette: SyntaxPalette): Color =
        if (language.jsonKeys && nextNonSpace(text, match.range.last + 1) == ':') palette.property else palette.string

    private fun identifierColor(language: SyntaxLanguage, text: String, match: MatchResult, palette: SyntaxPalette): Color? {
        val word = match.value
        if (word in language.keywords) return palette.keyword
        val next = nextNonSpace(text, match.range.last + 1)
        return when {
            next != null && language.propertySeparators.indexOf(next) >= 0 -> palette.property
            language.calls && next == '(' -> palette.function
            language.typeByCase && word.length > 1 && word[0].isUpperCase() -> palette.type
            else -> null
        }
    }

    private fun nextNonSpace(text: String, from: Int): Char? {
        var index = from
        while (index < text.length && (text[index] == ' ' || text[index] == '\t')) index++
        return text.getOrNull(index)
    }
}

/** Aplica o realce de sintaxe a um campo de texto sem alterar as posições do cursor. */
internal class SyntaxTransformation(
    private val language: SyntaxLanguage?,
    private val palette: SyntaxPalette
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText =
        TransformedText(CodeSyntax.highlight(text.text, language, palette), OffsetMapping.Identity)
}
