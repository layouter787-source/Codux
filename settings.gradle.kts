pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }
rootProject.name = "Codux"
include(":app", ":termux-terminal-emulator", ":termux-terminal-view")
project(":termux-terminal-emulator").projectDir = file("third_party/termux-terminal/terminal-emulator")
project(":termux-terminal-view").projectDir = file("third_party/termux-terminal/terminal-view")
