package io.github.thirtyeighttwentysix.volan.idea

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors as Colors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType

class VolanSyntaxHighlighterFactory : SyntaxHighlighterFactory() {
    override fun getSyntaxHighlighter(project: Project?, virtualFile: VirtualFile?) = object : SyntaxHighlighterBase() {
        override fun getHighlightingLexer() = VolanLexer()
        override fun getTokenHighlights(type: IElementType): Array<TextAttributesKey> = pack(when (type) {
            VolanTokens.STRING -> Colors.STRING; VolanTokens.NUMBER -> Colors.NUMBER
            VolanTokens.COMMENT -> Colors.LINE_COMMENT; VolanTokens.DOC_COMMENT -> Colors.DOC_COMMENT
            VolanTokens.AT, VolanTokens.BLOCK_AT -> Colors.METADATA
            VolanTokens.LBRACE, VolanTokens.RBRACE -> Colors.BRACES
            VolanTokens.LPAREN, VolanTokens.RPAREN -> Colors.PARENTHESES
            VolanTokens.LBRACKET, VolanTokens.RBRACKET -> Colors.BRACKETS
            VolanTokens.COMMA -> Colors.COMMA; VolanTokens.DOT -> Colors.DOT
            VolanTokens.EQUALS, VolanTokens.QUESTION -> Colors.OPERATION_SIGN
            TokenType.BAD_CHARACTER -> com.intellij.openapi.editor.HighlighterColors.BAD_CHARACTER
            else -> null
        })
    }
}

class VolanAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element !is VolanFile) return
        val insight = SchemaInsight.read(element)
        val length = element.textLength
        for (diagnostic in insight.diagnostics) {
            val start = diagnostic.span.start.coerceIn(0, length)
            val end = diagnostic.span.end.coerceIn(start, length)
            val range = if (end > start || length == 0) TextRange(start, end)
                else TextRange(start.coerceAtMost(length - 1), (start + 1).coerceAtMost(length))
            val message = "[${diagnostic.code.id}] ${diagnostic.message}" + (diagnostic.help?.let { ". $it" } ?: "")
            holder.newAnnotation(if (diagnostic.isError) HighlightSeverity.ERROR else HighlightSeverity.WARNING, message)
                .range(range).create()
        }
        insight.symbols.forEach { symbol ->
            val key = when (symbol.role) {
                "keyword" -> Colors.KEYWORD; "type", "declaration" -> Colors.CLASS_NAME
                "attribute" -> Colors.METADATA; "function" -> Colors.FUNCTION_CALL
                else -> null
            }
            if (key != null && symbol.span.end <= length) holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(TextRange(symbol.span.start, symbol.span.end)).textAttributes(key).create()
        }
    }
}
