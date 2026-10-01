package io.github.thirtyeighttwentysix.volan.idea

import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.util.IconLoader
import com.intellij.psi.tree.IElementType

object VolanLanguage : Language("Volan")

object VolanIcons {
    @JvmField val FILE = IconLoader.getIcon("/icons/volan.svg", VolanIcons::class.java)
}

class VolanFileType private constructor() : LanguageFileType(VolanLanguage) {
    override fun getName() = "Volan Schema"
    override fun getDescription() = "Volan ORM schema"
    override fun getDefaultExtension() = "volan"
    override fun getIcon() = VolanIcons.FILE
    companion object { @JvmField val INSTANCE = VolanFileType() }
}

object VolanTokens {
    val IDENTIFIER = IElementType("IDENTIFIER", VolanLanguage)
    val STRING = IElementType("STRING", VolanLanguage)
    val NUMBER = IElementType("NUMBER", VolanLanguage)
    val COMMENT = IElementType("COMMENT", VolanLanguage)
    val DOC_COMMENT = IElementType("DOC_COMMENT", VolanLanguage)
    val LBRACE = IElementType("{", VolanLanguage)
    val RBRACE = IElementType("}", VolanLanguage)
    val LPAREN = IElementType("(", VolanLanguage)
    val RPAREN = IElementType(")", VolanLanguage)
    val LBRACKET = IElementType("[", VolanLanguage)
    val RBRACKET = IElementType("]", VolanLanguage)
    val AT = IElementType("@", VolanLanguage)
    val BLOCK_AT = IElementType("@@", VolanLanguage)
    val DOT = IElementType(".", VolanLanguage)
    val COLON = IElementType(":", VolanLanguage)
    val COMMA = IElementType(",", VolanLanguage)
    val EQUALS = IElementType("=", VolanLanguage)
    val QUESTION = IElementType("?", VolanLanguage)
    val WORD = IElementType("WORD", VolanLanguage)
}
