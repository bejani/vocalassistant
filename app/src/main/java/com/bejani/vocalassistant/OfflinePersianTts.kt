package com.bejani.vocalassistant

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Offline Persian neural TTS backed by sherpa-onnx and a VITS ONNX model. */
class OfflinePersianTts(private val context: Context) {
    companion object {
        private const val TAG = "OfflinePersianTts"
        private const val MODEL_URL = "https://github.com/bejani/vocalassistant/releases/download/tts-model-v1/fas-model.onnx"
        private const val TOKENS_URL = "https://github.com/bejani/vocalassistant/releases/download/tts-model-v1/fas-tokens.txt"
    }

    private val modelDir = File(context.filesDir, "tts/fas")
    private var engine: OfflineTts? = null
    private var track: AudioTrack? = null

    fun isInstalled(): Boolean = File(modelDir, "model.onnx").length() > 1_000_000 &&
        File(modelDir, "tokens.txt").length() > 10

    /** Downloads the model once and initializes the native engine. Call off the main thread. */
    @Synchronized
    fun prepare(onProgress: ((Int) -> Unit)? = null) {
        modelDir.mkdirs()
        if (!isInstalled()) {
            download(MODEL_URL, File(modelDir, "model.onnx"), onProgress, 100)
            download(TOKENS_URL, File(modelDir, "tokens.txt"), null, 0)
        }
        if (engine == null) {
            val vits = OfflineTtsVitsModelConfig(
                model = File(modelDir, "model.onnx").absolutePath,
                lexicon = "",
                tokens = File(modelDir, "tokens.txt").absolutePath,
                dataDir = "",
                noiseScale = 0.667f,
                noiseScaleW = 0.8f,
                lengthScale = 1.0f,
            )
            engine = OfflineTts(
                config = OfflineTtsConfig(
                    model = OfflineTtsModelConfig(
                        vits = vits,
                        numThreads = 2,
                        provider = "cpu",
                    )
                )
            )
            Log.i(TAG, "Offline Persian TTS initialized; sampleRate=${engine!!.sampleRate()}")
        }
    }

    /** Generates and plays speech. Call off the main thread. */
    @Synchronized
    fun speak(text: String) {
        prepare()
        val audio = engine!!.generate(text = text, sid = 0, speed = 1.0f)
        if (audio.samples.isEmpty()) error("TTS generated no audio")
        val sampleRate = audio.sampleRate
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        ).coerceAtLeast(audio.samples.size * 4)
        track?.release()
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        track = AudioTrack(
            attributes,
            format,
            bufferSize,
            AudioTrack.MODE_STATIC,
            AudioTrack.AUDIO_SESSION_ID_GENERATE
        )
        track!!.write(audio.samples, 0, audio.samples.size, AudioTrack.WRITE_BLOCKING)
        track!!.play()
        Log.d(TAG, "Played ${audio.samples.size} samples at $sampleRate Hz")
    }

    @Synchronized
    fun release() {
        track?.release()
        track = null
        engine?.release()
        engine = null
    }

    private fun download(url: String, destination: File, onProgress: ((Int) -> Unit)?, progressWeight: Int) {
        val temporary = File(destination.parentFile, destination.name + ".part")
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            requestMethod = "GET"
        }
        try {
            connection.connect()
            check(connection.responseCode in 200..299) { "Download failed: HTTP ${connection.responseCode}" }
            val total = connection.contentLengthLong
            var received = 0L
            connection.inputStream.use { input ->
                temporary.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } >= 0) {
                        if (read == 0) continue
                        output.write(buffer, 0, read)
                        received += read
                        if (total > 0 && onProgress != null && progressWeight > 0) {
                            onProgress(((received * progressWeight) / total).toInt().coerceIn(0, progressWeight))
                        }
                    }
                }
            }
            check(temporary.length() > 0) { "Downloaded file is empty" }
            check(temporary.renameTo(destination)) { "Could not finalize ${destination.name}" }
        } finally {
            connection.disconnect()
            if (temporary.exists() && !destination.exists()) temporary.delete()
        }
    }
}
