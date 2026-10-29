package dev.codux.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import java.net.ServerSocket

class TermuxBridge(private val context: Context) {
    data class ServerConnection(val port: Int, val endpoint: String, val token: String)

    fun isInstalled(): Boolean = try {
        context.packageManager.getPackageInfo("com.termux", 0)
        true
    } catch (_: PackageManager.NameNotFoundException) { false }

    fun createConnection(token: String): ServerConnection {
        val port = ServerSocket(0).use { it.localPort }
        return ServerConnection(port, "ws://127.0.0.1:$port", token)
    }

    fun launchAppServer(connection: ServerConnection, workspacePath: String) {
        require(connection.port in 1024..65535)
        require(workspacePath.startsWith("/") && !workspacePath.contains('\n'))
        val home = "${'$'}HOME"
        val shellPid = "${'$'}${'$'}"
        val script = """
            set -eu
            umask 077
            mkdir -p "$home/.codux"
            token=${'$'}(cat)
            printf '%s' "${'$'}token" > "$home/.codux/app-server-token"
            chmod 600 "$home/.codux/app-server-token"
            printf '%s\n' "$shellPid" > "$home/.codux/app-server.pid"
            exec </dev/null
            exec codex app-server --listen "${connection.endpoint}" --ws-auth capability-token --ws-token-file "$home/.codux/app-server-token"
        """.trimIndent()
        val intent = Intent("com.termux.RUN_COMMAND").apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-lc", script))
            putExtra("com.termux.RUN_COMMAND_STDIN", connection.token + "\n")
            putExtra("com.termux.RUN_COMMAND_WORKDIR", workspacePath)
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
            putExtra("com.termux.RUN_COMMAND_COMMAND_LABEL", "Codux Codex App Server")
            putExtra("com.termux.RUN_COMMAND_COMMAND_DESCRIPTION", "Servidor local protegido do Codux.")
        }
        context.startService(intent)
    }

    fun stopAppServer() {
        val home = "${'$'}HOME"
        val script = """
            set -eu
            pid=${'$'}(cat "$home/.codux/app-server.pid" 2>/dev/null || true)
            if [ -n "${'$'}pid" ] && [ -r "/proc/${'$'}pid/cmdline" ]; then
                cmdline=${'$'}(tr '\000' ' ' < "/proc/${'$'}pid/cmdline" 2>/dev/null || true)
                case "${'$'}cmdline" in
                    *"codex app-server"*) kill "${'$'}pid" 2>/dev/null || true ;;
                esac
            fi
            rm -f "$home/.codux/app-server.pid" "$home/.codux/app-server-token"
        """.trimIndent()
        val intent = Intent("com.termux.RUN_COMMAND").apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-lc", script))
            putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
        }
        runCatching { context.startService(intent) }
    }
}
}pid" ] && [ -r "/proc/${'
                cmdline=${'$'}(tr '\000' ' ' < "/proc/$pid/cmdline" 2>/dev/null || true)
                case "$cmdline" in
                    *"codex app-server"*) kill "$pid" 2>/dev/null || true ;;
                esac
            fi
            rm -f "$home/.codux/app-server.pid" "$home/.codux/app-server-token"
        """.trimIndent()
        val intent = Intent("com.termux.RUN_COMMAND").apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-lc", script))
            putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
        }
        runCatching { context.startService(intent) }
    }
}
}pid/cmdline" ]; then
                cmdline=${'$'}(tr '\000' ' ' < "/proc/$pid/cmdline" 2>/dev/null || true)
                case "$cmdline" in
                    *"codex app-server"*) kill "$pid" 2>/dev/null || true ;;
                esac
            fi
            rm -f "$home/.codux/app-server.pid" "$home/.codux/app-server-token"
        """.trimIndent()
        val intent = Intent("com.termux.RUN_COMMAND").apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-lc", script))
            putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
        }
        runCatching { context.startService(intent) }
    }
}
}(tr '\000' ' ' < "/proc/${'
                case "$cmdline" in
                    *"codex app-server"*) kill "$pid" 2>/dev/null || true ;;
                esac
            fi
            rm -f "$home/.codux/app-server.pid" "$home/.codux/app-server-token"
        """.trimIndent()
        val intent = Intent("com.termux.RUN_COMMAND").apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-lc", script))
            putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
        }
        runCatching { context.startService(intent) }
    }
}
}pid" ] && [ -r "/proc/${'
                cmdline=${'$'}(tr '\000' ' ' < "/proc/$pid/cmdline" 2>/dev/null || true)
                case "$cmdline" in
                    *"codex app-server"*) kill "$pid" 2>/dev/null || true ;;
                esac
            fi
            rm -f "$home/.codux/app-server.pid" "$home/.codux/app-server-token"
        """.trimIndent()
        val intent = Intent("com.termux.RUN_COMMAND").apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-lc", script))
            putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
        }
        runCatching { context.startService(intent) }
    }
}
}pid/cmdline" ]; then
                cmdline=${'$'}(tr '\000' ' ' < "/proc/$pid/cmdline" 2>/dev/null || true)
                case "$cmdline" in
                    *"codex app-server"*) kill "$pid" 2>/dev/null || true ;;
                esac
            fi
            rm -f "$home/.codux/app-server.pid" "$home/.codux/app-server-token"
        """.trimIndent()
        val intent = Intent("com.termux.RUN_COMMAND").apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-lc", script))
            putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
        }
        runCatching { context.startService(intent) }
    }
}
}pid/cmdline" 2>/dev/null || true)
                case "$cmdline" in
                    *"codex app-server"*) kill "$pid" 2>/dev/null || true ;;
                esac
            fi
            rm -f "$home/.codux/app-server.pid" "$home/.codux/app-server-token"
        """.trimIndent()
        val intent = Intent("com.termux.RUN_COMMAND").apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-lc", script))
            putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
        }
        runCatching { context.startService(intent) }
    }
}
}pid" ] && [ -r "/proc/${'
                cmdline=${'$'}(tr '\000' ' ' < "/proc/$pid/cmdline" 2>/dev/null || true)
                case "$cmdline" in
                    *"codex app-server"*) kill "$pid" 2>/dev/null || true ;;
                esac
            fi
            rm -f "$home/.codux/app-server.pid" "$home/.codux/app-server-token"
        """.trimIndent()
        val intent = Intent("com.termux.RUN_COMMAND").apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-lc", script))
            putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
        }
        runCatching { context.startService(intent) }
    }
}
}pid/cmdline" ]; then
                cmdline=${'$'}(tr '\000' ' ' < "/proc/$pid/cmdline" 2>/dev/null || true)
                case "$cmdline" in
                    *"codex app-server"*) kill "$pid" 2>/dev/null || true ;;
                esac
            fi
            rm -f "$home/.codux/app-server.pid" "$home/.codux/app-server-token"
        """.trimIndent()
        val intent = Intent("com.termux.RUN_COMMAND").apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-lc", script))
            putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
        }
        runCatching { context.startService(intent) }
    }
}
}cmdline" in
                    *"codex app-server"*) kill "$pid" 2>/dev/null || true ;;
                esac
            fi
            rm -f "$home/.codux/app-server.pid" "$home/.codux/app-server-token"
        """.trimIndent()
        val intent = Intent("com.termux.RUN_COMMAND").apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-lc", script))
            putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
        }
        runCatching { context.startService(intent) }
    }
}
}pid" ] && [ -r "/proc/${'
                cmdline=${'$'}(tr '\000' ' ' < "/proc/$pid/cmdline" 2>/dev/null || true)
                case "$cmdline" in
                    *"codex app-server"*) kill "$pid" 2>/dev/null || true ;;
                esac
            fi
            rm -f "$home/.codux/app-server.pid" "$home/.codux/app-server-token"
        """.trimIndent()
        val intent = Intent("com.termux.RUN_COMMAND").apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-lc", script))
            putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
        }
        runCatching { context.startService(intent) }
    }
}
}pid/cmdline" ]; then
                cmdline=${'$'}(tr '\000' ' ' < "/proc/$pid/cmdline" 2>/dev/null || true)
                case "$cmdline" in
                    *"codex app-server"*) kill "$pid" 2>/dev/null || true ;;
                esac
            fi
            rm -f "$home/.codux/app-server.pid" "$home/.codux/app-server-token"
        """.trimIndent()
        val intent = Intent("com.termux.RUN_COMMAND").apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-lc", script))
            putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
        }
        runCatching { context.startService(intent) }
    }
}
}(tr '\000' ' ' < "/proc/${'
                case "$cmdline" in
                    *"codex app-server"*) kill "$pid" 2>/dev/null || true ;;
                esac
            fi
            rm -f "$home/.codux/app-server.pid" "$home/.codux/app-server-token"
        """.trimIndent()
        val intent = Intent("com.termux.RUN_COMMAND").apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-lc", script))
            putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
        }
        runCatching { context.startService(intent) }
    }
}
}pid" ] && [ -r "/proc/${'
                cmdline=${'$'}(tr '\000' ' ' < "/proc/$pid/cmdline" 2>/dev/null || true)
                case "$cmdline" in
                    *"codex app-server"*) kill "$pid" 2>/dev/null || true ;;
                esac
            fi
            rm -f "$home/.codux/app-server.pid" "$home/.codux/app-server-token"
        """.trimIndent()
        val intent = Intent("com.termux.RUN_COMMAND").apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-lc", script))
            putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
        }
        runCatching { context.startService(intent) }
    }
}
}pid/cmdline" ]; then
                cmdline=${'$'}(tr '\000' ' ' < "/proc/$pid/cmdline" 2>/dev/null || true)
                case "$cmdline" in
                    *"codex app-server"*) kill "$pid" 2>/dev/null || true ;;
                esac
            fi
            rm -f "$home/.codux/app-server.pid" "$home/.codux/app-server-token"
        """.trimIndent()
        val intent = Intent("com.termux.RUN_COMMAND").apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-lc", script))
            putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
        }
        runCatching { context.startService(intent) }
    }
}
}pid/cmdline" 2>/dev/null || true)
                case "$cmdline" in
                    *"codex app-server"*) kill "$pid" 2>/dev/null || true ;;
                esac
            fi
            rm -f "$home/.codux/app-server.pid" "$home/.codux/app-server-token"
        """.trimIndent()
        val intent = Intent("com.termux.RUN_COMMAND").apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-lc", script))
            putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
        }
        runCatching { context.startService(intent) }
    }
}
}pid" ] && [ -r "/proc/${'
                cmdline=${'$'}(tr '\000' ' ' < "/proc/$pid/cmdline" 2>/dev/null || true)
                case "$cmdline" in
                    *"codex app-server"*) kill "$pid" 2>/dev/null || true ;;
                esac
            fi
            rm -f "$home/.codux/app-server.pid" "$home/.codux/app-server-token"
        """.trimIndent()
        val intent = Intent("com.termux.RUN_COMMAND").apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-lc", script))
            putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
        }
        runCatching { context.startService(intent) }
    }
}
}pid/cmdline" ]; then
                cmdline=${'$'}(tr '\000' ' ' < "/proc/$pid/cmdline" 2>/dev/null || true)
                case "$cmdline" in
                    *"codex app-server"*) kill "$pid" 2>/dev/null || true ;;
                esac
            fi
            rm -f "$home/.codux/app-server.pid" "$home/.codux/app-server-token"
        """.trimIndent()
        val intent = Intent("com.termux.RUN_COMMAND").apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-lc", script))
            putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
        }
        runCatching { context.startService(intent) }
    }
}
}pid" 2>/dev/null || true ;;
                esac
            fi
            rm -f "$home/.codux/app-server.pid" "$home/.codux/app-server-token"
        """.trimIndent()
        val intent = Intent("com.termux.RUN_COMMAND").apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-lc", script))
            putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
        }
        runCatching { context.startService(intent) }
    }
}
}pid" ] && [ -r "/proc/${'
                cmdline=${'$'}(tr '\000' ' ' < "/proc/$pid/cmdline" 2>/dev/null || true)
                case "$cmdline" in
                    *"codex app-server"*) kill "$pid" 2>/dev/null || true ;;
                esac
            fi
            rm -f "$home/.codux/app-server.pid" "$home/.codux/app-server-token"
        """.trimIndent()
        val intent = Intent("com.termux.RUN_COMMAND").apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-lc", script))
            putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
        }
        runCatching { context.startService(intent) }
    }
}
}pid/cmdline" ]; then
                cmdline=${'$'}(tr '\000' ' ' < "/proc/$pid/cmdline" 2>/dev/null || true)
                case "$cmdline" in
                    *"codex app-server"*) kill "$pid" 2>/dev/null || true ;;
                esac
            fi
            rm -f "$home/.codux/app-server.pid" "$home/.codux/app-server-token"
        """.trimIndent()
        val intent = Intent("com.termux.RUN_COMMAND").apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-lc", script))
            putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
        }
        runCatching { context.startService(intent) }
    }
}
}(tr '\000' ' ' < "/proc/${'
                case "$cmdline" in
                    *"codex app-server"*) kill "$pid" 2>/dev/null || true ;;
                esac
            fi
            rm -f "$home/.codux/app-server.pid" "$home/.codux/app-server-token"
        """.trimIndent()
        val intent = Intent("com.termux.RUN_COMMAND").apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-lc", script))
            putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
        }
        runCatching { context.startService(intent) }
    }
}
}pid" ] && [ -r "/proc/${'
                cmdline=${'$'}(tr '\000' ' ' < "/proc/$pid/cmdline" 2>/dev/null || true)
                case "$cmdline" in
                    *"codex app-server"*) kill "$pid" 2>/dev/null || true ;;
                esac
            fi
            rm -f "$home/.codux/app-server.pid" "$home/.codux/app-server-token"
        """.trimIndent()
        val intent = Intent("com.termux.RUN_COMMAND").apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-lc", script))
            putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
        }
        runCatching { context.startService(intent) }
    }
}
}pid/cmdline" ]; then
                cmdline=${'$'}(tr '\000' ' ' < "/proc/$pid/cmdline" 2>/dev/null || true)
                case "$cmdline" in
                    *"codex app-server"*) kill "$pid" 2>/dev/null || true ;;
                esac
            fi
            rm -f "$home/.codux/app-server.pid" "$home/.codux/app-server-token"
        """.trimIndent()
        val intent = Intent("com.termux.RUN_COMMAND").apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-lc", script))
            putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
        }
        runCatching { context.startService(intent) }
    }
}
}pid/cmdline" 2>/dev/null || true)
                case "$cmdline" in
                    *"codex app-server"*) kill "$pid" 2>/dev/null || true ;;
                esac
            fi
            rm -f "$home/.codux/app-server.pid" "$home/.codux/app-server-token"
        """.trimIndent()
        val intent = Intent("com.termux.RUN_COMMAND").apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-lc", script))
            putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
        }
        runCatching { context.startService(intent) }
    }
}
}pid" ] && [ -r "/proc/${'
                cmdline=${'$'}(tr '\000' ' ' < "/proc/$pid/cmdline" 2>/dev/null || true)
                case "$cmdline" in
                    *"codex app-server"*) kill "$pid" 2>/dev/null || true ;;
                esac
            fi
            rm -f "$home/.codux/app-server.pid" "$home/.codux/app-server-token"
        """.trimIndent()
        val intent = Intent("com.termux.RUN_COMMAND").apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-lc", script))
            putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
        }
        runCatching { context.startService(intent) }
    }
}
}pid/cmdline" ]; then
                cmdline=${'$'}(tr '\000' ' ' < "/proc/$pid/cmdline" 2>/dev/null || true)
                case "$cmdline" in
                    *"codex app-server"*) kill "$pid" 2>/dev/null || true ;;
                esac
            fi
            rm -f "$home/.codux/app-server.pid" "$home/.codux/app-server-token"
        """.trimIndent()
        val intent = Intent("com.termux.RUN_COMMAND").apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-lc", script))
            putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
        }
        runCatching { context.startService(intent) }
    }
}
}cmdline" in
                    *"codex app-server"*) kill "$pid" 2>/dev/null || true ;;
                esac
            fi
            rm -f "$home/.codux/app-server.pid" "$home/.codux/app-server-token"
        """.trimIndent()
        val intent = Intent("com.termux.RUN_COMMAND").apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-lc", script))
            putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
        }
        runCatching { context.startService(intent) }
    }
}
}pid" ] && [ -r "/proc/${'
                cmdline=${'$'}(tr '\000' ' ' < "/proc/$pid/cmdline" 2>/dev/null || true)
                case "$cmdline" in
                    *"codex app-server"*) kill "$pid" 2>/dev/null || true ;;
                esac
            fi
            rm -f "$home/.codux/app-server.pid" "$home/.codux/app-server-token"
        """.trimIndent()
        val intent = Intent("com.termux.RUN_COMMAND").apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-lc", script))
            putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
        }
        runCatching { context.startService(intent) }
    }
}
}pid/cmdline" ]; then
                cmdline=${'$'}(tr '\000' ' ' < "/proc/$pid/cmdline" 2>/dev/null || true)
                case "$cmdline" in
                    *"codex app-server"*) kill "$pid" 2>/dev/null || true ;;
                esac
            fi
            rm -f "$home/.codux/app-server.pid" "$home/.codux/app-server-token"
        """.trimIndent()
        val intent = Intent("com.termux.RUN_COMMAND").apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-lc", script))
            putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
        }
        runCatching { context.startService(intent) }
    }
}
}(tr '\000' ' ' < "/proc/${'
                case "$cmdline" in
                    *"codex app-server"*) kill "$pid" 2>/dev/null || true ;;
                esac
            fi
            rm -f "$home/.codux/app-server.pid" "$home/.codux/app-server-token"
        """.trimIndent()
        val intent = Intent("com.termux.RUN_COMMAND").apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-lc", script))
            putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
        }
        runCatching { context.startService(intent) }
    }
}
}pid" ] && [ -r "/proc/${'
                cmdline=${'$'}(tr '\000' ' ' < "/proc/$pid/cmdline" 2>/dev/null || true)
                case "$cmdline" in
                    *"codex app-server"*) kill "$pid" 2>/dev/null || true ;;
                esac
            fi
            rm -f "$home/.codux/app-server.pid" "$home/.codux/app-server-token"
        """.trimIndent()
        val intent = Intent("com.termux.RUN_COMMAND").apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-lc", script))
            putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
        }
        runCatching { context.startService(intent) }
    }
}
}pid/cmdline" ]; then
                cmdline=${'$'}(tr '\000' ' ' < "/proc/$pid/cmdline" 2>/dev/null || true)
                case "$cmdline" in
                    *"codex app-server"*) kill "$pid" 2>/dev/null || true ;;
                esac
            fi
            rm -f "$home/.codux/app-server.pid" "$home/.codux/app-server-token"
        """.trimIndent()
        val intent = Intent("com.termux.RUN_COMMAND").apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-lc", script))
            putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
        }
        runCatching { context.startService(intent) }
    }
}
}pid/cmdline" 2>/dev/null || true)
                case "$cmdline" in
                    *"codex app-server"*) kill "$pid" 2>/dev/null || true ;;
                esac
            fi
            rm -f "$home/.codux/app-server.pid" "$home/.codux/app-server-token"
        """.trimIndent()
        val intent = Intent("com.termux.RUN_COMMAND").apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-lc", script))
            putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
        }
        runCatching { context.startService(intent) }
    }
}
}pid" ] && [ -r "/proc/${'
                cmdline=${'$'}(tr '\000' ' ' < "/proc/$pid/cmdline" 2>/dev/null || true)
                case "$cmdline" in
                    *"codex app-server"*) kill "$pid" 2>/dev/null || true ;;
                esac
            fi
            rm -f "$home/.codux/app-server.pid" "$home/.codux/app-server-token"
        """.trimIndent()
        val intent = Intent("com.termux.RUN_COMMAND").apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-lc", script))
            putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
        }
        runCatching { context.startService(intent) }
    }
}
}pid/cmdline" ]; then
                cmdline=${'$'}(tr '\000' ' ' < "/proc/$pid/cmdline" 2>/dev/null || true)
                case "$cmdline" in
                    *"codex app-server"*) kill "$pid" 2>/dev/null || true ;;
                esac
            fi
            rm -f "$home/.codux/app-server.pid" "$home/.codux/app-server-token"
        """.trimIndent()
        val intent = Intent("com.termux.RUN_COMMAND").apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-lc", script))
            putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
        }
        runCatching { context.startService(intent) }
    }
}
