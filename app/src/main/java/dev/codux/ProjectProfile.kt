package dev.codux

/** Plataformas e stacks de um projeto, deduzidos dos nomes dos ficheiros (sem ler o conteúdo). */
data class ProjectProfile(val platforms: List<String>, val stacks: List<String>) {
    companion object {
        val EMPTY = ProjectProfile(emptyList(), emptyList())

        /** [paths] são caminhos relativos à raiz do projeto, com "/" como separador. */
        fun detect(paths: Collection<String>): ProjectProfile {
            val normalized = paths.map { it.replace('\\', '/').lowercase() }
            val names = normalized.map { it.substringAfterLast('/') }.toSet()
            val extensions = names.map { it.substringAfterLast('.', "") }.filter { it.isNotEmpty() }.toSet()
            fun hasName(vararg candidates: String) = candidates.any { it in names }
            fun hasExt(vararg candidates: String) = candidates.any { it in extensions }

            val android = hasName("androidmanifest.xml") ||
                (hasName("build.gradle", "build.gradle.kts") && normalized.any { it.contains("/src/main/") || it.startsWith("app/") })
            val web = hasName("package.json", "index.html", "vite.config.js", "vite.config.ts", "next.config.js") ||
                hasExt("html", "jsx", "tsx", "vue", "svelte")
            val pc = hasName("cargo.toml", "go.mod", "tauri.conf.json") || hasExt("sln", "csproj") ||
                (!android && hasName("cmakelists.txt", "makefile") && hasExt("c", "cpp", "cc", "cxx", "h", "hpp"))
            val nativeCode = hasExt("cpp", "cc", "cxx", "hpp", "c")

            val platforms = buildList {
                if (android) add("Android")
                if (web) add("Web")
                if (pc) add("PC")
            }
            val stacks = buildList {
                if (hasExt("kt", "kts")) add("Kotlin")
                if (hasExt("java")) add("Java")
                // Código nativo dentro de um projeto Android: JNI + NDK (como no desenho: Kotlin+JNI+NDK+C++).
                if (android && nativeCode) {
                    add("JNI")
                    add("NDK")
                }
                if (hasExt("jsx", "tsx")) add("React")
                if (hasExt("ts", "tsx")) add("TypeScript")
                else if (hasExt("js", "mjs") || hasName("package.json")) add("JavaScript")
                if (hasExt("cpp", "cc", "cxx", "hpp")) add("C++")
                else if (hasExt("c")) add("C")
                if (hasExt("py")) add("Python")
                if (hasExt("rs") || hasName("cargo.toml")) add("Rust")
                if (hasExt("go") || hasName("go.mod")) add("Go")
                if (hasExt("html", "css")) add("HTML/CSS")
                if (hasExt("cs")) add("C#")
            }.take(6)
            return ProjectProfile(platforms, stacks)
        }
    }
}
