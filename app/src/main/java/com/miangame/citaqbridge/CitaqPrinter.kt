package com.miangame.citaqbridge

import android.util.Log
import java.io.File
import java.io.FileOutputStream


class CitaqPrinter : PrinterAdapter {
    private var fos: FileOutputStream? = null


    private fun ensureOpen(): Boolean {
        if (fos != null) return true
        return try {
            val dev = File("/dev/ttyS1")
            fos = FileOutputStream(dev, false)
            true
        } catch (e: Exception) {
            Log.e("CITAQ", "No puedo abrir /dev/ttyS1: ${e.message}", e)
            fos = null
            false
        }
    }


    override fun printRaw(data: ByteArray): Boolean {
        if (!ensureOpen()) return false
        return try {
            fos!!.write(data)
            fos!!.flush()
            true
        } catch (e: Exception) {
            Log.e("CITAQ", "Error enviando a impresora: ${e.message}", e)
            false
        }
    }


    override fun openDrawer(): Boolean {
        if (!ensureOpen()) return false
        return try {
// ESC p m t1 t2 → m=0 canal 1; t1=50ms; t2=250ms (ajustables)
            val kick = byteArrayOf(0x1B, 0x70, 0x00, 0x32, 0xFA.toByte())
            fos!!.write(kick)
            fos!!.flush()
            true
        } catch (e: Exception) {
            Log.e("CITAQ", "Error abriendo cajón: ${e.message}", e)
            false
        }
    }
}