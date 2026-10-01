package io.github.thirtyeighttwentysix.volan.idea

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.extapi.psi.PsiFileBase
import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiParser
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet

class VolanFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, VolanLanguage) {
    override fun getFileType() = VolanFileType.INSTANCE
    override fun toString() = "Volan schema"
}

class VolanWord(node: ASTNode) : ASTWrapperPsiElement(node) {
    override fun getReferences(): Array<PsiReference> = ReferenceProvidersRegistry.getReferencesFromProviders(this)
}

class VolanParserDefinition : ParserDefinition {
    override fun createLexer(project: Project?) = VolanLexer()
    override fun createParser(project: Project?) = object : PsiParser {
        override fun parse(root: IElementType, builder: PsiBuilder): ASTNode {
            val file = builder.mark()
            // Keep a lossless PSI even for incomplete input. The reference engine supplies
            // syntax/semantic diagnostics and a recovered AST, cached per PSI modification.
            while (!builder.eof()) {
                if (builder.tokenType == VolanTokens.IDENTIFIER) {
                    val word = builder.mark(); builder.advanceLexer(); word.done(VolanTokens.WORD)
                } else builder.advanceLexer()
            }
            file.done(root)
            return builder.treeBuilt
        }
    }
    override fun getFileNodeType() = FILE
    override fun getCommentTokens() = TokenSet.create(VolanTokens.COMMENT, VolanTokens.DOC_COMMENT)
    override fun getStringLiteralElements() = TokenSet.create(VolanTokens.STRING)
    override fun createElement(node: ASTNode): PsiElement = VolanWord(node)
    override fun createFile(viewProvider: FileViewProvider): PsiFile = VolanFile(viewProvider)
    companion object { val FILE = IFileElementType(VolanLanguage) }
}
