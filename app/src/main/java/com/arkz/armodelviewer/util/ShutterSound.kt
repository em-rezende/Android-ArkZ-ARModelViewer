/*
 * ArkZ ARModelViewer — visualizador de modelos 3D em Realidade Aumentada.
 * Copyright (C) 2026 Ark-Z Arquitetura Ltda
 *
 * Este programa é software livre: você pode redistribuí-lo e/ou modificá-lo sob
 * os termos da GNU General Public License, versão 3, publicada pela Free Software
 * Foundation. Este programa é distribuído na esperança de que seja útil, mas SEM
 * NENHUMA GARANTIA; sem mesmo a garantia implícita de COMERCIABILIDADE ou
 * ADEQUAÇÃO A UM PROPÓSITO ESPECÍFICO. Veja o arquivo LICENSE na raiz do projeto.
 *
 * Autoria: Ark-Z Arquitetura Ltda — desenvolvedor: Ezequiel M. Rezende.
 * https://github.com/em-rezende/Android-ArkZ-ARModelViewer
 */

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
