package com.arkz.armodelviewer.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.HardwareBuffer
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.arkz.armodelviewer.model.AR_LOG_TAG
import io.github.sceneview.utils.SurfaceMirrorer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Captura a cena de RA (câmera + conteúdo virtual) e salva como PNG na galeria.
 *
 * Usa o [SurfaceMirrorer] do SceneView: o **mesmo render pass da cena** é desenhado
 * uma segunda vez em um [ImageReader] — apenas no momento da captura — e o quadro
 * resultante é lido desse buffer.
 *
 * Por que não copiar a janela com `PixelCopy`? Porque o `ARSceneView` precisa então
 * ser criado com `SurfaceType.TextureSurface` (o conteúdo de um `SurfaceView` fica
 * em outra superfície e não entraria na cópia) — e o `TextureSurface` joga a
 * composição de cada quadro na thread de UI, deixando a RA de modelos pesados
 * lenta (travamentos/ANR). Com o espelhamento, a view continua no
 * `SurfaceType.Surface` (melhor desempenho) e o overlay do Compose **não** entra na
 * imagem, porque só a cena do Filament é espelhada.
 *
 * ## O que nunca pode acontecer (era o que derrubava o app)
 *
 * O espelhamento cria um *swap chain* do Filament na superfície do [ImageReader]. Se
 * uma segunda captura (ou a saída da tela) cancelasse a corrotina, o `finally`
 * rodava em um escopo já cancelado: o `stopMirroring` (que exige a thread principal
 * e é *suspend*) nem acontecia, mas o `ImageReader` era fechado — e o renderizador
 * continuava desenhando naquela superfície liberada, derrubando o processo. A
 * criação do [ImageReader] e a conversão do quadro também ficavam fora de qualquer
 * `runCatching`: sem memória (tela cheia QHD = ~20 MB só de buffers) a exceção
 * escapava do `LaunchedEffect`. Agora tudo é `runCatching`, a limpeza roda em
 * `NonCancellable` na ordem correta e a captura usa resolução limitada.
 *
 * ## A leitura do quadro precisa de um buffer legível pela CPU
 *
 * O PNG saía com pixels embaralhados quando a superfície era criada apenas com
 * `USAGE_GPU_COLOR_OUTPUT`: sem `USAGE_CPU_READ_OFTEN` (a marca que o construtor
 * padrão do `ImageReader` define), o Android pode alocar o buffer em formato *tiled*,
 * e a leitura linear de [Image.planes] devolve lixo. Veja [createImageReader].
 *
 * @param viewWidth/viewHeight dimensões da view de RA (destino do espelhamento).
 * @param maxLongSide limite do lado maior da captura, em pixels — `null` captura na
 *   resolução da tela (opção "Máxima" do menu). A escolha é do usuário e fica
 *   guardada em [AppPreferences].
 */
suspend fun captureSceneToGallery(
    context: Context,
    surfaceMirrorer: SurfaceMirrorer,
    viewWidth: Int,
    viewHeight: Int,
    fileName: String,
    folder: String,
    maxLongSide: Int? = MAX_CAPTURE_LONG_SIDE,
): Result<String> = runCatching {
    require(viewWidth > 0 && viewHeight > 0) {
        "A view de realidade aumentada ainda não tem dimensões válidas."
    }

    // Mesma proporção da view, porém limitada: o espelhamento redesenha a cena e o
    // quadro existe em duas cópias na memória (buffer do ImageReader + Bitmap).
    val (captureWidth, captureHeight) = scaledCaptureSize(viewWidth, viewHeight, maxLongSide)

    val imageReader = createImageReader(captureWidth, captureHeight)
    val firstFrame = CompletableDeferred<Image>()
    imageReader.setOnImageAvailableListener({ reader ->
        val image = runCatching { reader.acquireLatestImage() }.getOrNull()
            ?: return@setOnImageAvailableListener
        // `complete` só vale na primeira chamada; quadros seguintes são descartados
        // (e fechados, para não travar a fila do ImageReader).
        if (!firstFrame.complete(image)) image.close()
    }, Handler(Looper.getMainLooper()))

    try {
        // O espelhamento começa no próximo quadro renderizado. É thread-safe e não
        // custa nada enquanto não houver captura em andamento.
        surfaceMirrorer.startMirroring(
            surface = imageReader.surface,
            width = captureWidth,
            height = captureHeight,
        )

        val image = withTimeoutOrNull(CAPTURE_TIMEOUT_MILLIS) { firstFrame.await() }
            ?: error("Tempo esgotado ao capturar a cena de RA.")

        // Som de "disparo" da câmera: o quadro já está capturado, então o som sai
        // no momento certo — antes da conversão e do salvamento, que levam algumas
        // centenas de milissegundos.
        ShutterSound.play()

        // A leitura do buffer e a criação do Bitmap (alguns MB) saem da thread
        // principal: não há motivo para travar a composição da RA nessa cópia.
        val (bitmap, bufferInfo) = withContext(Dispatchers.Default) {
            try {
                val plane = image.planes.firstOrNull()
                val info = "quadro=${image.width}x${image.height} " +
                    "rowStride=${plane?.rowStride} pixelStride=${plane?.pixelStride}"
                image.toBitmap() to info
            } finally {
                image.close()
            }
        }
        // Diagnóstico (Logcat): distingue "a cena não foi desenhada no buffer" de
        // "o buffer foi lido com a geometria errada".
        Log.i(
            AR_LOG_TAG,
            "Captura de tela: $bufferInfo → PNG ${bitmap.width}x${bitmap.height}",
        )
        if (bitmap.isSingleColor()) {
            Log.w(
                AR_LOG_TAG,
                "A imagem capturada tem uma única cor: a cena provavelmente não foi " +
                    "desenhada na superfície de espelhamento.",
            )
        }

        saveBitmapToGallery(context, bitmap, fileName, folder).getOrThrow()
    } finally {
        // `NonCancellable` é obrigatório: a ORDEM importa (primeiro desligar o
        // espelhamento, depois fechar o ImageReader) e um cancelamento não pode
        // pular a primeira etapa — veja a nota no KDoc da função.
        withContext(NonCancellable + Dispatchers.Main) {
            firstFrame.takeIf { it.isCompleted }?.let { deferred ->
                runCatching { deferred.getCompleted().close() }
            }
            runCatching { surfaceMirrorer.stopMirroring(imageReader.surface) }
            runCatching { imageReader.close() }
        }
    }
}.onFailure { error ->
    // O usuário recebe a mensagem na barra de status; o Logcat mostra a causa.
    Log.w(
        AR_LOG_TAG,
        "Falha na captura de tela: ${error.message ?: error.javaClass.simpleName}",
        error,
    )
}

/**
 * Cria o [ImageReader] que recebe o quadro espelhado.
 *
 * As **duas** marcas de uso são necessárias neste caso:
 *  - `USAGE_GPU_COLOR_OUTPUT`: o espelhamento **desenha** nesta superfície (ela é
 *    um destino de cor da GPU);
 *  - `USAGE_CPU_READ_OFTEN`: nós **lemos** o quadro pela CPU
 *    (`Image.planes[0].buffer`) para montar o PNG.
 *
 * A segunda é a que faltava: o construtor padrão do `ImageReader` define
 * exatamente ela — em `ImageReader.java`, `newInstance(width, height, format,
 * maxImages)` chama `new ImageReader(..., format == ImageFormat.PRIVATE ? 0 :
 * HardwareBuffer.USAGE_CPU_READ_OFTEN, null)`. Passando apenas
 * `USAGE_GPU_COLOR_OUTPUT`, essa marca era perdida e o Android podia alocar o
 * buffer em formato *tiled* (acessível só pela GPU): o PNG saía com pixels
 * embaralhados, sem nenhuma relação com a cena capturada.
 */
private fun createImageReader(width: Int, height: Int): ImageReader =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ImageReader.newInstance(
            width,
            height,
            PixelFormat.RGBA_8888,
            MAX_IMAGES,
            HardwareBuffer.USAGE_GPU_COLOR_OUTPUT or HardwareBuffer.USAGE_CPU_READ_OFTEN,
        )
    } else {
        ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, MAX_IMAGES)
    }

/**
 * Tamanho da captura: a mesma proporção da view, com o lado maior limitado a
 * [maxLongSide] pixels (`null` = sem limite, resolução da tela) e ambos os lados
 * pares (como os drivers esperam de uma superfície de renderização).
 */
private fun scaledCaptureSize(
    viewWidth: Int,
    viewHeight: Int,
    maxLongSide: Int?,
): Pair<Int, Int> {
    val longestSide = maxOf(viewWidth, viewHeight)
    val factor = if (maxLongSide != null && longestSide > maxLongSide) {
        maxLongSide.toFloat() / longestSide
    } else {
        1f
    }
    return even((viewWidth * factor).toInt()) to even((viewHeight * factor).toInt())
}

/** Arredonda para baixo até um número par (nunca menor que 2). */
private fun even(value: Int): Int = (value - value % 2).coerceAtLeast(2)

/**
 * Amostra a imagem (32×32 pontos) para detectar um quadro de **uma única cor** —
 * sinal de que a cena não chegou a ser desenhada na superfície espelhada.
 */
private fun Bitmap.isSingleColor(): Boolean {
    val stepX = (width / 32).coerceAtLeast(1)
    val stepY = (height / 32).coerceAtLeast(1)
    val first = getPixel(0, 0)
    var y = 0
    while (y < height) {
        var x = 0
        while (x < width) {
            if (getPixel(x, y) != first) return false
            x += stepX
        }
        y += stepY
    }
    return true
}

/**
 * Converte um quadro `RGBA_8888` do [ImageReader] em [Bitmap], respeitando o
 * `rowStride` (que costuma trazer padding no fim de cada linha).
 */
private fun Image.toBitmap(): Bitmap {
    val plane = planes.firstOrNull()
        ?: error("O quadro capturado não tem planos de pixel.")
    val pixelStride = plane.pixelStride
    require(pixelStride == 4) {
        "Formato inesperado no quadro capturado (pixelStride=$pixelStride)."
    }
    val rowStride = plane.rowStride
    val rowPadding = rowStride - pixelStride * width
    val paddedWidth = width + rowPadding / pixelStride

    val padded = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
    padded.copyPixelsFromBuffer(plane.buffer)

    if (paddedWidth == width) return padded
    val cropped = Bitmap.createBitmap(padded, 0, 0, width, height)
    padded.recycle()
    return cropped
}

/** Quantos quadros o [ImageReader] mantém em fila (2 é suficiente para 1 captura). */
private const val MAX_IMAGES = 2

/** Espera máxima por um quadro espelhado. */
private const val CAPTURE_TIMEOUT_MILLIS = 4_000L

/**
 * Lado maior da captura, em pixels — valor PADRÃO, usado quando o usuário não
 * escolheu outra qualidade no menu. Uma tela QHD (1440×3200) geraria buffers de
 * ~18 MB cada (dois no ImageReader + o Bitmap), sem ganho real na imagem.
 */
private const val MAX_CAPTURE_LONG_SIDE = 1_920
