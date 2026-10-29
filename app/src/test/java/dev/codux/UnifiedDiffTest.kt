package dev.codux

import dev.codux.data.UnifiedDiff
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedDiffTest {
    @Test fun unchangedFileHasNoChanges() {
        assertEquals("Sem alterações.", UnifiedDiff.create("a\nb", "a\nb"))
    }

    @Test fun showsAddedAndRemovedLines() {
        val diff = UnifiedDiff.create("a\nold\nc", "a\nnew\nc")
        assertTrue(diff.contains("-old"))
        assertTrue(diff.contains("+new"))
        assertTrue(diff.contains(" a"))
    }

    @Test fun insertionAtBeginningUsesZeroOldLineStart() {
        val diff = UnifiedDiff.create("first\nsecond", "new\nfirst\nsecond", context = 0)
        assertTrue(diff.contains("@@ -0,0 +1,1 @@"))
    }

    @Test fun deletionAtEndUsesCorrectLineCounts() {
        val diff = UnifiedDiff.create("first\nsecond", "first")
        assertTrue(diff.contains("-second"))
        assertTrue(diff.contains("@@ -1,2 +1,1 @@"))
    }

    @Test fun insertionIntoEmptyFileHasZeroOldLines() {
        val diff = UnifiedDiff.create("", "hello")
        assertTrue(diff.contains("@@ -0,0 +1,1 @@"))
        assertTrue(diff.contains("+hello"))
    }

    @Test fun trailingNewlineDoesNotCountAsAnExtraLine() {
        val diff = UnifiedDiff.create("first\n", "first\nsecond\n")
        assertTrue(diff.contains("@@ -1,1 +1,2 @@"))
        assertTrue(diff.contains("+second"))
    }

    @Test fun finalNewlineChangeIsShownExplicitly() {
        val diff = UnifiedDiff.create("first\n", "first")
        assertTrue(diff.contains("\\ No newline at end of file"))
    }

    @Test fun veryLargeFilesUseTheBoundedFallback() {
        val before = "old\n".repeat(100_001)
        val after = "new\n".repeat(100_001)
        val diff = UnifiedDiff.create(before, after)
        assertTrue(diff.contains("@@ -1,100001 +1,100001 @@"))
        assertTrue(diff.contains("-old"))
        assertTrue(diff.contains("+new"))
    }
}
