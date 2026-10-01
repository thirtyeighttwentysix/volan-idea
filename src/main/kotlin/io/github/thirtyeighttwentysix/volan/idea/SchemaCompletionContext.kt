package io.github.thirtyeighttwentysix.volan.idea

import io.github.thirtyeighttwentysix.volan.schema.ast.*

/** Semantic assistance uses the recovered schema, not only the text of the current line. */
class SchemaCompletionContext(
    val document: SchemaDocument,
    val model: ModelDeclaration?,
    val field: FieldDeclaration?,
    val wordStart: Int,
) {
    val provider = (document.datasources.firstOrNull()?.entries?.firstOrNull { it.key.text == "provider" }?.value as? StringLiteral)?.value
    val target = document.models.firstOrNull { it.name.text == field?.type?.name?.text }
    val relation = field?.attributes?.firstOrNull { it.name.qualifiedName == "relation" }
    val localFields get() = nameList(relation?.arguments?.firstOrNull { it.name?.text == "fields" }?.value)
    val referencedFields get() = nameList(relation?.arguments?.firstOrNull { it.name?.text == "references" }?.value)

    fun isScalar(field: FieldDeclaration) = document.models.none { it.name.text == field.type.name.text }
    private fun has(field: FieldDeclaration, name: String) = field.attributes.any { it.name.qualifiedName == name }
    fun hasPrimaryKey() = model?.fields?.any { f -> f.attributes.any { it.name.qualifiedName == "id" && it.name.span.start != wordStart } } == true ||
        model?.attributes?.any { it.name.qualifiedName == "id" && it.name.span.start != wordStart } == true

    fun attributes(block: Boolean, isEnum: Boolean): List<String> {
        if (isEnum) return if (block) {
            if (model == null) listOf("map") else emptyList()
        } else listOf("map")
        val names = when {
            block -> listOf("id", "unique", "index", "fulltext", "map", "ignore")
            target != null -> listOf("relation", "ignore")
            else -> listOf("id", "unique", "default", "map", "updatedAt", "ignore", "db")
        }
        val existing = if (block) model?.attributes?.filter { it.name.span.start != wordStart }?.map { it.name.qualifiedName }.orEmpty()
            else field?.attributes?.filter { it.name.span.start != wordStart }?.map { it.name.qualifiedName }.orEmpty()
        return names.filter { name ->
            when {
                name in existing && (!block || name in listOf("id", "map", "ignore")) -> false
                name == "id" && (hasPrimaryKey() || !block && field?.type?.arity != TypeArity.REQUIRED) -> false
                name == "unique" && !block && field?.type?.arity == TypeArity.LIST -> false
                name == "updatedAt" && (field?.type?.name?.text != "DateTime" || field.type.arity == TypeArity.LIST) -> false
                name == "db" && (provider == "sqlite" || existing.any { it.startsWith("db.") } || document.enums.any { it.name.text == field?.type?.name?.text }) -> false
                name == "fulltext" && provider == "sqlite" -> false
                else -> true
            }
        }
    }

    /** Primary key first, then unique constraints. Composite keys remain ordered groups. */
    fun keys(model: ModelDeclaration?): List<List<FieldDeclaration>> {
        if (model == null) return emptyList()
        val byName = model.fields.associateBy { it.name.text }
        val groups = buildList {
            model.fields.filter { has(it, "id") && it.type.arity == TypeArity.REQUIRED }.forEach { add(listOf(it.name.text)) }
            model.attributes.filter { it.name.qualifiedName == "id" }.forEach { add(nameList(it.arguments.firstOrNull()?.value)) }
            model.fields.filter { has(it, "unique") }.forEach { add(listOf(it.name.text)) }
            model.attributes.filter { it.name.qualifiedName == "unique" }.forEach { add(nameList(it.arguments.firstOrNull()?.value)) }
        }
        return groups.filter { it.isNotEmpty() }.mapNotNull { group ->
            val fields = group.mapNotNull { byName[it] }
            fields.takeIf { it.size == group.size && it.all { field -> isScalar(field) && field.type.arity != TypeArity.LIST } }
        }.distinctBy { group -> group.map { it.name.text } }
    }

    fun preferredKey(): List<FieldDeclaration> = keys(target).firstOrNull().orEmpty()

    /** Infer only unambiguous existing FK fields; never invent or silently add columns. */
    fun inferredForeignKey(): List<FieldDeclaration> {
        if (field == null || model == null || field.type.arity == TypeArity.LIST) return emptyList()
        val used = mutableSetOf<String>()
        return preferredKey().map { reference ->
            val names = listOf(field.name.text + reference.name.text.replaceFirstChar { it.uppercase() },
                target!!.name.text.replaceFirstChar { it.lowercase() } + reference.name.text.replaceFirstChar { it.uppercase() })
            val found = model.fields.filter { isScalar(it) && it.type.arity != TypeArity.LIST &&
                it.type.name.text == reference.type.name.text && it.name.text in names && it.name.text !in used }
            val match = names.firstNotNullOfOrNull { name -> found.singleOrNull { it.name.text == name } } ?: return emptyList()
            used.add(match.name.text)
            match
        }
    }

    fun relationTemplate(): String {
        if (field?.type?.arity == TypeArity.LIST) return "relation(\"\")"
        val local = inferredForeignKey()
        return if (local.isNotEmpty()) "relation(fields: [${local.joinToString { it.name.text }}], references: [${preferredKey().joinToString { it.name.text }}])"
            else "relation(fields: [], references: [])"
    }

    fun allowedActions(): List<String> {
        val foreignKeys = localFields.mapNotNull { name -> model?.fields?.firstOrNull { it.name.text == name } }
            .ifEmpty { inferredForeignKey() }
        return listOf("Cascade", "Restrict", "NoAction", "SetNull", "SetDefault")
            .filter { it != "SetNull" || foreignKeys.isEmpty() || foreignKeys.all { f -> f.type.arity == TypeArity.OPTIONAL } }
    }

    fun nativeTypes(): List<String> {
        if (provider == "sqlite" || target != null) return emptyList()
        val mysql = provider in listOf("mysql", "mariadb")
        return when (field?.type?.name?.text) {
            "String" -> listOf("VarChar", "Char", "Text")
            "Int" -> if (mysql) listOf("Int", "SmallInt", "TinyInt") else listOf("Integer", "SmallInt")
            "Long" -> listOf("BigInt")
            "Float" -> if (mysql) listOf("Float") else listOf("Real")
            "Double" -> if (mysql) listOf("Double") else listOf("DoublePrecision")
            "Decimal" -> listOf("Decimal")
            "DateTime" -> if (mysql) listOf("DateTime", "Timestamp") else listOf("Timestamp", "Timestamptz")
            "Date" -> listOf("Date"); "Time" -> listOf("Time")
            "Uuid" -> if (mysql) listOf("Char") else listOf("Uuid")
            "Json" -> if (provider == "postgresql" || provider == null) listOf("JsonB", "Json") else listOf("Json")
            "Boolean" -> listOf("Boolean")
            "Bytes" -> if (mysql) listOf("Blob", "Binary", "VarBinary") else listOf("ByteA")
            else -> emptyList()
        }
    }

    fun typePriority(type: String, fieldName: String?): Double {
        val name = fieldName?.lowercase().orEmpty()
        if (fieldName != null && fieldName.endsWith("Id")) {
            val relationName = fieldName.removeSuffix("Id")
            val related = model?.fields?.firstOrNull { it.name.text == relationName }?.type?.name?.text
                ?: document.models.firstOrNull { it.name.text.equals(relationName, true) }?.name?.text
            val targetModel = document.models.firstOrNull { it.name.text == related }
            if (keys(targetModel).firstOrNull()?.singleOrNull()?.type?.name?.text == type) return 200.0
        }
        if (type.lowercase() == name || type.lowercase() + "s" == name) return 150.0
        return when {
            name.endsWith("at") || name.endsWith("timestamp") -> if (type == "DateTime") 100.0 else 0.0
            name.startsWith("is") || name.startsWith("has") || name in listOf("enabled", "active", "deleted") -> if (type == "Boolean") 100.0 else 0.0
            name in listOf("email", "name", "title", "description", "url", "password", "slug") -> if (type == "String") 100.0 else 0.0
            name == "id" || name.endsWith("id") || name.endsWith("count") -> if (type == "Int") 100.0 else 0.0
            else -> 0.0
        }
    }

    companion object {
        fun nameList(value: Expression?): List<String> = (value as? ArrayLiteral)?.elements?.mapNotNull { (it as? ConstantReference)?.name?.text }.orEmpty()
    }
}

/** Split only outer commas/colons: a colon in a previous argument is not the current argument. */
class AttributeCompletionContext(val body: List<InsightToken>) {
    private val segments = mutableListOf<List<InsightToken>>()
    init {
        var start = 0; var depth = 0
        body.forEachIndexed { i, token ->
            when (token.text) {
                "[", "(" -> depth++
                "]", ")" -> depth--
                "," -> if (depth == 0) { segments.add(body.subList(start, i)); start = i + 1 }
            }
        }
        segments.add(body.subList(start, body.size))
    }
    val current = segments.last()
    val hasPreviousArgument = segments.size > 1
    val named = if (current.getOrNull(1)?.text == ":") current.first().text else null
    val usedNames = segments.dropLast(1).mapNotNull { if (it.getOrNull(1)?.text == ":") it.first().text else null }.toSet()
    val value = if (named != null) current.drop(2) else current
    val inList = value.firstOrNull()?.text == "[" && value.lastOrNull()?.text != "]"
    val selected = if (inList) value.drop(1).filter { it.type == VolanTokens.IDENTIFIER }.map { it.text } else emptyList()
    val expectsValue = value.isEmpty()
}
