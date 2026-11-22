package com.armanmaurya.internetradio.custom.locale

import java.util.Locale

/**
 * Imposta "it" (Italiano) come lingua predefinita dell'app all'avvio.
 * Deve essere chiamato PRIMA di qualsiasi rendering UI/Compose per evitare flash di lingua sbagliata.
 */
fun setDefaultLocale() {
    Locale.setDefault(Locale("it"))
}
