package com.bejani.vocalassistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.media.AudioAttributes
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
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
    private var testSpeaker: TextToSpeech? = null
    private var testSpeakerReady = false
    private var testSpeechPending = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Vocal Assistant"

        selectedContact = TextView(this).apply {
            text = "مخاطب: انتخاب نشده"
            textSize = 18f
            setPadding(0, 24, 0, 24)
        }

        val explanation = TextView(this).apply {
            text = "عبارت بیدارباش: سلام یولداش\nبعد از فرمان تماس، برای ایمنی تأیید صوتی درخواست می‌شود.\n\nتوجه: تشخیص گفتار از SpeechRecognizer گوشی استفاده می‌کند و مدل آفلاین داخل برنامه ندارد."
            textSize = 16f
            setPadding(0, 16, 0, 24)
        }

        val choose = Button(this).apply {
            text = "انتخاب مخاطب"
            setOnClickListener {
                startActivityForResult(
                    Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI),
                    contactPicker
                )
            }
        }

        val start = Button(this).apply {
            text = "شروع دستیار صوتی"
            setOnClickListener { startAssistant() }
        }

        val stop = Button(this).apply {
            text = "توقف دستیار"
            setOnClickListener { stopService(Intent(this@MainActivity, VoiceAssistantService::class.java)) }
        }

        val ttsSettings = Button(this).apply {
            text = "تنظیم صدای فارسی"
            setOnClickListener { startActivity(Intent("com.android.settings.TTS_SETTINGS")) }
        }

        val testTts = Button(this).apply {
            text = "آزمایش صدا"
            setOnClickListener {
                Toast.makeText(this@MainActivity, "در حال آزمایش موتور صدا…", Toast.LENGTH_SHORT).show()
                Log.d("VocalAssistantTTS", "Test button clicked; ready=$testSpeakerReady")
                if (testSpeakerReady) {
                    val result = testSpeaker?.speak("این یک صدای آزمایشی از دستیار صوتی است", TextToSpeech.QUEUE_FLUSH, null, "main_test")
                    Log.d("VocalAssistantTTS", "speak() result=$result")
                    if (result == TextToSpeech.ERROR) Toast.makeText(this@MainActivity, "موتور صدا نتوانست پخش کند", Toast.LENGTH_LONG).show()
                } else {
                    testSpeechPending = true
                    Toast.makeText(this@MainActivity, "در حال آماده‌سازی موتور صدا…", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 32, 40, 32)
            addView(explanation)
            addView(selectedContact)
            addView(choose)
            addView(start)
            addView(stop)
            addView(ttsSettings)
            addView(testTts)
        }
        setContentView(root)
        testSpeaker = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val persianVoice = testSpeaker?.voices?.firstOrNull {
                    it.locale.language.equals("fa", ignoreCase = true) ||
                        it.name.contains("fa", ignoreCase = true) ||
                        it.name.contains("persian", ignoreCase = true)
                }
                val language = if (persianVoice != null) {
                    testSpeaker?.voice = persianVoice
                    0
                } else {
                    testSpeaker?.setLanguage(Locale("fa", "IR")) ?: TextToSpeech.LANG_NOT_SUPPORTED
                }
                Log.d("VocalAssistantTTS", "voices=${testSpeaker?.voices?.size}; selectedVoice=${persianVoice?.name}")
                testSpeaker?.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                testSpeaker?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) { Log.d("VocalAssistantTTS", "utterance started: $utteranceId") }
                    override fun onDone(utteranceId: String?) { Log.d("VocalAssistantTTS", "utterance done: $utteranceId") }
                    override fun onError(utteranceId: String?) { Log.e("VocalAssistantTTS", "utterance error: $utteranceId") }
                })
                // Some Android TTS engines report LANG_NOT_SUPPORTED for fa-IR
                // even though a Persian voice is selected and speak() works.
                testSpeakerReady = true
                Log.d("VocalAssistantTTS", "initialized; faIR=$language")
                if (testSpeechPending) {
                    testSpeechPending = false
                    val result = testSpeaker?.speak("این یک صدای آزمایشی از دستیار صوتی است", TextToSpeech.QUEUE_FLUSH, null, "main_test")
                    if (result == TextToSpeech.ERROR) Toast.makeText(this@MainActivity, "موتور صدا نتوانست پخش کند", Toast.LENGTH_LONG).show()
                }
            }
        }
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
        Toast.makeText(this, "دستیار فعال شد: سلام یولداش", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        testSpeaker?.stop()
        testSpeaker?.shutdown()
        testSpeaker = null
        super.onDestroy()
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
        selectedContact.text = if (name == null) "مخاطب: انتخاب نشده" else "مخاطب: $name"
    }
}
