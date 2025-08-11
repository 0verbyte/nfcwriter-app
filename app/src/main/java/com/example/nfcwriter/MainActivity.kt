package com.example.nfcwriter

import android.content.Intent
import android.net.Uri
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.Charset
import android.widget.Button
import android.widget.TextView

class MainActivity : AppCompatActivity() {
    private var nfcAdapter: NfcAdapter? = null

    private val links = mutableListOf<String>()
    private var currentIndex = 0

    private lateinit var btnPickCsv: Button
    private lateinit var btnReset: Button
    private lateinit var status: TextView
    private lateinit var currentLink: TextView

    private val pickCsvLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            loadCsv(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnPickCsv = findViewById(R.id.btnPickCsv)
        btnReset = findViewById(R.id.btnReset)
        status = findViewById(R.id.status)
        currentLink = findViewById(R.id.currentLink)

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            Toast.makeText(this, "NFC не поддерживается на этом устройстве", Toast.LENGTH_LONG).show()
            finish(); return
        }

        btnPickCsv.setOnClickListener {
            pickCsvLauncher.launch(arrayOf("text/*", "text/csv", "application/vnd.ms-excel"))
        }
        btnReset.setOnClickListener {
            currentIndex = 0; updateStatus()
        }

        updateStatus()
    }

    private fun loadCsv(uri: Uri) {
        try {
            links.clear()
            contentResolver.openInputStream(uri)?.use { input ->
                BufferedReader(InputStreamReader(input, Charset.forName("UTF-8"))).use { reader ->
                    reader.lineSequence()
                        .map { it.trim().trim('"') }
                        .filter { it.isNotEmpty() }
                        .forEach { line ->
                            val items = if ("," in line && !line.startsWith("http")) line.split(",") else listOf(line)
                            items.map { it.trim() }.filter { it.isNotEmpty() }.forEach { links.add(it) }
                        }
                }
            }
            currentIndex = 0
            Toast.makeText(this, "Загружено ссылок: ${links.size}", Toast.LENGTH_SHORT).show()
            updateStatus()
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка чтения CSV: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() { super.onResume(); enableForegroundDispatch() }
    override fun onPause() { super.onPause(); disableForegroundDispatch() }

    private fun enableForegroundDispatch() {
        val adapter = nfcAdapter ?: return
        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT or (
            if (android.os.Build.VERSION.SDK_INT >= 31) android.app.PendingIntent.FLAG_MUTABLE else 0
        )
        val pendingIntent = android.app.PendingIntent.getActivity(this, 0, intent, flags)
        adapter.enableForegroundDispatch(this, pendingIntent, null, null)
    }

    private fun disableForegroundDispatch() { nfcAdapter?.disableForegroundDispatch(this) }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        if (tag != null) handleTag(tag)
    }

    private fun handleTag(tag: Tag) {
        if (links.isEmpty()) { Toast.makeText(this, "Сначала загрузите CSV", Toast.LENGTH_SHORT).show(); return }
        if (currentIndex >= links.size) { Toast.makeText(this, "Все ссылки записаны", Toast.LENGTH_SHORT).show(); return }

        val link = links[currentIndex]
        val message = NfcUtils.createUriMessage(link)
        val result = NfcUtils.writeMessageToTag(tag, message)
        if (result.isSuccess) {
            currentIndex++
            Toast.makeText(this, "Записано: $link", Toast.LENGTH_SHORT).show()
            updateStatus()
        } else {
            Toast.makeText(this, "Ошибка записи: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun updateStatus() {
        val total = links.size
        val left = (total - currentIndex).coerceAtLeast(0)
        status.text = "Загружено: $total\nГотово: $currentIndex\nОсталось: $left\nПоднесите метку к телефону для записи следующей ссылки."
        currentLink.text = if (currentIndex < total) "Следующая: ${links[currentIndex]}" else "Следующая: —"
    }
}
