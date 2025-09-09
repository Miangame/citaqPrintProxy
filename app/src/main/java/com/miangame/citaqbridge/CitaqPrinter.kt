package com.miangame.citaqbridge

import android.util.Log
import java.io.File
import java.io.FileOutputStream

class CitaqPrinter : PrinterAdapter {
    private var fos: FileOutputStream? = null

    private fun ensureOpen(): Boolean {
        if (fos != null) return true
        return try {
            val dev = File("/dev/ttyS1") // Puerto serie interno típico del H10-3
            fos = FileOutputStream(dev, /* append = */ false)
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
            false
        }
    }
}
