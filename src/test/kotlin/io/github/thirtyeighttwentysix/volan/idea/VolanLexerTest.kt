package io.github.thirtyeighttwentysix.volan.idea

import junit.framework.TestCase

class VolanLexerTest : TestCase() {
    fun testIncrementalRestartAtEveryTokenBoundary() {
        val text = "/// documentation\r\nmodel model {\n x String @default(\"a\\\"b\") // note\n @@index([x])\n number Int @default(-10)\n}\n"
        val lexer = VolanLexer(); lexer.start(text)
        while (lexer.tokenType != null) {
            val restarted = VolanLexer()
            restarted.start(text, lexer.tokenStart, text.length, lexer.state)
            assertSame(lexer.tokenType, restarted.tokenType)
            assertEquals(lexer.tokenEnd, restarted.tokenEnd)
            assertTrue(lexer.tokenEnd > lexer.tokenStart)
            lexer.advance()
        }
    }
    fun testUnterminatedStringDoesNotSwallowFollowingLines() {
        val tokens = schemaTokens("model User {\n name String @map(\"broken\n id Int @id\n}")
        assertTrue(tokens.any { it.text == "id" })
        assertEquals(1, tokens.count { it.type == VolanTokens.STRING })
    }
}
