package io.github.thirtyeighttwentysix.volan.idea

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.AutoCompletionPolicy
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.openapi.project.DumbAware
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import com.intellij.util.ProcessingContext
import io.github.thirtyeighttwentysix.volan.ir.Provider
import io.github.thirtyeighttwentysix.volan.ir.ScalarType
import io.github.thirtyeighttwentysix.volan.schema.SchemaParser
import io.github.thirtyeighttwentysix.volan.schema.ast.*

data class CompletionItem(val lookup: String, val insert: String = lookup,
                          val detail: String = "", val caret: Int = insert.length, val priority: Double = 0.0)
data class CompletionSuggestions(val prefix: String, val items: List<CompletionItem>)
data class InsightToken(val text: String, val start: Int, val end: Int, val type: IElementType)

fun schemaTokens(text: String): List<InsightToken> = buildList {
    val lexer = VolanLexer(); lexer.start(text)
    while (lexer.tokenType != null) {
        val type = lexer.tokenType!!
        if (type != TokenType.WHITE_SPACE && type != VolanTokens.COMMENT && type != VolanTokens.DOC_COMMENT)
            add(InsightToken(text.substring(lexer.tokenStart, lexer.tokenEnd), lexer.tokenStart, lexer.tokenEnd, type))
        lexer.advance()
    }
}

/** Context comes from tokens, so braces in strings/comments and multiline arguments are safe. */
object VolanCompletion {
    fun suggest(text: String, offset: Int): CompletionSuggestions {
        val caret = offset.coerceIn(0, text.length)
        val lexer = VolanLexer(); lexer.start(text)
        var active: InsightToken? = null
        while (lexer.tokenType != null) {
            if (caret > lexer.tokenStart && caret <= lexer.tokenEnd) {
                active = InsightToken(text.substring(lexer.tokenStart, lexer.tokenEnd), lexer.tokenStart, lexer.tokenEnd, lexer.tokenType!!)
                break
            }
            lexer.advance()
        }
        if (active?.type in listOf(VolanTokens.COMMENT, VolanTokens.DOC_COMMENT)) return CompletionSuggestions("", emptyList())
        val inString = active?.type == VolanTokens.STRING && caret > active.start &&
            (caret < active.end || !active.text.endsWith('"') || active.text.length == 1)
        val wordStart = when {
            inString -> active.start + 1
            active?.type == VolanTokens.IDENTIFIER -> active.start
            else -> caret
        }
        val prefix = text.substring(wordStart, caret)
        val tokens = schemaTokens(text).filter { it.end <= wordStart }
        val values = tokens.map { it.text }
        val doc = SchemaParser.parse("completion.volan", text).document

        var blockKind: String? = null
        var blockName: String? = null
        var blockStart = -1
        var depth = 0
        tokens.forEachIndexed { index, token ->
            if (token.text == "{") {
                if (depth == 0) {
                    blockKind = tokens.getOrNull(index - 2)?.text
                    blockName = tokens.getOrNull(index - 1)?.text
                    blockStart = index
                }
                depth++
            } else if (token.text == "}") {
                depth--
                if (depth <= 0) { blockKind = null; blockName = null; blockStart = -1; depth = 0 }
            }
        }
        val model = doc.models.firstOrNull { it.name.text == blockName }
        val enum = doc.enums.firstOrNull { it.name.text == blockName }
        val items = mutableListOf<CompletionItem>()
        fun add(name: String, insert: String = name, detail: String = "", cursor: Int = insert.length, priority: Double = 0.0) {
            items.add(CompletionItem(name, insert, detail, cursor, priority))
        }
        fun types(fieldName: String?) {
            val semantic = SchemaCompletionContext(doc, model, null, wordStart)
            ScalarType.names().filter { it != "Decimal" || semantic.provider != "sqlite" }
                .forEach { add(it, detail = "scalar", priority = semantic.typePriority(it, fieldName)) }
            doc.models.forEach { add(it.name.text, detail = "model", priority = semantic.typePriority(it.name.text, fieldName)) }
            doc.enums.forEach { add(it.name.text, detail = "enum", priority = semantic.typePriority(it.name.text, fieldName)) }
        }
        fun quoted(value: String, detail: String = "") = add(value, if (inString) value else "\"$value\"", detail)
        fun result() = CompletionSuggestions(prefix, items.distinctBy { it.lookup }.sortedByDescending { it.priority })

        if (blockKind == null) {
            if (!inString && (tokens.isEmpty() || values.lastOrNull() == "}")) {
                if (doc.datasources.isEmpty()) add("datasource", "datasource db {\n  provider = \"postgresql\"\n  url = env(\"DATABASE_URL\")\n}", "database configuration")
                if (doc.generators.isEmpty()) add("generator", "generator client {\n  provider = \"volan-kotlin\"\n  package = \"com.example.db\"\n  output = \"build/generated/volan\"\n  javaFriendly = true\n}", "Kotlin + Java client")
                var modelName = "Model"; var suffix = 2
                while (doc.declarations.any { it.name.text == modelName }) modelName = "Model${suffix++}"
                add("model", "model $modelName {\n  id Int @id @default(autoincrement())\n}", "table", "model ".length)
                var enumName = "Enum"; suffix = 2
                while (doc.declarations.any { it.name.text == enumName }) enumName = "Enum${suffix++}"
                add("enum", "enum $enumName {\n  VALUE\n}", "named values", "enum ".length)
            }
            return result()
        }
        if (blockKind == "datasource" || blockKind == "generator") {
            val body = tokens.drop(blockStart + 1)
            val equalsIndex = body.indexOfLast { it.text == "=" }
            val key = body.getOrNull(equalsIndex - 1)?.text
            val lineStart = text.lastIndexOf('\n', (wordStart - 1).coerceAtLeast(0)) + 1
            val line = text.substring(lineStart, wordStart).substringAfterLast('{').trim()
            if (equalsIndex >= 0 && (inString || body.lastOrNull()?.text == "=" || key == "url" && line.contains('='))) {
                when (key) {
                    "provider" -> if (blockKind == "datasource") Provider.ids().forEach { quoted(it, "database provider") }
                        else quoted("volan-kotlin", "Kotlin + Java client")
                    "url" -> if (!inString) add("env", "env(\"DATABASE_URL\")", "environment URL")
                    "javaFriendly" -> { add("true"); add("false") }
                    else -> Unit
                }
            } else if (!inString && !line.contains('=')) {
                val keys = if (blockKind == "datasource") listOf("provider", "url") else listOf("provider", "package", "output", "javaFriendly")
                val existing = if (blockKind == "datasource") doc.datasources.firstOrNull { it.name.text == blockName }?.entries
                    else doc.generators.firstOrNull { it.name.text == blockName }?.entries
                keys.filter { keyName -> existing.orEmpty().none { it.key.text == keyName && it.key.span.start != wordStart } }
                    .forEach { add(it, "$it = ", VolanCatalog.docs["$blockKind.$it"] ?: "property") }
            }
            return result()
        }
        // Find the innermost open attribute call, retaining it while a nested default call is open.
        val parens = mutableListOf<Int>()
        values.forEachIndexed { i, value ->
            if (value == "(") parens.add(i)
            else if (value == ")" && parens.isNotEmpty()) parens.removeAt(parens.lastIndex)
        }
        val attributeOpen = parens.lastOrNull { i -> values.getOrNull(i - 2) in listOf("@", "@@") }
        val attrName = attributeOpen?.let { values[it - 1] }
        val attrBlock = attributeOpen?.let { values[it - 2] == "@@" } ?: false
        val lineStart = text.lastIndexOf('\n', (wordStart - 1).coerceAtLeast(0)) + 1
        val fieldPrefix = text.substring(lineStart, wordStart).substringAfterLast('{').trim()
        val fieldName = Regex("^([\\p{L}_][\\p{L}\\p{N}_]*)").find(fieldPrefix)?.groupValues?.get(1)
        val field = model?.fields?.lastOrNull { it.span.start <= caret && it.span.end >= caret }
            ?: if (attributeOpen != null) model?.fields?.lastOrNull { it.span.start < tokens[attributeOpen].start }
            else model?.fields?.firstOrNull { it.name.text == fieldName }
        val semantic = SchemaCompletionContext(doc, model, field, wordStart)

        if (attributeOpen != null) {
            val args = AttributeCompletionContext(tokens.drop(attributeOpen + 1))
            val named = args.named
            if (inString || parens.lastOrNull() != attributeOpen) return result()
            if (args.inList && (attrBlock && attrName in listOf("id", "unique", "index", "fulltext") || named in listOf("fields", "references"))) {
                val owner = if (named == "references") semantic.target else model
                val slot = args.selected.size
                val counterpartNames = if (named == "references") semantic.localFields else semantic.referencedFields
                val counterpartOwner = if (named == "references") model else semantic.target
                val expected = counterpartNames.getOrNull(slot)?.let { name -> counterpartOwner?.fields?.firstOrNull { it.name.text == name }?.type?.name?.text }
                    ?: if (named == "fields" && counterpartNames.isEmpty()) semantic.preferredKey().getOrNull(slot)?.type?.name?.text else null
                val keyCandidates = if (named == "references") semantic.keys(owner)
                    .filter { key -> key.take(slot).map { it.name.text } == args.selected }
                    .mapNotNull { it.getOrNull(slot)?.name?.text }.toSet() else null
                owner?.fields?.filter { f -> semantic.isScalar(f) && f.name.text !in args.selected &&
                    (keyCandidates == null || f.name.text in keyCandidates) &&
                    (expected == null || f.type.name.text == expected) &&
                    (attrName != "id" || !attrBlock || f.type.arity == TypeArity.REQUIRED) &&
                    (named !in listOf("fields", "references") || f.type.arity != TypeArity.LIST) }
                    ?.forEach { f ->
                        val preferred = if (named == "fields") semantic.inferredForeignKey().getOrNull(slot)?.name?.text == f.name.text
                            else semantic.preferredKey().getOrNull(slot)?.name?.text == f.name.text
                        add(f.name.text, detail = "${owner.name.text}.${f.name.text}: ${f.type}", priority = if (preferred) 100.0 else 0.0)
                    }
            } else if (attrName == "default" && args.expectsValue && !args.hasPreviousArgument) {
                if (field?.type?.arity == TypeArity.LIST) {
                    add("[]", detail = "empty list", priority = 100.0)
                    return result()
                }
                when (field?.type?.name?.text) {
                    "Int", "Long" -> if (semantic.provider != "sqlite" || field.attributes.any { it.name.qualifiedName == "id" })
                        add("autoincrement", "autoincrement()", "generated number", priority = 100.0)
                    "DateTime", "Date", "Time" -> add("now", "now()", "current date/time")
                    "Uuid" -> add("uuid", "uuid()")
                    "String" -> { add("uuid", "uuid()"); add("cuid", "cuid()"); add("\"\"", detail = "empty string", cursor = 1) }
                    "Boolean" -> { add("true"); add("false") }
                    else -> doc.enums.firstOrNull { it.name.text == field?.type?.name?.text }?.values?.forEach { add(it.name.text, detail = "enum value", priority = 100.0) }
                }
                if (field?.type?.name?.text in listOf("Int", "Long", "Float", "Double", "Decimal")) add("0", detail = "numeric default")
                add("dbgenerated", "dbgenerated(\"\")", "database expression", "dbgenerated(\"".length)
            } else if (attrName == "relation" && named in listOf("onDelete", "onUpdate") && args.expectsValue) {
                semantic.allowedActions().forEach { add(it, detail = VolanCatalog.docs[it] ?: "referential action") }
            } else if (args.expectsValue && named == null) {
                val names = when (attrName) {
                    "relation" -> if (field?.type?.arity == TypeArity.LIST) listOf("name") else listOf("name", "fields", "references", "onDelete", "onUpdate")
                    "id", "unique", "index", "fulltext" -> listOf("map")
                    "map" -> listOf("name")
                    else -> emptyList()
                }
                val alreadyNamed = semantic.relation?.arguments?.mapNotNull { it.name?.text }.orEmpty() + args.usedNames
                names.filter { it !in alreadyNamed }.forEach {
                    if (it == "fields" || it == "references") {
                        val inferred = if (it == "fields") semantic.inferredForeignKey() else semantic.preferredKey()
                        val insertion = "$it: [${inferred.joinToString { f -> f.name.text }}]"
                        add(it, insertion, if (inferred.isEmpty()) "field list" else "inferred field list", if (inferred.isEmpty()) it.length + 3 else insertion.length)
                    } else add(it, "$it: ", "named argument")
                }
            }
            return result()
        }
        if (inString) return result()
        val last = values.lastOrNull()
        if (last == "." && values.getOrNull(values.lastIndex - 1) == "db") {
            semantic.nativeTypes().forEach { native ->
                val needsSize = native in listOf("VarChar", "Char", "Decimal", "Binary", "VarBinary")
                add(native, if (needsSize) "$native()" else native, "${semantic.provider ?: "database"} · ${field?.type?.name?.text}",
                    if (needsSize) native.length + 1 else native.length)
            }
        } else if (last == "@" || last == "@@") {
            val names = semantic.attributes(last == "@@", enum != null || blockKind == "enum").filter { name ->
                if (enum == null) true else if (last == "@@") enum.attributes.none { it.name.qualifiedName == name && it.name.span.start != wordStart }
                    else enum.values.lastOrNull { it.span.start <= caret }?.attributes
                        ?.none { it.name.qualifiedName == name && it.name.span.start != wordStart } ?: true
            }
            names.forEach {
                val marker = if (last == "@@") "@@" else "@"
                when (it) {
                    "default", "map" -> add(it, "$it()", VolanCatalog.docs[marker + it] ?: "attribute", it.length + 1)
                    "relation" -> {
                        val template = semantic.relationTemplate()
                        add(it, template, if (semantic.inferredForeignKey().isEmpty()) "relation" else "inferred foreign key",
                            if (field?.type?.arity == TypeArity.LIST) "relation(\"".length else if (semantic.inferredForeignKey().isEmpty()) "relation(fields: [".length else template.length)
                    }
                    "id", "unique", "index", "fulltext" -> if (last == "@@") add(it, "$it([])", "block attribute", it.length + 2) else add(it, detail = "field attribute")
                    "db" -> add("db", "db.", "native database type")
                    else -> add(it, detail = "attribute")
                }
            }
        } else if (blockKind == "model" && Regex("^[\\p{L}_][\\p{L}\\p{N}_]*\\s+$").matches(
                text.substring(lineStart, wordStart).substringAfterLast('{').trimStart())) types(fieldName)
        else if (blockKind == "model" && fieldPrefix.isEmpty() && last != "@" && last != "@@") {
            val occupied = model?.fields?.map { it.name.text }.orEmpty()
            if (!semantic.hasPrimaryKey() && "id" !in occupied) add("id", "id Int @id @default(autoincrement())", "primary key", priority = 100.0)
            if ("createdAt" !in occupied) add("createdAt", "createdAt DateTime @default(now())", "creation timestamp")
            if ("updatedAt" !in occupied) add("updatedAt", "updatedAt DateTime @updatedAt", "update timestamp")
        }
        return result()
    }
}

class VolanCompletionContributor : CompletionContributor(), DumbAware {
    init {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement().withLanguage(VolanLanguage), object : CompletionProvider<CompletionParameters>() {
            override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
                val suggestions = VolanCompletion.suggest(parameters.originalFile.text, parameters.offset)
                val matched = result.withPrefixMatcher(suggestions.prefix)
                for (item in suggestions.items) {
                    val lookup = LookupElementBuilder.create(item.lookup).withIcon(VolanIcons.FILE)
                    .withTypeText(item.detail, true).withBoldness(item.priority > 0).withInsertHandler { insertion, _ ->
                        // Reuse existing () / [] / quotes when completing in already written code.
                        var replacement = item.insert
                        var cursor = item.caret
                        val suffix = insertion.document.charsSequence.subSequence(insertion.tailOffset, insertion.document.textLength).toString()
                        if (replacement.endsWith("()") && suffix.startsWith("(")) {
                            replacement = replacement.removeSuffix("()")
                            cursor = if (item.caret < item.insert.length) replacement.length + 1 else replacement.length
                        } else if (replacement.endsWith(".") && suffix.startsWith(".")) {
                            replacement = replacement.removeSuffix("."); cursor = replacement.length + 1
                        }
                        insertion.document.replaceString(insertion.startOffset, insertion.tailOffset, replacement)
                        insertion.tailOffset = insertion.startOffset + replacement.length
                        insertion.editor.caretModel.moveToOffset((insertion.startOffset + cursor).coerceAtMost(insertion.document.textLength))
                        if (insertion.completionChar in charArrayOf('(', '.', '"')) insertion.setAddCompletionChar(false)
                    }
                    // Large snippets should be chosen explicitly, even with a single match.
                    val policy = if (item.insert.contains('\n')) AutoCompletionPolicy.NEVER_AUTOCOMPLETE else AutoCompletionPolicy.SETTINGS_DEPENDENT
                    matched.addElement(PrioritizedLookupElement.withPriority(policy.applyPolicy(lookup), item.priority))
                }
            }
        })
    }
    override fun beforeCompletion(context: CompletionInitializationContext) {
        context.dummyIdentifier = "VolanCompletionDummy"
    }
}
