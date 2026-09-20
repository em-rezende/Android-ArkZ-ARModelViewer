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

import android.content.Context

/**
 * Preferências simples do app (`SharedPreferences`).
 *
 * Guarda apenas o que precisa sobreviver ao reinício do app e não pertence a
 * nenhum marcador: se a **Ajuda já foi mostrada** (primeira execução) e a
 * **qualidade escolhida para a captura de tela**.
 */
class AppPreferences(context: Context) {

    private val preferences = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** `true` depois que a Ajuda foi exibida uma vez (primeira execução). */
    var helpShown: Boolean
        get() = preferences.getBoolean(KEY_HELP_SHOWN, false)
        set(value) = preferences.edit().putBoolean(KEY_HELP_SHOWN, value).apply()

    /**
     * Idioma forçado pelo menu **"Idioma"** (código BCP-47: `pt-BR`, `pt-PT`,
     * `en`, `es`, `fr`, `de`, `it`, `zh-CN`) ou `null` para seguir o idioma do
     * aparelho.
     */
    var languageTag: String?
        get() = preferences.getString(KEY_LANGUAGE_TAG, null)?.takeIf { it.isNotBlank() }
        set(value) = preferences.edit().putString(KEY_LANGUAGE_TAG, value).apply()

    /**
     * Lado maior da captura de tela, em pixels — `null` significa "resolução da
     * tela" (sem limite).
     *
     * O valor é guardado como inteiro (0 = sem limite) porque as preferências não
     * têm tipo anulável.
     */
    var captureMaxLongSide: Int?
        get() = preferences.getInt(KEY_CAPTURE_MAX_LONG_SIDE, DEFAULT_CAPTURE_MAX_LONG_SIDE)
            .takeIf { it > 0 }
        set(value) = preferences
            .edit()
            .putInt(KEY_CAPTURE_MAX_LONG_SIDE, value ?: 0)
            .apply()

    private companion object {
        const val NAME = "app_preferences"
        const val KEY_HELP_SHOWN = "help_shown"
        const val KEY_CAPTURE_MAX_LONG_SIDE = "capture_max_long_side"
        const val KEY_LANGUAGE_TAG = "language_tag"

        /** Mesma resolução usada pela captura antes de ela virar uma opção. */
        const val DEFAULT_CAPTURE_MAX_LONG_SIDE = 1920
    }
}
