package com.bejani.vocalassistant

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.provider.ContactsContract
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.telecom.TelecomManager
import android.net.Uri
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import java.util.Locale

class VoiceAssistantService : Service() {
    private var recognizer: SpeechRecognizer? = null
    private var confirmationMode = false
    private var awaitingCommand = false
    private var restarting = false
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingSpeech: String? = null
    private var serviceActive = true
    private var pendingPhone: String? = null
    private var pendingName: String? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val languageStatus = tts?.setLanguage(Locale("fa", "IR")) ?: TextToSpeech.LANG_NOT_SUPPORTED
                tts?.setSpeechRate(0.9f)
                ttsReady = languageStatus != TextToSpeech.LANG_MISSING_DATA && languageStatus != TextToSpeech.LANG_NOT_SUPPORTED
                if (!ttsReady) {
                    updateNotification("صدای فارسی نصب نیست؛ از تنظیمات تبدیل متن به گفتار نصبش کنید")
                } else {
                    pendingSpeech?.let { queued ->
                        pendingSpeech = null
                        speak(queued)
                    }
                }
            }
        }
        startForeground(10, notification("در انتظار سلام یولداش"))
        startListening()
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            updateNotification("سرویس تشخیص گفتار روی این گوشی در دسترس نیست")
            return
        }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).also { speech ->
            speech.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.joinToString(" ")?.lowercase(Locale("fa", "IR")) ?: ""
                    handleText(text)
                    scheduleRestart()
                }
                override fun onError(error: Int) { scheduleRestart() }
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {
                    val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.joinToString(" ")?.lowercase(Locale("fa", "IR")) ?: ""
                    if (isWakePhrase(text)) {
                        handleText(text)
                    }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        recognizer?.startListening(intent)
    }

    private fun isWakePhrase(text: String): Boolean {
        val normalized = text.lowercase(Locale("fa", "IR"))
            .replace("ي", "ی")
            .replace("ئ", "ی")
            .replace("ك", "ک")
            .replace(Regex("[^a-z0-9آ-ی]"), "")
        return normalized.contains("سلامیولداش") || normalized.contains("salamyoldas")
    }

    private fun handleText(text: String) {
        if (isWakePhrase(text)) {
            confirmationMode = false
            awaitingCommand = true
            val prompt = "بله، در خدمتم. فرمان را بگویید. برای تماس بگویید تماس بگیر."
            updateNotification(prompt)
            speak(prompt)
            return
        }
        if (!confirmationMode && awaitingCommand && (text.contains("تماس") || text.contains("زنگ"))) {
            awaitingCommand = false
            val contact = findContact(text)
            if (contact == null) {
                awaitingCommand = true
                val prompt = "مخاطبی با این نام پیدا نشد. نام را دوباره بگویید."
                updateNotification(prompt)
                speak(prompt)
                return
            }
            pendingName = contact.first
            pendingPhone = contact.second
            awaitingCommand = false
            confirmationMode = true
            val prompt = "برای تماس با ${contact.first} بگویید تأیید می‌کنم."
            updateNotification(prompt)
            speak(prompt)
            return
        }
        if (confirmationMode && isConfirmation(text)) {
            confirmationMode = false
            placeCall()
        } else if (confirmationMode && isRejection(text)) {
            confirmationMode = false
            val prompt = "تماس لغو شد."
            awaitingCommand = true
            updateNotification(prompt)
            speak(prompt)
        } else if (!confirmationMode && awaitingCommand) {
            awaitingCommand = false
            val prompt = "این فرمان را متوجه نشدم. برای تماس بگویید تماس بگیر."
            updateNotification(prompt)
            speak(prompt)
        }
    }

    private fun findContact(command: String): Pair<String, String>? {
        val query = command
            .replace("تماس بگیر", "", ignoreCase = true)
            .replace("تماس بزن", "", ignoreCase = true)
            .replace("زنگ بزن", "", ignoreCase = true)
            .replace("زنگ بگیر", "", ignoreCase = true)
            .replace("زنگ بزن به", "", ignoreCase = true)
            .replace(Regex("^(به|با)\\s+"), "")
            .trim()
        if (query.isBlank()) return null
        val queryNormalized = normalizeName(query)
        var best: Pair<String, String>? = null
        var bestScore = 0
        contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
            null, null, null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(0) ?: continue
                val phone = cursor.getString(1) ?: continue
                val normalizedName = normalizeName(name)
                val score = when {
                    normalizedName == queryNormalized -> 100
                    normalizedName.contains(queryNormalized) || queryNormalized.contains(normalizedName) -> 80
                    else -> normalizeName(query).split(" ").count { token -> token.length > 1 && normalizedName.contains(token) } * 10
                }
                if (score > bestScore) {
                    bestScore = score
                    best = name to phone
                }
            }
        }
        return if (bestScore >= 20) best else null
    }

    private fun normalizeName(value: String): String = value.lowercase(Locale("fa", "IR"))
        .replace("ي", "ی")
        .replace("ك", "ک")
        .replace("ۀ", "ه")
        .replace("ة", "ه")
        .replace("ö", "o")
        .replace("ü", "u")
        .replace("ş", "s")
        .replace("ç", "c")
        .replace("ğ", "g")
        .replace(Regex("[^a-z0-9 ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun isConfirmation(text: String): Boolean = listOf("تایید", "تأیید", "تایید می کنم", "تأیید می‌کنم", "بله", "حتما", "حتماً").any(text::contains)
    private fun isRejection(text: String): Boolean = listOf("لغو", "نه", "خیر", "انصراف").any(text::contains)

    private fun placeCall() {
        val phone = pendingPhone ?: getSharedPreferences("assistant", MODE_PRIVATE).getString("phone", null)
        if (phone.isNullOrBlank()) {
            updateNotification("مخاطب انتخاب نشده است")
            return
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            updateNotification("مجوز تماس را از تنظیمات برنامه فعال کنید")
            return
        }
        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:${Uri.encode(phone)}")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
        updateNotification("تماس برقرار شد")
        speak("تماس برقرار شد")
        pendingPhone = null
        pendingName = null
    }

    private fun speak(text: String) {
        if (!ttsReady) {
            pendingSpeech = text
            return
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "assistant_prompt")
    }

    private fun scheduleRestart() {
        if (restarting) return
        restarting = true
        android.os.Handler(mainLooper).postDelayed({
            restarting = false
            if (serviceActive) startListening()
        }, 450)
    }

    private fun notification(text: String): Notification = NotificationCompat.Builder(this, "voice")
        .setSmallIcon(android.R.drawable.ic_btn_speak_now)
        .setContentTitle("Vocal Assistant")
        .setContentText(text)
        .setOngoing(true)
        .build()

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(10, notification(text))
    }

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("voice", "دستیار صوتی", NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onDestroy() {
        serviceActive = false
        recognizer?.destroy()
        recognizer = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
