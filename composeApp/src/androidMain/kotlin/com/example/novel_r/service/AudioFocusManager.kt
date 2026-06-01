package com.example.novel_r.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build

/**
 * 音訊焦點管理器 - 處理音訊焦點變化
 */
class AudioFocusManager(
    private val context: Context,
    private val onAudioFocusChange: (FocusChange) -> Unit
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false

    enum class FocusChange {
        GAIN,           // 取得焦點
        LOSS,           // 永久失去焦點
        LOSS_TRANSIENT, // 暫時失去焦點
        LOSS_DUCK       // 需要降低音量
    }

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasAudioFocus = true
                onAudioFocusChange(FocusChange.GAIN)
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                hasAudioFocus = false
                onAudioFocusChange(FocusChange.LOSS)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                hasAudioFocus = false
                onAudioFocusChange(FocusChange.LOSS_TRANSIENT)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                onAudioFocusChange(FocusChange.LOSS_DUCK)
            }
        }
    }

    /**
     * 請求音訊焦點
     */
    fun requestAudioFocus(): Boolean {
        if (hasAudioFocus) return true

        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .setAcceptsDelayedFocusGain(true)
                .build()

            audioManager.requestAudioFocus(audioFocusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }

        hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return hasAudioFocus
    }

    /**
     * 放棄音訊焦點
     */
    fun abandonAudioFocus() {
        if (!hasAudioFocus) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let {
                audioManager.abandonAudioFocusRequest(it)
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusChangeListener)
        }

        hasAudioFocus = false
    }
}
