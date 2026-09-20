package com.arkz.armodelviewer.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.arkz.armodelviewer.R
import com.arkz.armodelviewer.markers.MarkerDefinition
import java.util.Locale

/**
 * Gera a **folha de impressão** de um marcador e salva o PNG na galeria.
 *
 * A folha tem a mesma composição do gerador do projeto
 * (`tools/generate-assets.ps1`, `New-MarkerPrintSheet`): o marcador limpo em cima,
 * com margem branca em volta, e o **rótulo + instruções ABAIXO** da figura.
 *
 * Nada é desenhado *sobre* o marcador de propósito: qualquer texto por cima muda
 * os detalhes visuais que o ARCore usa para reconhecer a imagem.
 *
 * @param fileName nome do arquivo (a pasta é a de marcadores da galeria).
 * @return o caminho legível do arquivo salvo.
 */
suspend fun saveMarkerPrintSheetToGallery(
    context: Context,
    marker: MarkerDefinition,
    fileName: String,
    folder: String,
): Result<String> = runCatching {
    saveBitmapToGallery(context, buildPrintSheet(context, marker), fileName, folder).getOrThrow()
}

/** Desenha a folha de impressão em memória. */
private fun buildPrintSheet(context: Context, marker: MarkerDefinition): Bitmap {
    val markerSize = SHEET_MARKER_PIXELS
    val margin = (markerSize * SHEET_MARGIN_RATIO).toInt()
    val sheetWidth = markerSize + 2 * margin
    val textWidth = sheetWidth - 2 * margin

    // Os textos usam StaticLayout: assim o rótulo (que pode ser longo, digitado
    // pelo usuário) e a instrução (que muda de tamanho em cada idioma) quebram em
    // várias linhas em vez de vazar da folha.
    val labelLayout = centeredLayout(
        text = marker.label,
        paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = markerSize * SHEET_LABEL_TEXT_RATIO
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        },
        width = textWidth,
    )
    val noteLayout = centeredLayout(
        text = context.getString(
            R.string.print_sheet_note,
            String.format(Locale.US, "%.0f", marker.physicalWidthMeters * 100f),
        ),
        paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = markerSize * SHEET_NOTE_TEXT_RATIO
        },
        width = textWidth,
    )

    val captionGap = (markerSize * 0.03f).toInt()
    val captionTop = margin + markerSize + margin
    val sheetHeight = captionTop + labelLayout.height + captionGap + noteLayout.height + margin

    val sheet = Bitmap.createBitmap(sheetWidth, sheetHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(sheet)
    canvas.drawColor(Color.WHITE)

    // Ampliação SEM interpolação: os módulos ficam com bordas nítidas, que é o
    // que o ARCore precisa para reconhecer a figura impressa.
    canvas.drawBitmap(
        marker.bitmap,
        null,
        Rect(margin, margin, margin + markerSize, margin + markerSize),
        Paint().apply { isFilterBitmap = false },
    )

    canvas.save()
    canvas.translate(margin.toFloat(), captionTop.toFloat())
    labelLayout.draw(canvas)
    canvas.translate(0f, (labelLayout.height + captionGap).toFloat())
    noteLayout.draw(canvas)
    canvas.restore()

    return sheet
}

/** [StaticLayout] centralizado, com a largura disponível da coluna de texto. */
private fun centeredLayout(text: CharSequence, paint: TextPaint, width: Int): StaticLayout =
    StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
        .setAlignment(Layout.Alignment.ALIGN_CENTER)
        .setIncludePad(false)
        .build()

/** Lado do marcador na folha, em pixels (≈ 900 px ≈ 7,6 cm a 300 dpi). */
private const val SHEET_MARKER_PIXELS = 900

/** Margem branca em volta do marcador (zona de silêncio para a impressão). */
private const val SHEET_MARGIN_RATIO = 0.08f

/** Tamanho do rótulo e da instrução, proporcionais ao marcador. */
private const val SHEET_LABEL_TEXT_RATIO = 0.075f
private const val SHEET_NOTE_TEXT_RATIO = 0.032f
