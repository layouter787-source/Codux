package dev.codux

import dev.codux.data.ProjectItemName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectItemNameTest {
    @Test fun acceptsSimpleAndUnicodeFileNames() {
        assertEquals(Unit, ProjectItemName.validate("README.md"))
        assertEquals(Unit, ProjectItemName.validate("animação.kt"))
    }

    @Test fun rejectsBlankTraversalSeparatorsAndLineBreaks() {
        listOf("", "   ", ".", "..", "../outside", "folder/file", "folder\\file", "one\ntwo", "one\rtwo").forEach { name ->
            assertTrue("accepted unsafe file name: $name", runCatching { ProjectItemName.validate(name) }.isFailure)
        }
    }
}
