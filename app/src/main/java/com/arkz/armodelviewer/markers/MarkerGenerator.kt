package com.arkz.armodelviewer.markers

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.random.Random

/**
 * Desenha a imagem de um marcador NOVO — a figura que o ARCore procura no feed
 * da câmera — a partir do nome dado pelo usuário.
 *
 * O padrão é o mesmo perfil que o ARCore recomenda (alto contraste, muitos
 * cantos, nada repetitivo) e o mesmo do gerador dos marcadores de apoio
 * (`tools/generate-assets.ps1`): matriz pseudo-aleatória de módulos preto/branco
 * sorteada com seed determinística + os três "finder patterns" de um QR code.
 *
 * A seed vem do nome: o MESMO nome gera sempre a MESMA figura e nomes diferentes
 * geram figuras diferentes, de modo que dois marcadores nunca se confundem.
 *
 * A imagem sai em `ARGB_8888` (o único formato aceito por
 * `AugmentedImageDatabase.addImage`) com uma zona de silêncio branca em volta,
 * como num QR code impresso.
 */
object MarkerGenerator {

    /** Resolução padrão: suficiente para imprimir em 15 cm com boa definição. */
    const val DEFAULT_SIZE_PIXELS = 1024

    /** Módulos (quadradinhos) do miolo do marcador, como numa versão 4 de QR. */
    private const val MODULES = 33

    /** Lado dos "finder patterns" de um QR code, em módulos. */
    private const val FINDER_PATTERN_MODULES = 7

    /** Zona de silêncio branca em volta do miolo, em módulos. */
    private const val QUIET_ZONE_MODULES = 4

    /**
     * Gera o marcador de [label].
     *
     * @param label nome informado pelo usuário (define a seed do padrão).
     * @param sizePixels lado aproximado da imagem final, em pixels.
     */
    fun generate(
        label: String,
        sizePixels: Int = DEFAULT_SIZE_PIXELS,
    ): Bitmap {
        require(sizePixels > 0) { "Tamanho de marcador inválido: $sizePixels px" }

        val totalModules = MODULES + 2 * QUIET_ZONE_MODULES
        // Múltiplo exato de módulos: nenhum módulo fica com meio pixel (o ARCore
        // compara bordas nítidas — meio tom atrapalha o reconhecimento).
        val module = (sizePixels / totalModules).coerceAtLeast(2)
        val sidePixels = module * totalModules

        val bitmap = Bitmap.createBitmap(sidePixels, sidePixels, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val black = paint(Color.BLACK)
        val white = paint(Color.WHITE)

        val finderOrigins = listOf(
            0 to 0,
            (MODULES - FINDER_PATTERN_MODULES) to 0,
            0 to (MODULES - FINDER_PATTERN_MODULES),
        )

        // Áreas reservadas aos finder patterns (com 1 módulo de folga em volta,
        // o chamado "separador" do QR code) — o sorteio não escreve nelas.
        val reserved = Array(MODULES) { BooleanArray(MODULES) }
        for ((originX, originY) in finderOrigins) {
            for (y in -1..FINDER_PATTERN_MODULES) {
                for (x in -1..FINDER_PATTERN_MODULES) {
                    val moduleX = originX + x
                    val moduleY = originY + y
                    if (moduleX in 0 until MODULES && moduleY in 0 until MODULES) {
                        reserved[moduleX][moduleY] = true
                    }
                }
            }
        }

        val random = Random(label.trim().lowercase().hashCode())
        for (y in 0 until MODULES) {
            for (x in 0 until MODULES) {
                if (reserved[x][y]) continue
                if (random.nextDouble() > 0.5) {
                    fillModules(canvas, black, x, y, 1, module)
                }
            }
        }

        // Finder patterns: quadrado preto, anel branco e núcleo preto.
        for ((originX, originY) in finderOrigins) {
            fillModules(canvas, black, originX, originY, FINDER_PATTERN_MODULES, module)
            fillModules(
                canvas,
                white,
                originX + 1,
                originY + 1,
                FINDER_PATTERN_MODULES - 2,
                module,
            )
            fillModules(
                canvas,
                black,
                originX + 2,
                originY + 2,
                FINDER_PATTERN_MODULES - 4,
                module,
            )
        }

        return bitmap
    }

    private fun paint(color: Int): Paint = Paint().apply {
        this.color = color
        style = Paint.Style.FILL
        // Borda dura: módulo de 1 pixel não pode virar cinza.
        isAntiAlias = false
    }

    /** Pinta [count]×[count] módulos a partir de ([originX], [originY]). */
    private fun fillModules(
        canvas: Canvas,
        paint: Paint,
        originX: Int,
        originY: Int,
        count: Int,
        module: Int,
    ) {
        val left = ((originX + QUIET_ZONE_MODULES) * module).toFloat()
        val top = ((originY + QUIET_ZONE_MODULES) * module).toFloat()
        canvas.drawRect(left, top, left + count * module, top + count * module, paint)
    }
}
