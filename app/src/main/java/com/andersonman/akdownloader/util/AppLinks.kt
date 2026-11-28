package com.andersonman.akdownloader.util

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Lien reçu depuis une autre application (bouton "Partager" d'Android).
 * Observé par Compose : dès qu'il est défini, le navigateur s'ouvre dessus.
 */
object AppLinks {
    var pendingUrl: String? by mutableStateOf(null)
}
