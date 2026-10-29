package dev.codux.data

import java.io.IOException

internal object ProjectItemName {
    fun validate(name: String) {
        if (name.isBlank() || name == "." || name == ".." || name.contains('/') || name.contains('\\') || name.contains('\n') || name.contains('\r')) {
            throw IOException("Indica um nome válido sem separadores de pastas.")
        }
    }
}
