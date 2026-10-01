package io.github.thirtyeighttwentysix.volan.idea

import junit.framework.TestCase

class VolanCompletionTest : TestCase() {
    private fun suggestions(source: String): CompletionSuggestions {
        val offset = source.indexOf("<caret>")
        require(offset >= 0)
        return VolanCompletion.suggest(source.replace("<caret>", ""), offset)
    }
    private fun names(source: String) = suggestions(source).items.map { it.lookup }

    fun testTopLevelAndKeywordsAreNotReserved() {
        assertEquals(listOf("datasource", "generator", "model", "enum"), names("<caret>"))
        val result = suggestions("model model {\n model Str<caret>\n}")
        assertEquals("Str", result.prefix)
        assertTrue(result.items.any { it.lookup == "String" })
    }
    fun testDeclaredTypesInIncompleteModel() {
        val result = names("enum Role { USER }\nmodel User { id Int @id }\nmodel Post {\n author <caret>")
        assertTrue(result.containsAll(listOf("User", "Post", "Role", "String", "Uuid")))
    }
    fun testPropertiesAndNoDuplicateProperties() {
        assertEquals(listOf("provider", "url"), names("datasource db {\n <caret>\n}"))
        assertEquals(listOf("url"), names("datasource db {\n provider = \"postgresql\"\n <caret>\n}"))
        assertTrue(names("generator client {\n <caret>\n}").containsAll(listOf("provider", "package", "output", "javaFriendly")))
    }
    fun testProviderInStringAndBeforeQuotes() {
        val result = suggestions("datasource db {\n provider = \"po<caret>\"\n}")
        assertEquals("po", result.prefix)
        assertEquals("postgresql", result.items.first { it.lookup == "postgresql" }.insert)
        assertEquals("\"postgresql\"", suggestions("datasource db {\n provider = <caret>\n}").items.first { it.lookup == "postgresql" }.insert)
        assertEquals(listOf("volan-kotlin"), names("generator client { provider = \"<caret>\" }"))
    }
    fun testBooleansAndEnvironmentFunction() {
        assertEquals(listOf("true", "false"), names("generator client { javaFriendly = <caret> }"))
        assertEquals(listOf("env"), names("datasource db { url = <caret> }"))
    }
    fun testAttributeContextsAndNativeTypes() {
        assertTrue(names("model User {\n id Int @<caret>\n}").contains("id"))
        assertTrue(names("model User {\n id Int @id\n @@<caret>\n}").contains("index"))
        assertEquals(listOf("map"), names("enum Role {\n USER @<caret>\n}"))
        assertEquals(listOf("map"), names("enum Role {\n USER\n @@<caret>\n}"))
        assertTrue(names("model User {\n name String @db.<caret>\n}").contains("VarChar"))
    }
    fun testRelationAttributeAndArguments() {
        assertEquals(listOf("relation", "ignore"), names("model User { id Int @id }\nmodel Post {\n author User @<caret>\n}"))
        assertTrue(names("model User { id Int @id }\nmodel Post {\n author User @relation(<caret>)\n}").containsAll(listOf("fields", "references", "onDelete", "onUpdate")))
    }
    fun testLocalAndReferencedFieldsAcrossLines() {
        val base = "model User {\n id Int @id\n email String @unique\n}\nmodel Post {\n id Int @id\n authorId Int\n author User @relation(\n"
        assertTrue(names(base + " fields: [<caret>],\n references: [id])\n}").contains("authorId"))
        assertFalse(names(base + " fields: [<caret>],\n references: [id])\n}").contains("author"))
        assertEquals(listOf("id"), names(base + " fields: [authorId],\n references: [<caret>])\n}"))
    }
    fun testConstraintFieldsExcludeAlreadyListedFields() {
        assertEquals(listOf("name"), names("model User {\n id Int @id\n name String\n @@index([id, <caret>])\n}"))
    }
    fun testDefaultFunctionsRespectTypes() {
        assertTrue(names("model User {\n id Int @id @default(<caret>)\n}").contains("autoincrement"))
        assertFalse(names("model User {\n id Int @id @default(<caret>)\n}").contains("now"))
        assertTrue(names("model User {\n date DateTime @default(<caret>)\n}").contains("now"))
        assertTrue(names("enum Role { USER ADMIN }\nmodel User {\n role Role @default(<caret>)\n}").containsAll(listOf("USER", "ADMIN")))
    }
    fun testReferentialActions() {
        assertTrue(names("model User { id Int @id }\nmodel Post {\n author User @relation(onDelete: Cas<caret>)\n}").containsAll(listOf("Cascade", "SetNull", "Restrict")))
    }
    fun testCommentsStringsAndBracesDoNotLeakContext() {
        assertTrue(names("// model User { <caret>").isEmpty())
        assertTrue(names("model User {\n name String @map(\"<caret>\")\n}").isEmpty())
        assertEquals(listOf("generator", "model", "enum"), names("datasource db {\n url = \"{ }\"\n provider = \"postgresql\"\n}\n// {\n<caret>"))
    }
    fun testRepeatedAndConflictingAttributesAreRemoved() {
        val attrs = names("model User {\n id Int @id\n email String @unique @map(\"email\") @<caret>\n}")
        assertFalse(attrs.contains("id"))
        assertFalse(attrs.contains("unique"))
        assertFalse(attrs.contains("map"))
        assertFalse(attrs.contains("updatedAt"))
        assertTrue(attrs.contains("default"))
        assertFalse(names("model User {\n id Int @id\n @@<caret>\n}").contains("id"))
        assertFalse(names("model User {\n id Int\n @@id([id])\n @@<caret>\n}").contains("id"))
    }
    fun testOptionalAndListAttributeRules() {
        assertFalse(names("model User {\n email String? @<caret>\n}").contains("id"))
        val list = names("model User {\n tags String[] @<caret>\n}")
        assertFalse(list.contains("id"))
        assertFalse(list.contains("unique"))
        assertFalse(list.contains("updatedAt"))
        assertTrue(names("model User {\n modified DateTime? @<caret>\n}").contains("updatedAt"))
        assertFalse(names("model User {\n modified DateTime @updatedAt @<caret>\n}").contains("updatedAt"))
    }
    fun testEnumMapCannotBeRepeated() {
        assertTrue(names("enum Role {\n USER @map(\"user\") @<caret>\n}").isEmpty())
        assertTrue(names("enum Role {\n USER\n @@map(\"roles\")\n @@<caret>\n}").isEmpty())
    }
    fun testListDefaultsAndNestedCallArguments() {
        assertEquals(listOf("[]"), names("model User {\n tags String[] @default(<caret>)\n}"))
        assertTrue(names("model User {\n created DateTime @default(now(<caret>))\n}").isEmpty())
        assertTrue(names("model User {\n name String @default(uuid(), <caret>)\n}").isEmpty())
    }
    fun testForeignKeyTemplatesUseMatchingExistingColumns() {
        val source = "model User {\n id Uuid @id\n}\nmodel Post {\n id Int @id\n authorId Uuid\n author User @<caret>\n}"
        val relation = suggestions(source).items.first { it.lookup == "relation" }
        assertEquals("relation(fields: [authorId], references: [id])", relation.insert)
        val noMatch = suggestions(source.replace("authorId Uuid", "authorId Int")).items.first { it.lookup == "relation" }
        assertEquals("relation(fields: [], references: [])", noMatch.insert)
    }
    fun testCompositeKeyTemplatesAndOrderedReferenceCompletion() {
        val head = "model User {\n tenantId Int\n externalId Uuid\n @@id([tenantId, externalId])\n}\nmodel Post {\n id Int @id\n authorTenantId Int\n authorExternalId Uuid\n author User "
        val template = suggestions(head + "@<caret>\n}").items.first { it.lookup == "relation" }
        assertEquals("relation(fields: [authorTenantId, authorExternalId], references: [tenantId, externalId])", template.insert)
        assertEquals(listOf("tenantId"), names(head + "@relation(fields: [authorTenantId, authorExternalId], references: [<caret>])\n}"))
        assertEquals(listOf("externalId"), names(head + "@relation(fields: [authorTenantId, authorExternalId], references: [tenantId, <caret>])\n}"))
    }
    fun testReferencesDoNotExcludeNamesUsedInTheOtherList() {
        val source = "model User {\n id Int @id\n}\nmodel Post {\n id Int @id\n author User @relation(fields: [id], references: [<caret>])\n}"
        assertEquals(listOf("id"), names(source))
    }
    fun testReferencesUseKeysAndMatchingTypes() {
        val source = "model User {\n id Int @id\n email String @unique\n label String\n}\nmodel Post {\n id Int @id\n authorEmail String\n author User @relation(fields: [authorEmail], references: [<caret>])\n}"
        assertEquals(listOf("email"), names(source))
    }
    fun testForeignKeyFieldsAreRankedAndTypeFiltered() {
        val result = suggestions("model User { id Uuid @id }\nmodel Post {\n id Int @id\n ownerId Uuid\n authorId Uuid\n author User @relation(fields: [<caret>], references: [id])\n}")
        assertEquals(listOf("authorId", "ownerId"), result.items.map { it.lookup })
    }
    fun testSetNullRequiresNullableForeignKey() {
        val source = "model User { id Int @id }\nmodel Post {\n id Int @id\n authorId Int\n author User @relation(fields: [authorId], references: [id], onDelete: <caret>)\n}"
        assertFalse(names(source).contains("SetNull"))
        assertTrue(names(source.replace("authorId Int\n", "authorId Int?\n")).contains("SetNull"))
    }
    fun testNextArgumentDoesNotReuseThePreviousColon() {
        val source = "model User { id Int @id }\nmodel Post {\n id Int @id\n authorId Int\n author User @relation(onDelete: Cascade, <caret>)\n}"
        assertTrue(names(source).contains("fields"))
        assertFalse(names(source).contains("Cascade"))
        assertFalse(names(source).contains("onDelete"))
    }
    fun testListRelationsDoNotSuggestForeignKeyOwnership() {
        val source = "model Post { id Int @id }\nmodel User {\n id Int @id\n posts Post[] @"
        assertEquals("relation(\"\")", suggestions(source + "<caret>\n}").items.first { it.lookup == "relation" }.insert)
        assertEquals(listOf("name"), names(source + "relation(<caret>)\n}"))
    }
    fun testNativeTypesRespectTypeAndProvider() {
        assertEquals(listOf("VarChar", "Char", "Text"), names("datasource db { provider = \"postgresql\" url = env(\"URL\") }\nmodel User {\n email String @db.<caret>\n}"))
        assertEquals(listOf("BigInt"), names("model User {\n id Long @db.<caret>\n}"))
        val sqlite = "datasource db { provider = \"sqlite\" url = env(\"URL\") }\nmodel User {\n id Int @id\n"
        assertFalse(names(sqlite + " name String @<caret>\n}").contains("db"))
        assertFalse(names(sqlite + " @@<caret>\n}").contains("fulltext"))
        assertFalse(names(sqlite + " amount <caret>\n}").contains("Decimal"))
    }
    fun testSQLiteAutoincrementRequiresAnId() {
        val sqlite = "datasource db { provider = \"sqlite\" url = env(\"URL\") }\nmodel User {\n"
        assertFalse(names(sqlite + " count Int @default(<caret>)\n}").contains("autoincrement"))
        assertTrue(names(sqlite + " id Int @id @default(<caret>)\n}").contains("autoincrement"))
    }
    fun testTypeRankingUsesNamesAndForeignKeyType() {
        assertEquals("String", suggestions("model User {\n email <caret>\n}").items.first().lookup)
        assertEquals("Boolean", suggestions("model User {\n isActive <caret>\n}").items.first().lookup)
        assertEquals("DateTime", suggestions("model User {\n createdAt <caret>\n}").items.first().lookup)
        assertEquals("Role", suggestions("enum Role { USER }\nmodel User {\n role <caret>\n}").items.first().lookup)
        assertEquals("Uuid", suggestions("model Author { id Uuid @id }\nmodel Post {\n authorId <caret>\n}").items.first().lookup)
    }
    fun testBlockAndFieldTemplatesAvoidDuplicates() {
        assertTrue(suggestions("<caret>").items.first { it.lookup == "datasource" }.insert.contains("env(\"DATABASE_URL\")"))
        val top = names("datasource db { provider = \"postgresql\" url = env(\"URL\") }\ngenerator client { provider = \"volan-kotlin\" }\n<caret>")
        assertEquals(listOf("model", "enum"), top)
        assertEquals(listOf("id", "createdAt", "updatedAt"), names("model User {\n <caret>\n}"))
        assertFalse(names("model User {\n id Int @id\n createdAt DateTime\n <caret>\n}").contains("id"))
        assertFalse(names("model User {\n id Int @id\n createdAt DateTime\n <caret>\n}").contains("createdAt"))
    }
    fun testAutomaticPopupSkipsCommentsAndStrings() {
        fun triggers(line: String) = VolanCompletionPopup.shouldAutoPopup(line, line.length, line.last())
        assertTrue(triggers("  email "))
        assertTrue(triggers("  provider = "))
        assertTrue(triggers("  title String @db."))
        assertTrue(triggers("  author User @relation(fields: ["))
        assertFalse(triggers("  // title String @"))
        assertFalse(triggers("  title String @map(\"contains@"))
        assertFalse(triggers("  "))
    }
}
