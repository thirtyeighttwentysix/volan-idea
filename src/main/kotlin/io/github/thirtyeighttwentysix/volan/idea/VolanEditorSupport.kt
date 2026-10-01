package io.github.thirtyeighttwentysix.volan.idea

import com.intellij.lang.BracePair
import com.intellij.lang.Commenter
import com.intellij.lang.PairedBraceMatcher
import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType

class VolanBraceMatcher : PairedBraceMatcher {
    override fun getPairs() = arrayOf(BracePair(VolanTokens.LBRACE, VolanTokens.RBRACE, true),
        BracePair(VolanTokens.LPAREN, VolanTokens.RPAREN, false), BracePair(VolanTokens.LBRACKET, VolanTokens.RBRACKET, false))
    override fun isPairedBracesAllowedBeforeType(lbraceType: IElementType, contextType: IElementType?) = true
    override fun getCodeConstructStart(file: PsiFile, openingBraceOffset: Int) = openingBraceOffset
}

class VolanCommenter : Commenter {
    override fun getLineCommentPrefix() = "//"
    override fun getBlockCommentPrefix(): String? = null
    override fun getBlockCommentSuffix(): String? = null
    override fun getCommentedBlockCommentPrefix(): String? = null
    override fun getCommentedBlockCommentSuffix(): String? = null
}

class VolanFoldingBuilder : FoldingBuilderEx() {
    override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<FoldingDescriptor> {
        val stack = mutableListOf<InsightToken>()
        val regions = mutableListOf<FoldingDescriptor>()
        schemaTokens(root.text).forEach { token ->
            if (token.type == VolanTokens.LBRACE) stack.add(token)
            else if (token.type == VolanTokens.RBRACE && stack.isNotEmpty()) {
                val start = stack.removeAt(stack.lastIndex).start
                if (document.getLineNumber(start) < document.getLineNumber(token.end - 1))
                    regions.add(FoldingDescriptor(root.node, TextRange(start, token.end)))
            }
        }
        return regions.toTypedArray()
    }
    override fun getPlaceholderText(node: ASTNode) = "{ … }"
    override fun isCollapsedByDefault(node: ASTNode) = false
}
