package io.github.thirtyeighttwentysix.volan.idea

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

class VolanCompletionPopup : TypedHandlerDelegate() {
    override fun checkAutoPopup(charTyped: Char, project: Project, editor: Editor, file: PsiFile): Result {
        if (file !is VolanFile || charTyped !in charArrayOf('@', '.', ':', '[', ' ')) return Result.CONTINUE
        val source = editor.document.text
        val offset = editor.caretModel.offset
        // The callback runs before the character is inserted. Check the prospective context.
        val prospective = source.substring(0, offset) + charTyped + source.substring(offset)
        if (!shouldAutoPopup(prospective, offset + 1, charTyped)) return Result.CONTINUE
        AutoPopupController.getInstance(project).scheduleAutoPopup(editor)
        return Result.STOP
    }

    companion object {
        /** Called on the typing thread: inspect one line without parsing the entire schema. */
        fun shouldAutoPopup(text: String, offset: Int, charTyped: Char): Boolean {
            val start = text.lastIndexOf('\n', (offset - 1).coerceAtLeast(0)) + 1
            val line = text.substring(start, offset)
            val lexer = VolanLexer(); lexer.start(line)
            var lastType = lexer.tokenType
            while (lexer.tokenType != null) { lastType = lexer.tokenType; lexer.advance() }
            if (lastType in listOf(VolanTokens.COMMENT, VolanTokens.DOC_COMMENT, VolanTokens.STRING)) return false
            return when (charTyped) {
                '@', ':', '[' -> true
                '.' -> line.trimEnd().endsWith("@db.")
                ' ' -> Regex(".*[\\p{L}_][\\p{L}\\p{N}_]*\\s+(?:=\\s*)?$").matches(line.trimStart())
                else -> false
            }
        }
    }
}
