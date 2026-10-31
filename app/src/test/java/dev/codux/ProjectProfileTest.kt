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
                "app/src/main/java/com/draw/DrawView.kt",
                "app/src/main/cpp/Brush.cpp",
                "app/src/main/cpp/CMakeLists.txt"
            )
        )
        assertEquals(listOf("Android"), profile.platforms)
        assertEquals(listOf("Kotlin", "JNI", "NDK", "C++"), profile.stacks)
    }

    @Test
    fun detectsReactWebProject() {
        val profile = ProjectProfile.detect(listOf("package.json", "src/App.tsx", "src/index.css", "public/index.html"))
        assertEquals(listOf("Web"), profile.platforms)
        assertTrue("React" in profile.stacks)
        assertTrue("TypeScript" in profile.stacks)
    }

    @Test
    fun detectsPcProject() {
        val profile = ProjectProfile.detect(listOf("Cargo.toml", "src/main.rs"))
        assertEquals(listOf("PC"), profile.platforms)
        assertEquals(listOf("Rust"), profile.stacks)
    }

    @Test
    fun emptyProjectHasNoProfile() {
        assertEquals(ProjectProfile.EMPTY, ProjectProfile.detect(emptyList()))
    }
}
