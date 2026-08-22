package com.bejani.vocalassistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private val contactPicker = 1001
    private val permissionRequest = 1002
    private lateinit var selectedContact: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Vocal Assistant"

        selectedContact = TextView(this).apply {
            text = "Kontakt: seçilməyib"
            textSize = 18f
            setPadding(0, 24, 0, 24)
        }

        val explanation = TextView(this).apply {
            text = "Wake word: Salam Yoldaş\nSəsli zəng əmrindən sonra təhlükəsizlik üçün təsdiq istəyəcək.\n\nQeyd: Səs tanıma telefonun SpeechRecognizer xidmətindən istifadə edir və proqramda offline model yoxdur."
            textSize = 16f
            setPadding(0, 16, 0, 24)
        }

        val choose = Button(this).apply {
            text = "Kontakt seç"
            setOnClickListener {
                startActivityForResult(
                    Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI),
                    contactPicker
                )
            }
        }

        val start = Button(this).apply {
            text = "Səsli köməkçini başlat"
            setOnClickListener { startAssistant() }
        }

        val stop = Button(this).apply {
            text = "Köməkçini dayandır"
            setOnClickListener { stopService(Intent(this@MainActivity, VoiceAssistantService::class.java)) }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 32, 40, 32)
            addView(explanation)
            addView(selectedContact)
            addView(choose)
            addView(start)
            addView(stop)
        }
        setContentView(root)
        refreshContactLabel()
        requestPermissionsIfNeeded()
    }

    private fun requestPermissionsIfNeeded() {
        val required = arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE)
        val missing = required.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) ActivityCompat.requestPermissions(this, missing.toTypedArray(), permissionRequest)
    }

    private fun startAssistant() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionsIfNeeded()
            return
        }
        ContextCompat.startForegroundService(this, Intent(this, VoiceAssistantService::class.java))
        Toast.makeText(this, "Köməkçi aktivdir: Salam Yoldaş", Toast.LENGTH_SHORT).show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != contactPicker || resultCode != RESULT_OK || data?.data == null) return
        val uri: Uri = data.data!!
        contentResolver.query(
            uri,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
            null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val name = cursor.getString(0) ?: "مخاطب"
                val phone = cursor.getString(1) ?: return
                getSharedPreferences("assistant", MODE_PRIVATE).edit()
                    .putString("name", name).putString("phone", phone).apply()
                refreshContactLabel()
            }
        }
    }

    private fun refreshContactLabel() {
        val prefs = getSharedPreferences("assistant", MODE_PRIVATE)
        val name = prefs.getString("name", null)
        selectedContact.text = if (name == null) "Kontakt: seçilməyib" else "Kontakt: $name"
    }
}
