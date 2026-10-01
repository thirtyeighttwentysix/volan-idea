package io.github.thirtyeighttwentysix.volan.idea

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.util.ProcessingContext

class VolanDocumentationProvider : AbstractDocumentationProvider() {
    override fun generateDoc(element: PsiElement, originalElement: PsiElement?): String? {
        val source = originalElement ?: element
        val file = source.containingFile as? VolanFile ?: return null
        val insight = SchemaInsight.read(file)
        val symbol = insight.symbolAt(source.textRange.startOffset)
            ?: insight.symbolAt(element.textRange.startOffset) ?: return null
        val escaped = StringUtil.escapeXmlEntities(symbol.description).replace("\n", "<br>")
        return "<div class='definition'><pre>${StringUtil.escapeXmlEntities(symbol.name)}</pre></div><div class='content'>$escaped</div>"
    }
}

class VolanReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(PlatformPatterns.psiElement(VolanWord::class.java), object : PsiReferenceProvider() {
            override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
                val file = element.containingFile as? VolanFile ?: return PsiReference.EMPTY_ARRAY
                val symbol = SchemaInsight.read(file).symbolAt(element.textRange.startOffset) ?: return PsiReference.EMPTY_ARRAY
                if (symbol.target == null) return PsiReference.EMPTY_ARRAY
                return arrayOf(object : PsiReferenceBase<PsiElement>(element, TextRange(0, element.textLength), false) {
                    override fun resolve(): PsiElement? {
                        val current = SchemaInsight.read(element.containingFile).symbolAt(element.textRange.startOffset) ?: return null
                        val target = current.target ?: return null
                        val leaf = element.containingFile.findElementAt(target.start) ?: return null
                        return if (leaf.parent is VolanWord) leaf.parent else leaf
                    }
                    override fun getVariants(): Array<Any> = emptyArray()
                })
            }
        })
    }
}
