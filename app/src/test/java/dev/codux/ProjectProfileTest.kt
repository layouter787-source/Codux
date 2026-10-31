package dev.codux

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectProfileTest {
    @Test
    fun detectsAndroidProjectWithNativeCode() {
        val profile = ProjectProfile.detect(
            listOf(
                "build.gradle.kts",
                "app/src/main/AndroidManifest.xml",
                "app/src/main/java/com/draw/MainActivity.java",
                "app/src/main/cpp/Brush.cpp",
                "app/src/main/cpp/CMakeLists.txt"
            )
        )
        assertEquals(listOf("Android"), profile.platforms)
        assertTrue("Java" in profile.stacks)
        assertTrue("C++" in profile.stacks)
    }

    @Test
    fun detectsReactWebProject() {
        val profile = ProjectProfile.detect(listOf("package.json", "src/App.tsx", "src/index.css", "public/index.html"))
        assertEquals(listOf("Web"), profile.platforms)
        assertTrue("React" in profile.stacks)
        assertTrue("TypeScript" in profile.stacks)
    }

    @Test
    fun detectsDesktopProject() {
        val profile = ProjectProfile.detect(listOf("Cargo.toml", "src/main.rs"))
        assertEquals(listOf("Desktop"), profile.platforms)
        assertEquals(listOf("Rust"), profile.stacks)
    }

    @Test
    fun emptyProjectHasNoProfile() {
        assertEquals(ProjectProfile.EMPTY, ProjectProfile.detect(emptyList()))
    }
}
