package com.arkz.armodelviewer.util

import android.media.MediaActionSound
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.arkz.armodelviewer.model.AR_LOG_TAG

/**
 * Som de "disparo de câmera" tocado na captura de tela.
 *
 * Usa o [MediaActionSound.SHUTTER_CLICK] — o som oficial do sistema, o mesmo dos
 * apps de câmera. Vantagens sobre um arquivo de áudio próprio: é o som que o
 * usuário já conhece, **respeita as configurações do aparelho** (no modo
 * silencioso/vibrar os sons de câmera não tocam, como manda a convenção do
 * Android) e não exige nenhuma permissão.
 *
 * A instância é criada e carregada **uma única vez** e reaproveitada: `load`
 * aloca um recurso no `SoundPool` do sistema e repetir isso a cada captura seria
 * desperdício. O play é postado na thread principal, que é onde o `SoundPool`
 * opera.
 */
object ShutterSound {

    private val handler = Handler(Looper.getMainLooper())
    private var sound: MediaActionSound? = null

    /**
     * Toca o som de disparo. Nunca lança: uma falha de áudio não pode atrapalhar a
     * captura — no máximo fica registrada no Logcat.
     */
    fun play() {
        handler.post {
            runCatching {
                val mediaSound = sound ?: MediaActionSound().apply {
                    load(MediaActionSound.SHUTTER_CLICK)
                    sound = this
                }
                mediaSound.play(MediaActionSound.SHUTTER_CLICK)
            }.onFailure { error ->
                Log.w(
                    AR_LOG_TAG,
                    "Não foi possível tocar o som de captura: " +
                        (error.message ?: error.javaClass.simpleName),
                )
            }
        }
    }
}
