package io.github.thirtyeighttwentysix.volan.idea

import com.intellij.lexer.LexerBase
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType

/** Tokens never contain state spanning a newline, so restart at any token boundary is safe. */
class VolanLexer : LexerBase() {
    private var buffer: CharSequence = ""
    private var limit = 0
    private var start = 0
    private var end = 0
    private var type: IElementType? = null

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        this.buffer = buffer; limit = endOffset; start = startOffset; end = startOffset
        advance()
    }
    override fun getState() = 0
    override fun getTokenType() = type
    override fun getTokenStart() = start
    override fun getTokenEnd() = end
    override fun getBufferSequence() = buffer
    override fun getBufferEnd() = limit

    override fun advance() {
        start = end
        if (start >= limit) { type = null; return }
        var i = start + 1
        val c = buffer[start]
        type = when {
            c.isWhitespace() -> {
                while (i < limit && buffer[i].isWhitespace()) i++
                TokenType.WHITE_SPACE
            }
            c == '/' && i < limit && buffer[i] == '/' -> {
                while (i < limit && buffer[i] != '\n' && buffer[i] != '\r') i++
                if (start + 2 < limit && buffer[start + 2] == '/') VolanTokens.DOC_COMMENT else VolanTokens.COMMENT
            }
            c == '"' -> {
                while (i < limit && buffer[i] != '\n' && buffer[i] != '\r') {
                    val next = buffer[i++]
                    if (next == '"') break
                    if (next == '\\' && i < limit && buffer[i] != '\n' && buffer[i] != '\r') i++
                }
                VolanTokens.STRING
            }
            c.isLetter() || c == '_' -> {
                while (i < limit && (buffer[i].isLetterOrDigit() || buffer[i] == '_')) i++
                VolanTokens.IDENTIFIER
            }
            c.isDigit() || c == '-' && i < limit && buffer[i].isDigit() -> {
                while (i < limit && (buffer[i].isDigit() || buffer[i] == '.')) i++
                VolanTokens.NUMBER
            }
            c == '@' -> {
                if (i < limit && buffer[i] == '@') { i++; VolanTokens.BLOCK_AT } else VolanTokens.AT
            }
            else -> when (c) {
                '{' -> VolanTokens.LBRACE; '}' -> VolanTokens.RBRACE
                '(' -> VolanTokens.LPAREN; ')' -> VolanTokens.RPAREN
                '[' -> VolanTokens.LBRACKET; ']' -> VolanTokens.RBRACKET
                '.' -> VolanTokens.DOT; ':' -> VolanTokens.COLON
                ',' -> VolanTokens.COMMA; '=' -> VolanTokens.EQUALS; '?' -> VolanTokens.QUESTION
                else -> TokenType.BAD_CHARACTER
            }
        }
        end = i
    }
}
