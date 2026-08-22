package com.bejani.vocalassistant

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
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
    private var restarting = false
    private var tts: TextToSpeech? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) tts?.language = Locale("fa", "IR")
        }
        startForeground(10, notification("در حال انتظار برای «سلام یولداش»"))
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
                        ?.joinToString(" ")?.lowercase(Locale("fa")) ?: ""
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
                        ?.joinToString(" ")?.lowercase(Locale("fa")) ?: ""
                    if (text.contains("سلام یولداش") || text.contains("سلام یولداش".replace(" ", ""))) {
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

    private fun handleText(text: String) {
        if (text.contains("سلام یولداش") || text.contains("سلام یولداش".replace(" ", ""))) {
            confirmationMode = false
            val prompt = "فرمان را بگویید. برای تماس بگویید تماس بگیر."
            updateNotification(prompt)
            speak(prompt)
            return
        }
        if (!confirmationMode && (text.contains("تماس") || text.contains("زنگ"))) {
            val name = getSharedPreferences("assistant", MODE_PRIVATE).getString("name", "مخاطب")
            confirmationMode = true
            val prompt = "برای تماس با $name بگویید تأیید می‌کنم."
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
            updateNotification(prompt)
            speak(prompt)
        }
    }

    private fun isConfirmation(text: String): Boolean = listOf("تایید", "تأیید", "بله", "انجام بده", "تایید می کنم", "تأیید می‌کنم").any(text::contains)
    private fun isRejection(text: String): Boolean = listOf("لغو", "نه", "خیر", "انصراف").any(text::contains)

    private fun placeCall() {
        val phone = getSharedPreferences("assistant", MODE_PRIVATE).getString("phone", null)
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
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "assistant_prompt")
    }

    private fun scheduleRestart() {
        if (restarting) return
        restarting = true
        android.os.Handler(mainLooper).postDelayed({
            restarting = false
            if (!isDestroyed) startListening()
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
        recognizer?.destroy()
        recognizer = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
