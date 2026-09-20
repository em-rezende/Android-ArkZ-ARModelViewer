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
import android.graphics.BitmapFactory
import java.io.InputStream

/**
 * Um marcador pronto para uso — a imagem de referência que o ARCore procura no
 * feed da câmera (`AugmentedImageDatabase`).
 *
 * @property id Nome registrado no banco de imagens; é o valor devolvido por
 *   `AugmentedImage.name` quando o marcador é reconhecido (precisa ser único).
 * @property label Rótulo exibido na interface.
 * @property physicalWidthMeters Largura FÍSICA real da imagem, em metros.
 * @property bitmap Imagem de referência já em `ARGB_8888` (exigência do ARCore).
 * @property isCustom `true` para marcadores adicionados pelo usuário (podem ser
 *   removidos pela interface).
 */
data class MarkerDefinition(
    val id: String,
    val label: String,
    val physicalWidthMeters: Float,
    val bitmap: Bitmap,
    val isCustom: Boolean = false,
)

/** Marcador embutido no APK (imagem em `assets/`). */
private data class BundledMarker(
    val id: String,
    val label: String,
    val assetPath: String,
    val physicalWidthMeters: Float,
)

/**
 * Marcadores que acompanham o app.
 *
 * As imagens são QR codes (alto contraste, muitos cantos, padrão não repetitivo)
 * — o perfil que o ARCore recomenda. Para trocar, substitua os arquivos em
 * `assets/augmented_images/` (e reajuste a largura física se mudar o tamanho de
 * impressão).
 */
object MarkerCatalog {

    /** Largura física padrão considerada para um marcador (15 cm). */
    const val DEFAULT_PHYSICAL_WIDTH_METERS = 0.15f

    private val bundled = listOf(
        BundledMarker(
            id = "marker_a",
            label = "Marcador A",
            assetPath = "augmented_images/marker_a.png",
            physicalWidthMeters = DEFAULT_PHYSICAL_WIDTH_METERS,
        ),
        BundledMarker(
            id = "marker_b",
            label = "Marcador B",
            assetPath = "augmented_images/marker_b.png",
            physicalWidthMeters = DEFAULT_PHYSICAL_WIDTH_METERS,
        ),
    )

    /** Carrega os marcadores embutidos (assets) como bitmaps `ARGB_8888`. */
    fun loadBundled(context: Context): List<MarkerDefinition> = bundled.mapNotNull { marker ->
        runCatching {
            val bitmap = context.assets.open(marker.assetPath).use { decodeArgb8888(it) }
            MarkerDefinition(
                id = marker.id,
                label = marker.label,
                physicalWidthMeters = marker.physicalWidthMeters,
                bitmap = bitmap,
            )
        }.getOrNull()
    }
}

/** Rótulo de um marcador a partir do nome devolvido pelo ARCore. */
fun List<MarkerDefinition>.labelFor(id: String): String =
    firstOrNull { it.id == id }?.label ?: id

/** Decodifica uma imagem garantindo o formato `ARGB_8888`. */
fun decodeArgb8888(input: InputStream): Bitmap {
    val decoded = BitmapFactory.decodeStream(input)
        ?: error("Não foi possível decodificar a imagem.")
    return decoded.toArgb8888()
}

/**
 * Converte para `ARGB_8888`.
 *
 * É o único formato aceito pelo `AugmentedImageDatabase.addImage` — um bitmap
 * `RGB_565` (comum em JPEGs) faz o ARCore lançar `IllegalArgumentException`.
 */
fun Bitmap.toArgb8888(): Bitmap =
    if (config == Bitmap.Config.ARGB_8888) {
        this
    } else {
        copy(Bitmap.Config.ARGB_8888, false).also { recycle() }
    }
