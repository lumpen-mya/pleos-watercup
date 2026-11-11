package app.watercup.signal

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import android.content.Context
import android.util.Log

/** Pleos SpeechToText SDK(온디바이스 모드)로 정차 중 음성 질문을 받는다. */
class VoiceAsk(context: Context, private val onHeard: (String) -> Unit) {
    private val appContext = context.applicationContext
    private var stt: SpeechToText? = null

    @Volatile var ready = false
        private set

    @Volatile var listening = false
        private set

    @Volatile var error: String? = null
        private set

    private val listener = object : ResultListener {
        override fun onUpdated(text: String, isFinal: Boolean) {
            if (!isFinal) return
            finish()
            if (text.isNotBlank()) onHeard(text)
        }

        override fun onUpdatedEpdData(start: Long, end: Long) = Unit
        override fun onStartedRecognition() = Unit
        override fun onEndedRecognition() {
            listening = false
        }

        override fun onError(errorType: ErrorType) {
            error = errorType.toString()
            listening = false
        }

        override fun onReady() = Unit
        override fun onUpdatedRms(rms: Double) = Unit
    }

    fun start() {
        if (stt != null) return
        try {
            val engine = SpeechToText(appContext, Mode.ON_DEVICE)
            engine.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    engine.addListener(listener)
                    ready = true
                }

                override fun onFailure(errorType: ErrorType) {
                    error = errorType.toString()
                }
            })
            stt = engine
        } catch (e: Throwable) {
            error = e.message ?: e.javaClass.simpleName
            Log.w("WaterCup", "stt init failed", e)
        }
    }

    fun ask() {
        if (!ready || listening) return
        try {
            error = null
            listening = true
            stt?.request()
        } catch (e: Throwable) {
            listening = false
            error = e.message ?: e.javaClass.simpleName
        }
    }

    fun cancel() = finish()

    private fun finish() {
        listening = false
        try {
            stt?.stop()
        } catch (e: Throwable) {
            Log.w("WaterCup", "stt stop failed", e)
        }
    }

    fun stop() {
        try {
            stt?.removeListener(listener)
            stt?.release()
        } catch (e: Throwable) {
            Log.w("WaterCup", "stt release failed", e)
        }
        stt = null
        ready = false
        listening = false
    }
}
