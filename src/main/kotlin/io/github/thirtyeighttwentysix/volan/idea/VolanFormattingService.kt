package io.github.thirtyeighttwentysix.volan.idea

import com.intellij.formatting.FormattingContext
import com.intellij.formatting.service.AbstractDocumentFormattingService
import com.intellij.formatting.service.FormattingService.Feature
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import io.github.thirtyeighttwentysix.volan.schema.SchemaFormatter
import io.github.thirtyeighttwentysix.volan.schema.SchemaParser

class VolanFormattingService : AbstractDocumentFormattingService() {
    override fun getFeatures(): Set<Feature> = setOf(Feature.AD_HOC_FORMATTING)
    override fun canFormat(file: PsiFile) = file is VolanFile

    override fun formatDocument(document: Document, formattingRanges: List<TextRange>,
                                formattingContext: FormattingContext, canChangeWhiteSpaceOnly: Boolean, quickFormat: Boolean) {
        if (quickFormat) return
        // The canonical engine formats a document as a whole. Never change text outside
        // an explicit selection, or discard unrecovered text in an invalid document.
        if (formattingRanges.isNotEmpty() && formattingRanges.none { it.startOffset == 0 && it.endOffset >= document.textLength }) return
        val parsed = SchemaParser.parse(formattingContext.containingFile.name, document.text)
        if (parsed.hasErrors) return
        val formatted = SchemaFormatter.format(parsed.document)
        if (formatted == document.text) return
        if (canChangeWhiteSpaceOnly && schemaTokens(document.text).map { it.text } != schemaTokens(formatted).map { it.text }) return
        document.setText(formatted)
    }
}
