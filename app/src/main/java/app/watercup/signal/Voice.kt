package app.watercup.signal

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.util.Log

/** Pleos TextToSpeech SDK(온디바이스 모드)로 짧은 음성 안내를 낸다. */
class Voice(context: Context) {
    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null

    @Volatile var ready = false
        private set

    @Volatile var error: String? = null
        private set

    fun start() {
        if (tts != null) return
        try {
            val engine = TextToSpeech(appContext, Mode.ON_DEVICE)
            engine.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    ready = true
                }

                override fun onFailure(errorType: ErrorType) {
                    error = errorType.toString()
                }
            })
            tts = engine
        } catch (e: Throwable) {
            error = e.message ?: e.javaClass.simpleName
            Log.w("WaterCup", "tts init failed", e)
        }
    }

    fun say(text: String) {
        if (!ready) return
        try {
            tts?.speak(text, true)
        } catch (e: Throwable) {
            error = e.message ?: e.javaClass.simpleName
        }
    }

    fun stop() {
        try {
            tts?.release()
        } catch (e: Throwable) {
            Log.w("WaterCup", "tts release failed", e)
        }
        tts = null
        ready = false
    }
}
