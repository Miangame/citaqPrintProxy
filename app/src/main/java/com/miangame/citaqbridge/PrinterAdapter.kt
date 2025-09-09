package com.miangame.citaqbridge

/**
 * Interfaz simple para adaptadores de impresora.
 * Permite intercambiar implementación directa (/dev/ttyS1) o SDK de Citaq.
 */
interface PrinterAdapter {
    /**
     * Envía datos en crudo (normalmente ESC/POS) a la impresora.
     * @param data bytes del ticket.
     * @return true si se envió correctamente, false si hubo error.
     */
    fun printRaw(data: ByteArray): Boolean
}
