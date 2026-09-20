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

package com.arkz.armodelviewer.markers

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Armazena os **marcadores personalizados** — imagens `.png`/`.jpg` escolhidas
 * pelo usuário (galeria/arquivos do aparelho) para serem rastreadas pelo ARCore.
 *
 * A imagem é copiada para o armazenamento privado do app (a permissão de leitura
 * da URI original expira) e reconvertida para PNG `ARGB_8888`, o formato que o
 * `AugmentedImageDatabase` exige. Os metadados ficam em `SharedPreferences`, de
 * modo que os marcadores sobrevivem ao reinício do app.
 */
class CustomMarkerStore(private val context: Context) {

    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private val directory: File
        get() = File(context.filesDir, DIRECTORY_NAME).apply { mkdirs() }

    /** Metadados de um marcador personalizado. */
    data class Entry(
        val id: String,
        val label: String,
        val fileName: String,
        val widthMeters: Float,
    ) {
        /** Codificação simples (`|` é removido do rótulo para não quebrar o formato). */
        fun encode(): String = "$id|${label.replace('|', '-')}|$fileName|$widthMeters"

        companion object {
            fun decode(encoded: String): Entry? {
                val parts = encoded.split('|')
                if (parts.size < 4) return null
                val width = parts[3].toFloatOrNull() ?: return null
                return Entry(parts[0], parts[1], parts[2], width)
            }
        }
    }

    /** Lista os marcadores personalizados salvos (ordenados pelo rótulo). */
    fun list(): List<Entry> = preferences
        .getStringSet(KEY_ENTRIES, emptySet())
        .orEmpty()
        .mapNotNull { Entry.decode(it) }
        .sortedBy { it.label.lowercase() }

    /** Carrega as imagens (bitmaps) dos marcadores personalizados. */
    fun loadDefinitions(): List<MarkerDefinition> = list().mapNotNull { entry ->
        runCatching {
            val file = File(directory, entry.fileName)
            require(file.isFile) { "Imagem do marcador não encontrada: ${entry.fileName}" }
            val bitmap = file.inputStream().use { decodeArgb8888(it) }
            MarkerDefinition(
                id = entry.id,
                label = entry.label,
                physicalWidthMeters = entry.widthMeters,
                bitmap = bitmap,
                isCustom = true,
            )
        }.getOrNull()
    }

    /**
     * Copia a imagem de [source] para o app e registra um novo marcador
     * ("Carregar marcador": imagem que já está no aparelho).
     *
     * @param label rótulo exibido na interface (o nome do arquivo é usado quando
     *   não informado).
     */
    suspend fun add(
        source: Uri,
        label: String? = null,
        widthMeters: Float = MarkerCatalog.DEFAULT_PHYSICAL_WIDTH_METERS,
    ): Result<MarkerDefinition> = withContext(Dispatchers.IO) {
        runCatching {
            val displayName = queryDisplayName(source)
            // Decodifica e regrava como PNG/ARGB_8888: garante o formato exigido
            // pelo ARCore, independentemente do arquivo de origem.
            val bitmap = context.contentResolver.openInputStream(source)
                ?.use { decodeArgb8888(it) }
                ?: error("Não foi possível ler a imagem escolhida.")
            store(
                bitmap = bitmap,
                label = label?.takeIf { it.isNotBlank() }
                    ?: displayName.substringBeforeLast('.').ifBlank { "Marcador personalizado" },
                widthMeters = widthMeters,
            )
        }
    }

    /**
     * Registra um marcador a partir de um bitmap JÁ pronto — usado pelo
     * "Criar marcador", que desenha a figura no próprio app
     * ([MarkerGenerator.generate]).
     */
    suspend fun addBitmap(
        bitmap: Bitmap,
        label: String,
        widthMeters: Float = MarkerCatalog.DEFAULT_PHYSICAL_WIDTH_METERS,
    ): Result<MarkerDefinition> = withContext(Dispatchers.IO) {
        runCatching {
            store(
                bitmap = bitmap,
                label = label.trim().takeIf { it.isNotEmpty() } ?: "Marcador personalizado",
                widthMeters = widthMeters,
            )
        }
    }

    /**
     * Grava o bitmap como PNG `ARGB_8888` na pasta privada do app + metadados nas
     * preferências, e devolve o marcador pronto para uso imediato.
     */
    private fun store(bitmap: Bitmap, label: String, widthMeters: Float): MarkerDefinition {
        val id = "custom_${System.currentTimeMillis()}"
        val fileName = "$id.png"
        File(directory, fileName).outputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }

        val entry = Entry(id = id, label = label, fileName = fileName, widthMeters = widthMeters)
        val entries = list().filterNot { it.id == id } + entry
        preferences.edit()
            .putStringSet(KEY_ENTRIES, entries.map { it.encode() }.toSet())
            .apply()

        return MarkerDefinition(
            id = entry.id,
            label = entry.label,
            physicalWidthMeters = entry.widthMeters,
            bitmap = bitmap,
            isCustom = true,
        )
    }

    /** Remove um marcador personalizado (metadados + imagem). */
    fun remove(id: String) {
        val remaining = list().filterNot { it.id == id }
        preferences.edit()
            .putStringSet(KEY_ENTRIES, remaining.map { it.encode() }.toSet())
            .apply()
        File(directory, "$id.png").delete()
    }

    private fun queryDisplayName(uri: Uri): String {
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) {
                    cursor.getString(index)?.let { return it }
                }
            }
        return uri.lastPathSegment ?: "marcador.png"
    }

    private companion object {
        const val PREFERENCES_NAME = "custom_markers"
        const val KEY_ENTRIES = "entries"
        const val DIRECTORY_NAME = "custom_markers"
    }
}
