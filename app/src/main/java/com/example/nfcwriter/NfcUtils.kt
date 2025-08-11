package com.example.nfcwriter

import android.net.Uri
import android.nfc.Ndef
import android.nfc.NdefFormatable
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.util.Log
import java.io.IOException

object NfcUtils {
    fun createUriMessage(link: String): NdefMessage {
        val uri = try { Uri.parse(link.trim()) } catch (_: Throwable) { Uri.EMPTY }
        val record = NdefRecord.createUri(uri)
        return NdefMessage(arrayOf(record))
    }

    fun writeMessageToTag(tag: Tag, message: NdefMessage): Result<Unit> {
        return try {
            val ndef = Ndef.get(tag)
            if (ndef != null) {
                ndef.connect()
                if (!ndef.isWritable) {
                    ndef.close()
                    return Result.failure(IllegalStateException("Tag is read-only"))
                }
                val bytes = message.toByteArray()
                if (bytes.size > ndef.maxSize) {
                    val msg = "Message too large: ${bytes.size}B > ${ndef.maxSize}B"
                    ndef.close()
                    return Result.failure(IllegalStateException(msg))
                }
                ndef.writeNdefMessage(message)
                ndef.close()
                Result.success(Unit)
            } else {
                val formatable = NdefFormatable.get(tag)
                if (formatable != null) {
                    try {
                        formatable.connect()
                        formatable.format(message)
                        formatable.close()
                        Result.success(Unit)
                    } catch (e: IOException) {
                        Result.failure(e)
                    }
                } else {
                    Result.failure(UnsupportedOperationException("Tag does not support NDEF"))
                }
            }
        } catch (e: Exception) {
            Log.e("NFC", "Write failed", e)
            Result.failure(e)
        }
    }
}
