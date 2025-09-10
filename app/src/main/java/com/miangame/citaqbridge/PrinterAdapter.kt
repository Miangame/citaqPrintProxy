package com.miangame.citaqbridge

/**
 * Interfaz simple para adaptadores de impresora.
 * Permite intercambiar implementación directa (/dev/ttyS1) o SDK de Citaq.
 */
interface PrinterAdapter {
    fun printRaw(data: ByteArray): Boolean
    fun openDrawer(): Boolean
}
