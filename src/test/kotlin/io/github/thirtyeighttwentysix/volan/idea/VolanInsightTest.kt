package io.github.thirtyeighttwentysix.volan.idea

import com.intellij.codeInsight.documentation.DocumentationManager
import com.intellij.lang.folding.LanguageFolding
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.thirtyeighttwentysix.volan.schema.SchemaFormatter

class VolanInsightTest : BasePlatformTestCase() {
    private val datasource = "datasource db {\n provider = \"postgresql\"\n url = env(\"DATABASE_URL\")\n}\n"
    private fun open(source: String) = myFixture.configureByText("schema.volan", source)
    private fun choose(name: String, char: Char = '\n') {
        myFixture.completeBasic()
        if (myFixture.lookupElementStrings != null) {
            myFixture.lookup.currentItem = myFixture.lookupElements!!.first { it.lookupString == name }
            myFixture.finishLookup(char)
        }
    }

    fun testFileTypeAndLosslessPsi() {
        val source = "// comment\nmodel model {\n model String? @map(\"model\")\n}\n"
        val file = open(source)
        assertTrue(file is VolanFile)
        assertSame(VolanFileType.INSTANCE, file.fileType)
        assertEquals(source, file.text)
    }
    fun testCompletionIntegrationAndInsertion() {
        open(datasource + "model User {\n id Int @id\n email Str<caret>\n}")
        myFixture.completeBasic()
        // A single matching item is inserted automatically by the platform.
        if (myFixture.lookupElementStrings != null) {
            assertTrue(myFixture.lookupElementStrings!!.contains("String"))
            myFixture.lookup.currentItem = myFixture.lookupElements!!.first { it.lookupString == "String" }
            myFixture.finishLookup('\n')
        }
        assertTrue(myFixture.file.text.contains("email String"))
    }
    fun testAttributeCompletionPlacesCaretInsideArguments() {
        open(datasource + "model User {\n id Int @id @def<caret>\n}")
        myFixture.completeBasic()
        if (myFixture.lookupElementStrings != null) {
            myFixture.lookup.currentItem = myFixture.lookupElements!!.first { it.lookupString == "default" }
            myFixture.finishLookup('\n')
        }
        myFixture.checkResult(datasource + "model User {\n id Int @id @default(<caret>)\n}")
    }
    fun testProviderCompletionKeepsExactlyOnePairOfQuotes() {
        open("datasource db {\n provider = \"post<caret>\"\n url = env(\"DATABASE_URL\")\n}")
        myFixture.completeBasic()
        if (myFixture.lookupElementStrings != null) {
            myFixture.lookup.currentItem = myFixture.lookupElements!!.first { it.lookupString == "postgresql" }
            myFixture.finishLookup('\n')
        }
        myFixture.checkResult("datasource db {\n provider = \"postgresql<caret>\"\n url = env(\"DATABASE_URL\")\n}")
    }
    fun testEnumDefaultReferenceNavigation() {
        val file = open(datasource + "enum Role {\n USER\n ADMIN\n}\nmodel User {\n id Int @id\n role Role @default(ADM<caret>IN)\n}")
        val reference = file.findReferenceAt(myFixture.caretOffset)
        assertNotNull(reference)
        assertEquals(file.text.indexOf("ADMIN\n"), reference!!.resolve()!!.textRange.startOffset)
    }
    fun testCompletionReusesExistingDefaultParentheses() {
        open(datasource + "model User {\n id Int @id @def<caret>(autoincrement())\n}")
        choose("default")
        myFixture.checkResult(datasource + "model User {\n id Int @id @default(<caret>autoincrement())\n}")
    }
    fun testFunctionCompletionReusesExistingParentheses() {
        open(datasource + "model User {\n id Int @id\n createdAt DateTime @default(no<caret>())\n}")
        choose("now")
        assertEquals(datasource + "model User {\n id Int @id\n createdAt DateTime @default(now())\n}", myFixture.file.text)
    }
    fun testInferredRelationCompletionInsertsMatchingKeys() {
        open(datasource + "model User {\n id Uuid @id\n posts Post[]\n}\nmodel Post {\n id Int @id\n authorId Uuid\n author User @rel<caret>\n}")
        choose("relation")
        assertTrue(myFixture.file.text.contains("@relation(fields: [authorId], references: [id])"))
        assertFalse(SchemaInsight.read(myFixture.file).diagnostics.any { it.isError })
    }
    fun testNativeTypeCompletionInsertsSizeParentheses() {
        open(datasource + "model User {\n id Int @id\n email String @db.VarC<caret>\n}")
        choose("VarChar")
        myFixture.checkResult(datasource + "model User {\n id Int @id\n email String @db.VarChar(<caret>)\n}")
    }
    fun testModelSnippetRequiresExplicitSelectionAndHasLogo() {
        open("mod<caret>")
        myFixture.completeBasic()
        assertNotNull(myFixture.lookup)
        myFixture.lookup.currentItem = myFixture.lookupElements!!.first { it.lookupString == "model" }
        val presentation = com.intellij.codeInsight.lookup.LookupElementPresentation()
        myFixture.lookup.currentItem!!.renderElement(presentation)
        assertSame(VolanIcons.FILE, presentation.icon)
        myFixture.finishLookup('\n')
        assertTrue(myFixture.file.text.contains("id Int @id @default(autoincrement())"))
    }
    fun testSyntaxAndSemanticDiagnostics() {
        open(datasource + "model User {\n id Int @id\n name Strng\n}")
        assertTrue(myFixture.doHighlighting().any { it.description?.contains("[E0201]") == true && it.description.contains("String") })
        open(datasource + "model User {\n name String\n}")
        assertTrue(myFixture.doHighlighting().any { it.description?.contains("[E0209]") == true })
        open(datasource + "model User {\n name String??\n}")
        assertTrue(myFixture.doHighlighting().any { it.description?.contains("[E0111]") == true })
    }
    fun testWarningsAndValidRelations() {
        open("datasource db { provider = \"postgresql\" url = \"jdbc:postgresql://localhost/db\" }\nmodel User { id Int @id }")
        assertTrue(myFixture.doHighlighting().any { it.description?.contains("[E0234]") == true })
        open(datasource + "model User {\n id Int @id\n posts Post[]\n}\nmodel Post {\n id Int @id\n authorId Int\n author User @relation(fields: [authorId], references: [id])\n}")
        val problems = myFixture.doHighlighting().filter { it.description?.startsWith("[E") == true }
        assertEquals(problems.toString(), 0, problems.size)
    }
    fun testTypeReferenceNavigation() {
        val file = open(datasource + "model User { id Int @id }\nmodel Post {\n id Int @id\n authorId Int\n author Us<caret>er @relation(fields: [authorId], references: [id])\n}")
        val reference = file.findReferenceAt(myFixture.caretOffset)
        assertNotNull(reference)
        assertEquals(file.text.indexOf("User {"), reference!!.resolve()!!.textRange.startOffset)
    }
    fun testRelationReferenceNavigatesToTargetModelField() {
        val file = open(datasource + "model User {\n id Int @id\n posts Post[]\n}\nmodel Post {\n id Int @id\n authorId Int\n author User @relation(fields: [authorId], references: [i<caret>d])\n}")
        val reference = file.findReferenceAt(myFixture.caretOffset)
        assertNotNull(reference)
        assertEquals(file.text.indexOf("id Int @id"), reference!!.resolve()!!.textRange.startOffset)
    }
    fun testDocumentationEscapesUserComments() {
        val file = open(datasource + "/// <script>alert(1)</script>\nmodel Us<caret>er { id Int @id }")
        val leaf = file.findElementAt(myFixture.caretOffset)!!
        val html = VolanDocumentationProvider().generateDoc(leaf.parent, leaf)!!
        assertTrue(html.contains("&lt;script&gt;"))
        assertFalse(html.contains("<script>"))
        assertNotNull(DocumentationManager.getProviderFromElement(leaf))
    }
    fun testCanonicalReformatPreservesCommentsAndIsIdempotent() {
        val source = datasource + "/// Users\nmodel User {\n// key\nid Int @id // primary\n\nname String?\n@@map(\"users\")\n}\n"
        val file = open(source)
        WriteCommandAction.runWriteCommandAction(project) { CodeStyleManager.getInstance(project).reformat(file) }
        val formatted = file.text
        assertEquals(SchemaFormatter.format("schema.volan", source), formatted)
        WriteCommandAction.runWriteCommandAction(project) { CodeStyleManager.getInstance(project).reformat(file) }
        assertEquals(formatted, file.text)
    }
    fun testInvalidSchemaIsNotDestroyedByReformat() {
        val source = "model User {\n id @id\n broken \"unterminated\n"
        val file = open(source)
        WriteCommandAction.runWriteCommandAction(project) { CodeStyleManager.getInstance(project).reformat(file) }
        assertEquals(source, file.text)
    }
    fun testSelectedFragmentDoesNotFormatTheWholeFile() {
        val source = datasource + "model User {\n id Int @id\nname String\n}\n"
        val file = open(source)
        val start = source.indexOf("name String")
        WriteCommandAction.runWriteCommandAction(project) { CodeStyleManager.getInstance(project).reformatText(file, start, start + 11) }
        assertEquals(source, file.text)
    }
    fun testFoldingIgnoresBracesInCommentsAndStrings() {
        val file = open("// {\ndatasource db {\n provider = \"postgresql\"\n url = \"{ }\"\n}\nmodel User {\n id Int @id\n}\n")
        val builder = LanguageFolding.INSTANCE.forLanguage(file.language)
        assertNotNull(builder)
        assertEquals(2, builder!!.buildFoldRegions(file.node, myFixture.editor.document).size)
    }
    fun testCacheInvalidatesAfterEditing() {
        val file = open(datasource + "model User { id Int @id }")
        assertFalse(SchemaInsight.read(file).diagnostics.any { it.isError })
        myFixture.editor.caretModel.moveToOffset(file.text.indexOf("Int @id"))
        myFixture.type("Unknown")
        com.intellij.psi.PsiDocumentManager.getInstance(project).commitAllDocuments()
        assertTrue(SchemaInsight.read(file).diagnostics.any { it.code.id == "E0201" })
    }
}
