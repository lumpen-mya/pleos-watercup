package app.watercup.signal

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.SystemClock
import android.util.Log

/** Pleos TextToSpeech SDK(온디바이스 모드)로 짧은 음성 안내를 낸다. */
class Voice(context: Context) {
    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private val audio = appContext.getSystemService(AudioManager::class.java)
    private var pending: String? = null
    private var pendingAt = 0L

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

    /**
     * 내비 음성 안내가 나오는 중이면 겹치지 않게 미뤘다가 끝난 뒤 말한다.
     * 너무 늦어진 말은 상황이 지났으므로 버린다. 새 말이 오면 미뤄 둔 말을 대신한다.
     */
    fun say(text: String) {
        if (!ready) return
        if (naviSpeaking()) {
            pending = text
            pendingAt = SystemClock.elapsedRealtime()
            return
        }
        pending = null
        speak(text)
    }

    /** 주기적으로 불러 미뤄 둔 말을 처리한다. */
    fun pump() {
        val text = pending ?: return
        if (SystemClock.elapsedRealtime() - pendingAt > MAX_WAIT_MS) {
            pending = null
            return
        }
        if (naviSpeaking()) return
        pending = null
        speak(text)
    }

    private fun naviSpeaking(): Boolean = try {
        audio?.activePlaybackConfigurations?.any {
            it.audioAttributes.usage == AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE
        } == true
    } catch (e: Throwable) {
        false
    }

    private fun speak(text: String) {
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

    private companion object {
        const val MAX_WAIT_MS = 6000L
    }
}
