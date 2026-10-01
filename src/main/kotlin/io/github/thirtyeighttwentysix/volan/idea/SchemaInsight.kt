package io.github.thirtyeighttwentysix.volan.idea

import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import io.github.thirtyeighttwentysix.volan.ir.SchemaLoader
import io.github.thirtyeighttwentysix.volan.schema.ParseResult
import io.github.thirtyeighttwentysix.volan.schema.SchemaParser
import io.github.thirtyeighttwentysix.volan.schema.SourceSpan
import io.github.thirtyeighttwentysix.volan.schema.ast.*

data class SchemaSymbol(val span: SourceSpan, val name: String, val description: String,
                        val target: SourceSpan? = null, val role: String = "name")

class SchemaInsight(val parse: ParseResult) {
    val document get() = parse.document
    val diagnostics by lazy { SchemaLoader.analyze(parse).diagnostics }
    val symbols: List<SchemaSymbol> by lazy { buildSymbols() }
    fun symbolAt(offset: Int) = symbols.filter { offset >= it.span.start && offset < it.span.end }
        .minByOrNull { it.span.length }

    private fun buildSymbols(): List<SchemaSymbol> = buildList {
        val declarations = document.declarations.associateBy { it.name.text }
        fun addName(id: Identifier, description: String, target: SourceSpan? = null, role: String = "name") {
            add(SchemaSymbol(id.span, id.text, description, target, role))
        }
        fun expression(value: Expression, model: ModelDeclaration?, field: FieldDeclaration?, argument: String?) {
            when (value) {
                is ConstantReference -> {
                    val targetModel = if (argument == "references")
                        document.models.firstOrNull { it.name.text == field?.type?.name?.text } else model
                    val enum = document.enums.firstOrNull { it.name.text == field?.type?.name?.text }
                    val targetField = targetModel?.fields?.firstOrNull { it.name.text == value.name.text }
                    val enumValue = enum?.values?.firstOrNull { it.name.text == value.name.text }
                    val isFieldArgument = argument in listOf("fields", "references", "constraint")
                    val target = if (isFieldArgument) targetField?.name?.span else enumValue?.name?.span
                    val description = if (targetField != null && isFieldArgument)
                        "${targetModel.name.text}.${targetField.name.text}: ${targetField.type}"
                    else if (enumValue != null) "${enum?.name?.text}.${value.name.text}"
                    else VolanCatalog.docs[value.name.text] ?: value.name.text
                    addName(value.name, description, target)
                }
                is ArrayLiteral -> value.elements.forEach { expression(it, model, field, argument) }
                is FunctionCall -> {
                    addName(value.name, VolanCatalog.docs[value.name.text] ?: "${value.name.text}()", role = "function")
                    value.arguments.forEach { expression(it.value, model, field, it.name?.text) }
                }
                else -> Unit
            }
        }
        fun attribute(name: AttributeName, args: List<Argument>, model: ModelDeclaration?, field: FieldDeclaration?, block: Boolean) {
            val key = (if (block) "@@" else "@") + name.qualifiedName
            add(SchemaSymbol(name.span, key, VolanCatalog.docs[key]
                ?: if (name.namespace?.text == "db") "Native database type ${name.qualifiedName}; its meaning depends on the datasource."
                else key, role = "attribute"))
            args.forEach { arg ->
                arg.name?.let { addName(it, VolanCatalog.docs[it.text] ?: it.text) }
                val argument = arg.name?.text ?: if (block && name.name.text in listOf("id", "unique", "index", "fulltext")) "constraint" else null
                expression(arg.value, model, field, argument)
            }
        }
        for (declaration in document.declarations) {
            val kind = when (declaration) {
                is ModelDeclaration -> "model"; is EnumDeclaration -> "enum"
                is DatasourceDeclaration -> "datasource"; is GeneratorDeclaration -> "generator"
            }
            val docs = declaration.leadingComments.filter { it.isDoc }.joinToString("\n") { it.text }
            addName(declaration.name, "$kind ${declaration.name.text}" + if (docs.isEmpty()) "" else "\n$docs", role = "declaration")
            // Keywords remain valid names in every other position.
            val keywordStart = parse.source.text.indexOf(kind, declaration.span.start)
            if (keywordStart in declaration.span.start until declaration.name.span.start)
                add(SchemaSymbol(SourceSpan(keywordStart, keywordStart + kind.length), kind, VolanCatalog.docs.getValue(kind), role = "keyword"))
            when (declaration) {
                is ModelDeclaration -> {
                    for (field in declaration.fields) {
                        val fieldDocs = field.leadingComments.filter { it.isDoc }.joinToString("\n") { it.text }
                        addName(field.name, "${declaration.name.text}.${field.name.text}: ${field.type}" + if (fieldDocs.isEmpty()) "" else "\n$fieldDocs")
                        val type = field.type.name
                        addName(type, VolanCatalog.docs[type.text] ?: "${if (declarations[type.text] is EnumDeclaration) "enum" else "model"} ${type.text}",
                            declarations[type.text]?.name?.span, "type")
                        field.attributes.forEach { attribute(it.name, it.arguments, declaration, field, false) }
                    }
                    declaration.attributes.forEach { attribute(it.name, it.arguments, declaration, null, true) }
                }
                is EnumDeclaration -> {
                    declaration.values.forEach { value ->
                        addName(value.name, "${declaration.name.text}.${value.name.text}")
                        value.attributes.forEach { attribute(it.name, it.arguments, null, null, false) }
                    }
                    declaration.attributes.forEach { attribute(it.name, it.arguments, null, null, true) }
                }
                else -> {
                    val entries = if (declaration is DatasourceDeclaration) declaration.entries else (declaration as GeneratorDeclaration).entries
                    entries.forEach { entry ->
                        addName(entry.key, VolanCatalog.docs["$kind.${entry.key.text}"] ?: entry.key.text)
                        expression(entry.value, null, null, null)
                    }
                }
            }
        }
    }

    companion object {
        fun read(file: PsiFile): SchemaInsight = CachedValuesManager.getCachedValue(file) {
            CachedValueProvider.Result.create(SchemaInsight(SchemaParser.parse(file.name, file.text)), file)
        }
    }
}

object VolanCatalog {
    val docs = mapOf(
        "model" to "Declares a database table. Each model needs @id or @@id unless it has @@ignore.",
        "enum" to "Declares named values usable as a field type. Values can be mapped with @map.",
        "datasource" to "Database configuration: provider and url = env(\"DATABASE_URL\"). One per schema.",
        "generator" to "Client generation configuration: provider, package, output, javaFriendly.",
        "String" to "Text; generated as String.", "Int" to "32-bit signed integer; generated as Int / int.",
        "Long" to "64-bit signed integer; generated as Long / long.",
        "Float" to "32-bit floating point.", "Double" to "64-bit floating point.",
        "Decimal" to "Exact decimal; generated as java.math.BigDecimal.", "Boolean" to "Boolean value.",
        "DateTime" to "Date and time; generated as java.time.Instant.",
        "Date" to "Date without time; generated as java.time.LocalDate.",
        "Time" to "Time without date; generated as java.time.LocalTime.",
        "Json" to "JSON data; generated as Volan Json.", "Bytes" to "Binary data; generated as ByteArray / byte[].",
        "Uuid" to "UUID; generated as java.util.UUID.",
        "@id" to "Primary key. The field must be required and hold a single value. Optional argument: map.",
        "@unique" to "Unique constraint on a single field. Optional argument: map.",
        "@default" to "Default value matching the field type, for example @default(now()) or @default(USER).",
        "@map" to "Database column or enum value name: @map(\"database_name\").",
        "@updatedAt" to "Maintain the timestamp when a row changes. Requires a supported temporal field.",
        "@ignore" to "Exclude this field from the generated client.",
        "@relation" to "Relation metadata: name, fields, references, onDelete, onUpdate. Both ends must match.",
        "@@id" to "Composite primary key: @@id([first, second], map: \"pk_name\").",
        "@@unique" to "Composite unique constraint: @@unique([first, second], map: \"constraint_name\").",
        "@@index" to "Index over scalar fields: @@index([first, second], map: \"index_name\").",
        "@@fulltext" to "Full-text index over the listed fields. Database support depends on the provider.",
        "@@map" to "Database table or enum type name: @@map(\"database_name\").",
        "@@ignore" to "Exclude this model from client generation; a primary key is not required.",
        "env" to "env(\"NAME\"): read a connection URL from an environment variable when the schema is used.",
        "autoincrement" to "autoincrement(): database-generated number, valid for Int and Long.",
        "now" to "now(): current date/time, valid for DateTime, Date and Time.",
        "uuid" to "uuid(): generated UUID, valid for Uuid and String.",
        "cuid" to "cuid(): generated collision-resistant identifier, valid for String.",
        "dbgenerated" to "dbgenerated(\"SQL expression\"): default expression supplied by the database.",
        "fields" to "Local scalar foreign-key fields, paired with references in the target model.",
        "references" to "Fields in the target model identifying a primary key or unique constraint.",
        "onDelete" to "Referential action when a referenced row is deleted.",
        "onUpdate" to "Referential action when a referenced key is updated.",
        "Cascade" to "Propagate deletion or update to dependent rows.",
        "Restrict" to "Reject deletion or update while dependent rows exist.",
        "NoAction" to "Use the database's no-action constraint behavior.",
        "SetNull" to "Set foreign keys to null; requires nullable foreign-key fields.",
        "SetDefault" to "Set foreign keys to their default values.",
        "datasource.provider" to "Database provider: postgresql, mysql, mariadb, sqlite or h2. Runtime support varies by ORM release.",
        "datasource.url" to "Connection URL as env(\"DATABASE_URL\") or a string literal.",
        "generator.provider" to "Generator provider: \"volan-kotlin\".",
        "generator.package" to "Package for generated Kotlin and Java API classes.",
        "generator.output" to "Generated source directory, relative to the schema project.",
        "generator.javaFriendly" to "true enables Java callbacks, getters, builders and asynchronous API."
    )
}
