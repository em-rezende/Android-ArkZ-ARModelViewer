package com.arkz.armodelviewer.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.arkz.armodelviewer.R
import com.arkz.armodelviewer.markers.CustomMarkerStore
import com.arkz.armodelviewer.markers.MarkerCatalog
import com.arkz.armodelviewer.markers.MarkerDefinition
import com.arkz.armodelviewer.markers.MarkerGenerator
import com.arkz.armodelviewer.markers.labelFor
import com.arkz.armodelviewer.model.AR_LOG_TAG
import com.arkz.armodelviewer.model.ModelFileStaging
import com.arkz.armodelviewer.model.ModelLoadState
import com.arkz.armodelviewer.model.StagedModel
import com.arkz.armodelviewer.model.rememberModelLoadState
import com.arkz.armodelviewer.util.AppPreferences
import com.arkz.armodelviewer.util.DEVELOPER_EMAIL
import com.arkz.armodelviewer.util.DEVELOPER_SITE_URL
import com.arkz.armodelviewer.util.appVersionLabel
import com.arkz.armodelviewer.util.captureSceneToGallery
import com.arkz.armodelviewer.util.copyToClipboard
import com.arkz.armodelviewer.util.deviceLabel
import com.arkz.armodelviewer.util.openExternal
import com.arkz.armodelviewer.util.playStoreMarketUri
import com.arkz.armodelviewer.util.playStoreWebUri
import com.arkz.armodelviewer.util.saveBitmapToGallery
import com.arkz.armodelviewer.util.saveMarkerPrintSheetToGallery
import com.arkz.armodelviewer.util.sendEmail
import com.arkz.armodelviewer.util.shareApp
import com.google.ar.core.AugmentedImage
import com.google.ar.core.Config
import com.google.ar.core.TrackingState
import io.github.sceneview.SurfaceType
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.arcore.AddImageResult
import io.github.sceneview.ar.arcore.rememberRuntimeAugmentedImageDatabase
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.math.Size
import io.github.sceneview.model.ModelInstance
import io.github.sceneview.model.model
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberSurfaceMirrorer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Valor de filtro que libera o modelo para QUALQUER marcador detectado. */
private const val ALL_MARKERS_ID = "all_markers"

/** Tamanho inicial do modelo (maior dimensão, em metros). */
private const val DEFAULT_MODEL_SIZE_METERS = 0.2f
private const val MIN_MODEL_SIZE_METERS = 0.02f
private const val MAX_MODEL_SIZE_METERS = 2.0f

/**
 * Rotação inicial do modelo: 0°, 0°, 0° (sem transformação).
 *
 * O espaço do marcador vem da pose da imagem no ARCore: +X = direita da imagem,
 * +Y = "para cima" no plano da imagem e +Z = normal (saindo do marcador). Cada
 * modelo glTF tem sua própria convenção de "para cima", por isso o ajuste fica
 * por conta dos sliders do painel (ex.: 90° em X para colocar em pé um modelo
 * cujo eixo "para cima" é +Y).
 */
private const val DEFAULT_ROTATION_X = 0f

/**
 * Elevação inicial do modelo em relação ao PLANO DO MARCADOR (metros),
 * controlada pelo slider "Elevação Z".
 *
 * O eixo +Z do marcador é a normal da imagem (saindo do plano, na direção da
 * câmera). Valores positivos afastam o modelo da superfície do marcador — útil
 * quando o modelo tem profundidade (a metade "de trás" dele ficaria enterrada no
 * plano) ou quando o marcador está sobre uma mesa/parede e o modelo precisa
 * flutuar à frente dela.
 */
private const val DEFAULT_ELEVATION_METERS = 0f
private const val MIN_ELEVATION_METERS = -0.5f
private const val MAX_ELEVATION_METERS = 0.5f

/** Passo do slider de elevação: 1 cm. */
private const val ELEVATION_STEP_METERS = 0.01f

/** Opções de qualidade da captura de tela: lado maior em pixels (`null` = tela). */
private const val CAPTURE_QUALITY_STANDARD = 1280
private const val CAPTURE_QUALITY_HIGH = 1920
private val CAPTURE_QUALITY_OPTIONS: List<Int?> = listOf(
    CAPTURE_QUALITY_STANDARD,
    CAPTURE_QUALITY_HIGH,
    null,
)

/** Tempo que a mensagem "Marcador detectado" fica visível antes de desaparecer. */
private const val DETECTION_MESSAGE_MILLIS = 4000L

/** Tempo que uma mensagem de erro fica visível. */
private const val ERROR_MESSAGE_MILLIS = 8000L

/** Tempo que a confirmação de modelo carregado (com a duração) fica visível. */
private const val LOAD_TIP_MILLIS = 8000L

/** Depois deste tempo, o carregamento é rotulado como "modelo grande". */
private const val SLOW_LOAD_HINT_MILLIS = 20_000L

/**
 * Tela de Realidade Aumentada do ArkZ ARModelViewer.
 *
 * Fluxo principal:
 *  1. o [ARSceneView] abre a sessão do ARCore e registra os marcadores no
 *     `AugmentedImageDatabase` (embutidos no APK + imagens personalizadas);
 *  2. a cada frame, `onSessionUpdated` consulta as imagens RECONHECIDAS
 *     (`frame.getUpdatedTrackables`);
 *  3. cada imagem reconhecida vira um `AugmentedImageNode` (ancorado à pose do
 *     marcador) com o `ModelNode` dentro;
 *  4. o modelo pode ser girado (eixos X/Y/Z) e redimensionado pelo painel de
 *     ajustes, por pinça na tela ou pela escala automática;
 *  5. a barra flutuante oferece Reset, marcadores, ajustes, escala automática,
 *     captura de tela e saída.
 *
 * @param onExit Chamado pelo botão "Sair" (finaliza a Activity).
 * @param languageTag idioma forçado no menu "Idioma" (`null` = idioma do aparelho).
 * @param onLanguageChange grava a escolha do idioma nas preferências do app.
 */
@Composable
fun ARViewScreen(
    onExit: () -> Unit,
    languageTag: String?,
    onLanguageChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ── Recursos do Filament/SceneView ──────────────────────────────────────
    // Os helpers remember* criam e liberam os recursos nativos (JNI) junto ao
    // ciclo de vida da composição; toda chamada ao Filament roda na main thread.
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)

    // ── Estado da interface ─────────────────────────────────────────────────
    var selectedMarkerId by remember { mutableStateOf(ALL_MARKERS_ID) }
    var showMarkerDialog by remember { mutableStateOf(false) }
    var showCreateMarkerDialog by remember { mutableStateOf(false) }
    // Menu do canto superior direito (três pontos verticais).
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showCaptureQualityDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    // Preferências que sobrevivem ao reinício: Ajuda da primeira execução e a
    // qualidade escolhida para a captura de tela.
    val preferences = remember { AppPreferences(context) }
    var captureMaxLongSide by remember { mutableStateOf(preferences.captureMaxLongSide) }
    var markerNameInput by remember { mutableStateOf("") }
    // Nome digitado em "Criar marcador", usado quando a imagem for escolhida.
    var pendingMarkerName by remember { mutableStateOf<String?>(null) }
    var showModelSourceDialog by remember { mutableStateOf(false) }
    var showAdjustPanel by remember { mutableStateOf(false) }
    var stagedModel by remember { mutableStateOf<StagedModel?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var sessionReady by remember { mutableStateOf(false) }
    // Tamanho da view de RA — usado como destino do espelhamento na captura.
    var arViewSize by remember { mutableStateOf(IntSize.Zero) }

    // Transformação do modelo: ajustável pelo painel (sliders) e pelos gestos.
    var rotationX by remember { mutableFloatStateOf(DEFAULT_ROTATION_X) }
    var rotationY by remember { mutableFloatStateOf(0f) }
    var rotationZ by remember { mutableFloatStateOf(0f) }
    var modelSizeMeters by remember { mutableFloatStateOf(DEFAULT_MODEL_SIZE_METERS) }
    // Elevação em relação ao plano do marcador (normal da imagem).
    var modelElevationMeters by remember { mutableFloatStateOf(DEFAULT_ELEVATION_METERS) }

    // Imagens reconhecidas pelo ARCore. Guardamos somente FATOS DERIVADOS
    // (nunca o objeto Frame) para não recompor a interface a 60 FPS.
    val detectedImages = remember { mutableStateListOf<AugmentedImage>() }

    // ── Marcadores: embutidos (assets) + personalizados (escolhidos) ────────
    val customMarkerStore = remember { CustomMarkerStore(context) }
    val markers = remember { mutableStateListOf<MarkerDefinition>() }
    // Banco de imagens do ARCore que aceita novas imagens em tempo de execução
    // (encapsula o AugmentedImageDatabase, reconstruindo-o e reaplicando-o).
    val arImageDatabase = rememberRuntimeAugmentedImageDatabase()

    // Espelhamento usado APENAS na captura de tela: permite manter a view em
    // `SurfaceType.Surface` (o modo de melhor desempenho) sem perder o recurso.
    val surfaceMirrorer = rememberSurfaceMirrorer()

    LaunchedEffect(Unit) {
        // Limpa as cópias antigas de modelos UMA vez, ao abrir a tela — nunca
        // durante uma cópia, para não apagar o arquivo de um carregamento em
        // andamento.
        withContext(Dispatchers.IO) { ModelFileStaging.cleanup(context) }
        reloadMarkers(context, customMarkerStore, markers)
        // Primeira execução: apresenta a Ajuda uma única vez (a marca fica nas
        // preferências). É o que explica o fluxo para quem abre o app sem manual.
        if (!preferences.helpShown) {
            preferences.helpShown = true
            showHelpDialog = true
        }
    }

    // Sempre que a lista de marcadores muda (início, marcador adicionado ou
    // removido), o banco do ARCore é re-sincronizado: a sessão aceita apenas UM
    // banco de imagens, então limpamos e registramos o conjunto atual.
    val markerIds = markers.map { marker -> marker.id }
    LaunchedEffect(markerIds) {
        arImageDatabase.clear()
        detectedImages.clear()
        for (marker in markers) {
            // addImage extrai as features fora da main thread e já reconfigura a
            // sessão para que a nova imagem passe a ser rastreada.
            when (
                val result = arImageDatabase.addImage(
                    marker.id,
                    marker.bitmap,
                    marker.physicalWidthMeters,
                )
            ) {
                AddImageResult.Added -> Unit

                AddImageResult.LowQuality -> message =
                    "${marker.label}: imagem com poucos detalhes/contraste — o ARCore pode não reconhecê-la."

                is AddImageResult.Error -> message =
                    "${marker.label}: ${result.cause.message ?: "falha ao registrar o marcador."}"
            }
        }
    }

    // ── Modelo escolhido pelo usuário ───────────────────────────────────────
    val modelState = rememberModelLoadState(modelLoader, stagedModel?.location)
    val modelInstance = (modelState as? ModelLoadState.Loaded)?.instance

    // Material do marcador-placeholder, exibido quando um marcador é
    // reconhecido antes de existir um modelo carregado.
    val placeholderMaterial = remember(materialLoader) {
        materialLoader.createColorInstance(Color(0xFF00E5FF))
    }

    // ── Seletores do sistema ────────────────────────────────────────────────
    val handlePickedUri: (Uri?) -> Unit = { uri ->
        if (uri != null) {
            scope.launch {
                ModelFileStaging.stage(context, uri)
                    .onSuccess { staged ->
                        stagedModel = staged
                        message = null
                    }
                    .onFailure { error ->
                        message = error.message ?: "Não foi possível abrir o arquivo escolhido."
                    }
            }
        }
    }

    // Seletor de arquivos do sistema, SEM filtro de tipo MIME.
    //
    // O `ACTION_OPEN_DOCUMENT` filtra por MIME, e o Android não conhece os tipos
    // de `.glb`, `.obj` e `.ply` — eles chegam como `application/octet-stream`.
    // Com um filtro por MIME o usuario veria esses arquivos *esmaecidos e
    // impossiveis de selecionar* (so `.stl` tem tipo reconhecido). Por isso a
    // lista sai sem filtro e a validacao e feita por EXTENSAO em
    // `ModelFileStaging.stage()`, que explica o motivo quando o formato nao serve.
    val pickModelLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = handlePickedUri,
    )
    // 4) Imagem de um marcador NOVO (galeria/fotos). O nome digitado em
    //    "Criar marcador" vira o rótulo e o nome do PNG exportado.
    val pickMarkerImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        val markerName = pendingMarkerName
        pendingMarkerName = null
        if (uri != null) {
            scope.launch {
                customMarkerStore.add(uri, label = markerName)
                    .onSuccess { marker ->
                        reloadMarkers(context, customMarkerStore, markers)
                        selectedMarkerId = marker.id
                        // Exporta a imagem como PNG na pasta de imagens do
                        // aparelho, para imprimir e usar em outros projetos.
                        message = saveMarkerImageToGallery(context, marker).fold(
                            onSuccess = { path ->
                                context.getString(
                                    R.string.status_marker_created,
                                    marker.label,
                                    path,
                                )
                            },
                            onFailure = { error ->
                                context.getString(
                                    R.string.status_marker_created_no_export,
                                    marker.label,
                                    error.message ?: error.javaClass.simpleName,
                                )
                            },
                        )
                    }
                    .onFailure { error ->
                        message = error.message ?: "Não foi possível usar a imagem escolhida."
                    }
            }
        }
    }

    // ── Estado derivado (lido pela UI) ──────────────────────────────────────
    // `distinctByPose` elimina conteúdo duplicado quando duas entradas do banco
    // de imagens casam com a MESMA figura física (por exemplo um marcador
    // personalizado criado a partir de uma imagem igual à de um marcador
    // embutido) — sem ele, dois nodes ficavam exatamente um sobre o outro.
    val filteredImages = detectedImages
        .filter { image -> selectedMarkerId == ALL_MARKERS_ID || image.name == selectedMarkerId }
    // A comparação de poses só é necessária com mais de uma imagem (cada leitura
    // de pose é uma chamada JNI ao ARCore).
    val visibleImages = if (filteredImages.size > 1) {
        filteredImages.distinctByPose()
    } else {
        filteredImages
    }
    val selectedMarkerLabel = if (selectedMarkerId == ALL_MARKERS_ID) {
        stringResource(R.string.marker_all)
    } else {
        markers.labelFor(selectedMarkerId)
    }
    val trackedLabels = visibleImages.map { image -> markers.labelFor(image.name) }.distinct()
    val trackedMarkersText = trackedLabels.joinToString(", ")

    // Resultado (e demora) do carregamento: mensagens passageiras que não
    // obstruem a visão da câmera. O mesmo efeito registra o resultado no Logcat:
    // um "modelo que não aparece" pode ser falha de leitura, escala errada ou
    // modelo longe do marcador, e o log diz qual dos três é.
    var loadTip by remember { mutableStateOf<String?>(null) }
    var slowLoad by remember { mutableStateOf(false) }
    LaunchedEffect(modelState) {
        slowLoad = false
        when (val state = modelState) {
            is ModelLoadState.Loaded -> {
                val seconds = String.format(Locale.US, "%.1f", state.durationMillis / 1000f)
                Log.i(
                    AR_LOG_TAG,
                    "Modelo carregado em $seconds s: ${stagedModel?.displayName} " +
                        "(maior dimensão no arquivo: ${state.largestDimensionUnits} unidades)",
                )
                loadTip = context.getString(
                    R.string.status_model_loaded,
                    stagedModel?.displayName ?: "",
                    seconds,
                    String.format(Locale.US, "%.2f", state.largestDimensionUnits),
                )
                delay(LOAD_TIP_MILLIS)
                loadTip = null
            }

            ModelLoadState.Loading -> {
                // A criação do asset roda na thread principal: modelos grandes
                // levam dezenas de segundos. Avisa em vez de parecer travado.
                delay(SLOW_LOAD_HINT_MILLIS)
                slowLoad = true
            }

            is ModelLoadState.Failed -> {
                Log.w(
                    AR_LOG_TAG,
                    "Falha ao carregar ${stagedModel?.displayName} " +
                        "(${state.durationMillis} ms): ${state.message}",
                )
                loadTip = null
            }

            else -> loadTip = null
        }
    }

    // A barra de status diz o próximo passo do usuário (detecção vem primeiro).
    val statusText = when {
        message != null -> message!!
        loadTip != null -> loadTip!!
        !sessionReady -> stringResource(R.string.status_starting_camera)
        trackedLabels.isNotEmpty() && modelInstance == null ->
            stringResource(R.string.status_marker_tracking_no_model, trackedMarkersText)
        trackedLabels.isNotEmpty() ->
            stringResource(R.string.status_marker_tracking, trackedMarkersText)
        stagedModel == null -> stringResource(R.string.status_no_model)
        modelState is ModelLoadState.Loading -> stringResource(
            if (slowLoad) R.string.status_loading_model_slow else R.string.status_loading_model,
            stagedModel?.displayName ?: "",
        )
        modelState is ModelLoadState.Failed -> modelState.message
        else -> stringResource(R.string.status_scan_marker, selectedMarkerLabel)
    }

    // Mensagens informativas (carregamento, detecção) desaparecem sozinhas em
    // fade, liberando a visão da câmera; instruções permanecem visíveis.
    val transientStatus = message == null && (
        loadTip != null ||
            modelState is ModelLoadState.Failed ||
            trackedLabels.isNotEmpty()
        )
    val statusHoldMillis = when {
        loadTip != null -> LOAD_TIP_MILLIS
        modelState is ModelLoadState.Failed -> ERROR_MESSAGE_MILLIS
        else -> DETECTION_MESSAGE_MILLIS
    }
    var statusVisible by remember { mutableStateOf(true) }
    LaunchedEffect(statusText, transientStatus, statusHoldMillis) {
        statusVisible = true
        if (transientStatus) {
            delay(statusHoldMillis)
            statusVisible = false
        }
    }

    // ── Tamanho e alinhamento do modelo ─────────────────────────────────────
    // A bounding box do asset (lida uma vez por modelo) fornece as duas coisas:
    //
    // ● tamanho: `normalization` faz a maior dimensão medir 1 m; multiplicada pelo
    //   tamanho escolhido, o resultado é o tamanho REAL em metros.
    // ● alinhamento: `anchorPosition` devolve o deslocamento que apoia a base do
    //   modelo no marcador e o centraliza (mesma intenção do
    //   `centerOrigin = Position(0f, -1f, 0f)`) — calculado com a escala ATUAL,
    //   porque o `centerOrigin` do SceneView 4.38 é aplicado uma única vez no
    //   construtor e ficaria gravado em unidades cruas do arquivo.
    //
    // Por que não usar `scaleToUnits`? Na implementação do ModelNode o `scale`
    // reativo só é aplicado quando `scaleToUnits == null`
    // (`if (scaleToUnits == null) node.scale = scale`, em SceneScope.kt), ou
    // seja: passar `scaleToUnits` faz o slider/pinça de tamanho parecer "só valer
    // no próximo carregamento do modelo". Aqui o `scale` multiplica o tamanho
    // escolhido — 100% reativo.
    val modelMetrics = remember(modelInstance) {
        modelInstance?.let { computeModelMetrics(it) }
    }
    val modelNormalization = modelMetrics?.normalization ?: 1f
    val modelScale = modelNormalization * modelSizeMeters
    val modelPosition = modelMetrics?.anchorPosition(modelScale, modelElevationMeters)
        ?: Position(0f, modelElevationMeters, 0f)

    // Diagnóstico no Logcat (tag ArkZARModelViewer): um modelo "que carrega mas não
    // aparece" é escala errada ou posição longe do marcador — este log diz qual.
    LaunchedEffect(modelInstance, modelMetrics) {
        val metrics = modelMetrics ?: run {
            if (modelInstance != null) {
                Log.w(
                    AR_LOG_TAG,
                    "Bounding box indisponível para o modelo carregado: a escala fica " +
                        "igual ao slider de tamanho (sem normalização) e o centro do " +
                        "modelo vai para a origem do marcador.",
                )
            }
            return@LaunchedEffect
        }
        Log.i(
            AR_LOG_TAG,
            String.format(
                Locale.US,
                "Modelo ancorado: bounding box centro=(%.3f, %.3f, %.3f) " +
                    "meia-extensão=(%.3f, %.3f, %.3f) | normalização=%.6f " +
                    "escala=%.6f posição=(%.4f, %.4f, %.4f) elevação-Z=%.3f m",
                metrics.center.x, metrics.center.y, metrics.center.z,
                metrics.halfExtent.x, metrics.halfExtent.y, metrics.halfExtent.z,
                modelNormalization, modelScale,
                modelPosition.x, modelPosition.y, modelPosition.z,
                modelElevationMeters,
            ),
        )
    }

    // ── Captura de tela ─────────────────────────────────────────────────────
    // O SceneView espelha a cena (câmera + conteúdo 3D) em um buffer próprio
    // APENAS durante a captura, então o overlay do Compose não entra na imagem e
    // não há custo quando o botão não é usado.
    //
    // Uma captura por vez, disparada em `scope.launch` (e não em um
    // `LaunchedEffect` com contador): um segundo toque não pode CANCELAR a captura
    // em andamento, porque o espelhamento precisa ser desligado antes de a
    // superfície de destino ser liberada.
    var capturing by remember { mutableStateOf(false) }
    val requestCapture: () -> Unit = {
        if (capturing) {
            message = context.getString(R.string.status_capture_in_progress)
        } else {
            capturing = true
            scope.launch {
                try {
                    message = captureSceneToGallery(
                        context = context,
                        surfaceMirrorer = surfaceMirrorer,
                        viewWidth = arViewSize.width,
                        viewHeight = arViewSize.height,
                        fileName = "ArkZARModelViewer_${
                            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                        }.png",
                        folder = SCREENSHOT_GALLERY_FOLDER,
                        maxLongSide = captureMaxLongSide,
                    ).fold(
                        onSuccess = { path -> "Captura salva em $path" },
                        onFailure = { error ->
                            "Não foi possível capturar a tela: " +
                                (error.message ?: error.javaClass.simpleName)
                        },
                    )
                } finally {
                    capturing = false
                }
            }
        }
    }

    // ── Menu (três pontos): ajuda, sobre, contato e diagnóstico ──────────────
    // Todos os destinos são aplicativos externos; quando não há app instalado
    // (ex.: nenhum cliente de e-mail), a própria barra de status explica.
    val sendFeedback: () -> Unit = {
        message = context.sendEmail(
            to = DEVELOPER_EMAIL,
            subject = context.getString(R.string.feedback_subject),
            body = context.getString(
                R.string.feedback_body,
                context.appVersionLabel(),
                deviceLabel(),
                stagedModel?.displayName ?: context.getString(R.string.value_none),
            ),
        )
    }
    val rateOnPlayStore: () -> Unit = {
        message = context.openExternal(
            playStoreMarketUri(context.packageName),
            playStoreWebUri(context.packageName),
        )
    }
    val openDeveloperSite: () -> Unit = {
        message = context.openExternal(Uri.parse(DEVELOPER_SITE_URL))
    }
    // "Copiar diagnóstico": versão, aparelho, estado do modelo/marcador e a última
    // mensagem da barra de status. É o que permite investigar um problema sem pedir
    // prints de tela ao usuário.
    val copyDiagnostics: () -> Unit = {
        val none = context.getString(R.string.value_none)
        val text = buildString {
            appendLine(context.getString(R.string.diagnostics_title))
            appendLine(context.getString(R.string.about_version, context.appVersionLabel()))
            appendLine(deviceLabel())
            appendLine(
                context.getString(R.string.diagnostics_model, stagedModel?.displayName ?: none),
            )
            appendLine(context.getString(R.string.diagnostics_markers, markers.size))
            appendLine(context.getString(R.string.diagnostics_selected_marker, selectedMarkerLabel))
            appendLine(context.getString(R.string.diagnostics_status, statusText))
        }
        message = if (context.copyToClipboard(context.getString(R.string.app_name), text)) {
            context.getString(R.string.status_diagnostics_copied)
        } else {
            context.getString(R.string.status_diagnostics_failed)
        }
    }
    val shareAppAction: () -> Unit = {
        message = context.shareApp(
            chooserTitle = context.getString(R.string.menu_share_app),
            subject = context.getString(R.string.share_app_subject),
            text = context.getString(
                R.string.share_app_text,
                playStoreWebUri(context.packageName).toString(),
            ),
        )
    }
    // "Exportar folha de impressão": o marcador IMPRESSO, pronto para usar como
    // alvo. Precisa de um marcador específico escolhido — "Todos os marcadores" não
    // tem uma figura única para imprimir.
    val exportPrintSheet: () -> Unit = {
        val marker = markers.firstOrNull { it.id == selectedMarkerId }
        if (marker == null) {
            message = context.getString(R.string.status_print_sheet_needs_marker)
        } else {
            scope.launch {
                message = saveMarkerPrintSheetToGallery(
                    context = context,
                    marker = marker,
                    fileName = "${sanitizeFileName(marker.label)}_folha_impressao.png",
                    folder = MARKER_GALLERY_FOLDER,
                ).fold(
                    onSuccess = { path -> context.getString(R.string.print_sheet_saved, path) },
                    onFailure = { error ->
                        context.getString(
                            R.string.print_sheet_failed,
                            error.message ?: error.javaClass.simpleName,
                        )
                    },
                )
            }
        }
    }
    // Rótulo da qualidade atual, mostrado no próprio item do menu.
    val captureQualityText = captureQualityLabel(context, captureMaxLongSide)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        ARSceneView(
            modifier = Modifier
                .fillMaxSize()
                // Guarda o tamanho da view: é o destino do espelhamento na captura.
                .onSizeChanged { size -> arViewSize = size },
            // SurfaceView: melhor desempenho (o TextureSurface joga a composição
            // de cada quadro na thread de UI e deixa a RA pesada lenta). A captura
            // de tela usa o `surfaceMirrorer`, que funciona com SurfaceView.
            surfaceType = SurfaceType.Surface,
            // Custo zero enquanto nada está sendo capturado: o espelhamento só
            // renderiza a cena de novo entre startMirroring/stopMirroring.
            surfaceMirrorer = surfaceMirrorer,
            engine = engine,
            modelLoader = modelLoader,
            materialLoader = materialLoader,
            // Sem detecção/renderização de planos: tudo vem dos marcadores.
            planeRenderer = false,
            planeFindingMode = Config.PlaneFindingMode.DISABLED,
            focusMode = Config.FocusMode.AUTO,
            sessionConfiguration = { session, config ->
                // ★ O AugmentedImageDatabase é reaplicado a cada
                // (re)configuração da sessão — é o que faz o ARCore procurar
                // os marcadores no feed da câmera.
                arImageDatabase.applyTo(config, session)
            },
            onSessionCreated = { session ->
                // Vincula a sessão viva: permite registrar imagens em tempo
                // de execução (os marcadores personalizados).
                arImageDatabase.bind(session)
            },
            onSessionUpdated = { _, frame ->
                if (!sessionReady) sessionReady = true

                // getUpdatedTrackables devolve apenas os trackables
                // ATUALIZADOS NESTE FRAME (caminho barato).
                //
                // A condição é `trackingState == TRACKING` — exatamente a do
                // exemplo da própria biblioteca (`ARSceneScope.AugmentedImageNode`),
                // e NÃO o atalho `isTracking`: essa extensão só é `true` com
                // `FULL_TRACKING`, de modo que um marcador visto em
                // `LAST_KNOWN_POSE` (situação comum após o primeiro instante e em
                // ângulo rasante) nunca entrava na lista — o resultado era
                // "marcador sem conteúdo": nenhum nó, nenhum modelo, nenhuma caixa.
                frame.getUpdatedTrackables(AugmentedImage::class.java).forEach { image ->
                    if (image.trackingState == TrackingState.TRACKING) {
                        // O ARCore cria um NOVO objeto quando a imagem é
                        // re-detectada: guardar o objeto antigo (mesmo `index`)
                        // deixaria na cena um nó apontando para um trackable morto.
                        val existing = detectedImages.indexOfFirst { it.index == image.index }
                        if (existing >= 0) {
                            if (detectedImages[existing] !== image) detectedImages[existing] = image
                        } else {
                            detectedImages.add(image)
                            // Diagnóstico (Logcat): método de rastreio e largura
                            // física estimada pelo ARCore — explica um conteúdo que
                            // oscila (LAST_KNOWN_POSE) ou escala errada.
                            Log.i(
                                AR_LOG_TAG,
                                "Marcador reconhecido: ${image.name} " +
                                    "(estado=${image.trackingState}, método=${image.trackingMethod}, " +
                                    "largura=${image.extentX} m, altura=${image.extentZ} m)",
                            )
                        }
                    }
                }

                // Só saem da cena as imagens ENCERRADAS (`STOPPED` — o trackable
                // morreu). As `PAUSED`/`LAST_KNOWN_POSE` PERMANECEM: é o próprio
                // `AugmentedImageNode` que as esconde (o padrão
                // `visibleTrackingStates = { TRACKING }`), e assim o conteúdo volta
                // instantaneamente quando o marcador é revisto — sem destruir e
                // recriar nós a cada oscilação do rastreio (o que causava engasgos).
                //
                // A lista só é tocada quando existe algo a remover: escrever no
                // estado a cada frame recomporia a tela a 60 FPS.
                if (detectedImages.any { it.trackingState == TrackingState.STOPPED }) {
                    detectedImages.removeAll { it.trackingState == TrackingState.STOPPED }
                }
            },
        ) {
            // ── Renderização ancorada ao marcador ───────────────────────────
            // O AugmentedImageNode assume a cada frame a pose
            // `image.centerPose`, então o conteúdo filho acompanha o marcador.
            visibleImages.forEach { image ->
                // Configuração IDÊNTICA ao exemplo da própria biblioteca
                // (`ARSceneScope.AugmentedImageNode`): sem restringir
                // `visibleTrackingMethods`. Restringir a `FULL_TRACKING` deixava o
                // marcador "detectado" na barra de status e a cena VAZIA, porque o
                // ARCore reporta `LAST_KNOWN_POSE` em boa parte do tempo (marcador
                // visto de longe, de lado, com pouco contraste momentâneo).
                AugmentedImageNode(
                    augmentedImage = image,
                    applyImageScale = false,
                ) {
                    if (modelInstance != null) {
                        ModelNode(
                            modelInstance = modelInstance,
                            // `scale` é reativo (com `scaleToUnits` o slider de
                            // tamanho só valeria no próximo carregamento — veja
                            // `ModelMetrics`): a normalização faz a maior dimensão
                            // do modelo medir 1 m, então este valor É o tamanho em
                            // metros.
                            scale = Scale(modelScale),
                            rotation = Rotation(rotationX, rotationY, rotationZ),
                            // Alinhamento no marcador (centrado em X/Z, base em Y e
                            // elevado em Z) já multiplicado pela escala atual.
                            position = modelPosition,
                            // SEM animação automática: um visualizador estático não
                            // pode ter o modelo deslocado pela animação que o arquivo
                            // traz, e o `Animator` do Filament recalcula os nós a
                            // cada quadro (custo altíssimo nos modelos de CAD com
                            // milhares de nós).
                            autoAnimate = false,
                        )
                    } else {
                        // Marcador reconhecido sem modelo: caixa ciano que
                        // indica que a detecção está funcionando.
                        CubeNode(
                            size = Size(0.05f),
                            center = Position(0f, 0f, 0.025f),
                            materialInstance = placeholderMaterial,
                        )
                    }
                }
            }
        }

        // ── Gesto sobre o viewport de RA ────────────────────────────────────
        // Apenas PINÇA (zoom no tamanho do modelo). Pan e rotação com dois dedos
        // foram removidos de propósito: a orientação é ajustada pelos sliders do
        // painel de ajustes, que dão controle preciso e previsível.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        modelSizeMeters = (modelSizeMeters * zoom)
                            .coerceIn(MIN_MODEL_SIZE_METERS, MAX_MODEL_SIZE_METERS)
                    }
                },
        )

        // ── Overlay de interface ─────────────────────────────────────────────
        // `run { }` mantém o escopo do Box (para os `Modifier.align` abaixo).
        // A captura de tela espelha só a cena do Filament, então a interface não
        // precisa ser escondida durante a captura.
        run {
            // Menu "Idioma" + faixa de status. O menu (três pontos) fica no canto
            // superior ESQUERDO e a faixa de status à direita dele; a faixa
            // desaparece em fade e o botão permanece, discreto e translúcido como
            // os demais controles, para não cobrir a câmera.
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(top = 48.dp, start = 8.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Menu (três pontos) à ESQUERDA; a faixa de status fica à direita
                // dele, centralizada no espaço restante.
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.Black.copy(alpha = 0.45f),
                ) {
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = stringResource(R.string.menu_more),
                                tint = Color.White,
                            )
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_help)) },
                                onClick = {
                                    showOverflowMenu = false
                                    showHelpDialog = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_about)) },
                                onClick = {
                                    showOverflowMenu = false
                                    showAboutDialog = true
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(
                                            R.string.language_current,
                                            stringResource(appLanguageLabelRes(languageTag)),
                                        ),
                                    )
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    showLanguageDialog = true
                                },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_feedback_email)) },
                                onClick = {
                                    showOverflowMenu = false
                                    sendFeedback()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_rate_play)) },
                                onClick = {
                                    showOverflowMenu = false
                                    rateOnPlayStore()
                                },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_share_app)) },
                                onClick = {
                                    showOverflowMenu = false
                                    shareAppAction()
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(
                                            R.string.capture_quality_current,
                                            captureQualityText,
                                        ),
                                    )
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    showCaptureQualityDialog = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_print_sheet)) },
                                onClick = {
                                    showOverflowMenu = false
                                    exportPrintSheet()
                                },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_copy_diagnostics)) },
                                onClick = {
                                    showOverflowMenu = false
                                    copyDiagnostics()
                                },
                            )
                        }
                    }
                }
                AnimatedVisibility(
                    visible = statusVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.weight(1f),
                ) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        StatusPill(text = statusText)
                    }
                }
            }

            // Enquanto o arquivo do modelo é lido/convertido.
            if (modelState is ModelLoadState.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White,
                )
            }

            // Painel de ajustes: rotação em X/Y/Z e tamanho do modelo.
            if (showAdjustPanel) {
                ModelAdjustPanel(
                    rotationX = rotationX,
                    rotationY = rotationY,
                    rotationZ = rotationZ,
                    sizeMeters = modelSizeMeters,
                    elevationMeters = modelElevationMeters,
                    onRotationXChange = { rotationX = it },
                    onRotationYChange = { rotationY = it },
                    onRotationZChange = { rotationZ = it },
                    onSizeChange = { modelSizeMeters = it },
                    onElevationChange = { modelElevationMeters = it },
                    onReset = {
                        rotationX = DEFAULT_ROTATION_X
                        rotationY = 0f
                        rotationZ = 0f
                        modelSizeMeters = DEFAULT_MODEL_SIZE_METERS
                        modelElevationMeters = DEFAULT_ELEVATION_METERS
                    },
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 12.dp),
                )
            }

            // Barra de ferramentas flutuante (Row + IconButton).
            ControlBar(
                adjustPanelOpen = showAdjustPanel,
                onReset = {
                    // Descarta as detecções atuais e volta à orientação e ao
                    // tamanho padrão do modelo.
                    detectedImages.clear()
                    rotationX = DEFAULT_ROTATION_X
                    rotationY = 0f
                    rotationZ = 0f
                    modelSizeMeters = DEFAULT_MODEL_SIZE_METERS
                    modelElevationMeters = DEFAULT_ELEVATION_METERS
                    message = null
                },
                onChooseMarker = { showMarkerDialog = true },
                onAutoScale = {
                    if (modelInstance == null) {
                        message = context.getString(R.string.status_load_model_first)
                    } else {
                        // "Zoom extents": dimensiona o modelo pela largura do
                        // marcador DETECTADO (medida real informada pelo ARCore);
                        // sem marcador visível, usa a largura física configurada
                        // e, por fim, o padrão de 15 cm.
                        val targetMeters = visibleImages
                            .firstNotNullOfOrNull { image -> image.extentX.takeIf { it > 0.001f } }
                            ?: markers.firstOrNull { it.id == selectedMarkerId }
                                ?.physicalWidthMeters
                            ?: MarkerCatalog.DEFAULT_PHYSICAL_WIDTH_METERS

                        modelSizeMeters = targetMeters
                            .coerceIn(MIN_MODEL_SIZE_METERS, MAX_MODEL_SIZE_METERS)
                        message = context.getString(
                            R.string.status_auto_scale,
                            String.format(Locale.US, "%.2f", modelSizeMeters),
                        )
                    }
                },
                onToggleAdjust = { showAdjustPanel = !showAdjustPanel },
                onCapture = requestCapture,
                onExit = onExit,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 28.dp),
            )

            // FAB: carregar modelo 3D.
            FloatingActionButton(
                onClick = { showModelSourceDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 100.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Image,
                    contentDescription = stringResource(R.string.action_pick_model),
                )
            }

            // Diálogo "Gerenciar marcador": embutidos + personalizados.
            if (showMarkerDialog) {
                MarkerSelectionDialog(
                    selectedMarkerId = selectedMarkerId,
                    markers = markers,
                    onSelect = { id ->
                        selectedMarkerId = id
                        showMarkerDialog = false
                    },
                    onCreateMarker = {
                        showMarkerDialog = false
                        markerNameInput = ""
                        showCreateMarkerDialog = true
                    },
                    onExportMarker = { marker ->
                        // Salva o PNG do marcador na pasta de imagens do aparelho,
                        // para o usuário imprimir (qualquer marcador da lista).
                        scope.launch {
                            message = saveMarkerImageToGallery(context, marker).fold(
                                onSuccess = { path ->
                                    context.getString(R.string.status_marker_exported, path)
                                },
                                onFailure = { error ->
                                    "Não foi possível salvar a imagem: ${
                                        error.message ?: error.javaClass.simpleName
                                    }"
                                },
                            )
                        }
                    },
                    onRemoveCustomMarker = { marker ->
                        customMarkerStore.remove(marker.id)
                        if (selectedMarkerId == marker.id) {
                            selectedMarkerId = ALL_MARKERS_ID
                        }
                        scope.launch { reloadMarkers(context, customMarkerStore, markers) }
                    },
                    onDismiss = { showMarkerDialog = false },
                )
            }

            // Diálogo "Criar/Carregar marcador": o usuário nomeia e escolhe entre
            //   - "Criar marcador": o app DESENHA a figura com o nome informado
            //     ([MarkerGenerator]), salva como marcador e exporta o PNG para
            //     imprimir;
            //   - "Carregar marcador": usa uma imagem que já está no aparelho.
            if (showCreateMarkerDialog) {
                CreateOrLoadMarkerDialog(
                    name = markerNameInput,
                    onNameChange = { markerNameInput = it },
                    onCreateMarker = {
                        val name = markerNameInput.trim()
                        showCreateMarkerDialog = false
                        if (name.isEmpty()) {
                            message = context.getString(R.string.status_marker_name_required)
                        } else {
                            markerNameInput = ""
                            scope.launch {
                                // A geração da figura é desenhada FORA da thread
                                // principal (o `withContext` envolve também a
                                // chamada de [MarkerGenerator]).
                                val created = withContext(Dispatchers.IO) {
                                    customMarkerStore.addBitmap(
                                        bitmap = MarkerGenerator.generate(name),
                                        label = name,
                                    )
                                }
                                created
                                    .onSuccess { marker ->
                                        reloadMarkers(context, customMarkerStore, markers)
                                        selectedMarkerId = marker.id
                                        // O PNG exportado é o que o usuário imprime
                                        // e aponta a câmera.
                                        message = saveMarkerImageToGallery(context, marker).fold(
                                            onSuccess = { path ->
                                                context.getString(
                                                    R.string.status_marker_generated,
                                                    marker.label,
                                                    path,
                                                )
                                            },
                                            onFailure = { error ->
                                                context.getString(
                                                    R.string.status_marker_created_no_export,
                                                    marker.label,
                                                    error.message ?: error.javaClass.simpleName,
                                                )
                                            },
                                        )
                                    }
                                    .onFailure { error ->
                                        message = error.message
                                            ?: context.getString(R.string.status_marker_create_failed)
                                    }
                            }
                        }
                    },
                    onLoadMarker = {
                        pendingMarkerName = markerNameInput.trim()
                        markerNameInput = ""
                        showCreateMarkerDialog = false
                        pickMarkerImageLauncher.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    },
                    onDismiss = { showCreateMarkerDialog = false },
                )
            }

            // Diálogo de origem do modelo.
            if (showModelSourceDialog) {
                ModelSourceDialog(
                    onPickModelFile = {
                        showModelSourceDialog = false
                        pickModelLauncher.launch(ModelFileStaging.pickerMimeTypes)
                    },
                    onDismiss = { showModelSourceDialog = false },
                )
            }

            // Ajuda / Sobre — abertos pelo menu de três pontos.
            if (showHelpDialog) {
                HelpDialog(onDismiss = { showHelpDialog = false })
            }
            if (showAboutDialog) {
                AboutDialog(
                    onOpenSite = {
                        showAboutDialog = false
                        openDeveloperSite()
                    },
                    onSendEmail = {
                        showAboutDialog = false
                        sendFeedback()
                    },
                    onDismiss = { showAboutDialog = false },
                )
            }
            if (showCaptureQualityDialog) {
                CaptureQualityDialog(
                    currentMaxLongSide = captureMaxLongSide,
                    onSelect = { maxLongSide ->
                        captureMaxLongSide = maxLongSide
                        // Guarda a escolha: vale para as próximas capturas, mesmo
                        // depois de fechar o app.
                        preferences.captureMaxLongSide = maxLongSide
                        showCaptureQualityDialog = false
                    },
                    onDismiss = { showCaptureQualityDialog = false },
                )
            }
            if (showLanguageDialog) {
                LanguageDialog(
                    currentTag = languageTag,
                    onSelect = { tag ->
                        // O idioma vale para toda a UI na hora (sem recriar a tela de
                        // RA) e fica guardado para a próxima abertura do app.
                        onLanguageChange(tag)
                        showLanguageDialog = false
                    },
                    onDismiss = { showLanguageDialog = false },
                )
            }
        }
    }
}

/** Recarrega a lista de marcadores (assets + personalizados). */
private suspend fun reloadMarkers(
    context: Context,
    store: CustomMarkerStore,
    target: SnapshotStateList<MarkerDefinition>,
) {
    val loaded = withContext(Dispatchers.IO) {
        MarkerCatalog.loadBundled(context) + store.loadDefinitions()
    }
    target.clear()
    target.addAll(loaded)
}

/** Faixa semitransparente com a instrução/status atual. */
@Composable
private fun StatusPill(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = Color.Black.copy(alpha = 0.6f),
    ) {
        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

/**
 * Barra de ferramentas flutuante (Row + IconButton).
 *
 * @param adjustPanelOpen quando `true`, o painel de ajustes está aberto.
 */
@Composable
private fun ControlBar(
    adjustPanelOpen: Boolean,
    onReset: () -> Unit,
    onChooseMarker: () -> Unit,
    onAutoScale: () -> Unit,
    onToggleAdjust: () -> Unit,
    onCapture: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = Color.Black.copy(alpha = 0.55f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            IconButton(onClick = onReset) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.action_reset),
                    tint = Color.White,
                )
            }
            IconButton(onClick = onChooseMarker) {
                Icon(
                    imageVector = Icons.Filled.QrCode2,
                    contentDescription = stringResource(R.string.action_choose_marker),
                    tint = Color.White,
                )
            }
            IconButton(onClick = onToggleAdjust) {
                // Ajustes de rotação (X/Y/Z) e tamanho do modelo.
                Icon(
                    imageVector = Icons.Filled.Tune,
                    contentDescription = stringResource(R.string.action_adjust_model),
                    tint = if (adjustPanelOpen) AccentColor else Color.White,
                )
            }
            IconButton(onClick = onAutoScale) {
                // "Zoom extents": ajusta o tamanho do modelo à largura do
                // marcador detectado (ou ao tamanho configurado).
                Icon(
                    imageVector = Icons.Filled.ZoomOutMap,
                    contentDescription = stringResource(R.string.action_auto_scale),
                    tint = Color.White,
                )
            }
            IconButton(onClick = onCapture) {
                // Captura a vista atual (câmera + modelo) para a galeria.
                Icon(
                    imageVector = Icons.Filled.PhotoCamera,
                    contentDescription = stringResource(R.string.action_capture),
                    tint = Color.White,
                )
            }
            IconButton(onClick = onExit) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.action_exit),
                    tint = Color.White,
                )
            }
        }
    }
}

/** Cor de destaque da interface (ciano). */
private val AccentColor = Color(0xFF00E5FF)

/**
 * Painel flutuante de ajustes do modelo.
 *
 * Não é um diálogo modal: fica sobre a câmera para que o efeito de cada slider
 * seja visto em tempo real.
 */
@Composable
private fun ModelAdjustPanel(
    rotationX: Float,
    rotationY: Float,
    rotationZ: Float,
    sizeMeters: Float,
    elevationMeters: Float,
    onRotationXChange: (Float) -> Unit,
    onRotationYChange: (Float) -> Unit,
    onRotationZChange: (Float) -> Unit,
    onSizeChange: (Float) -> Unit,
    onElevationChange: (Float) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.width(230.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color.Black.copy(alpha = 0.65f),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                text = stringResource(R.string.adjust_title),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
            )
            AdjustSlider(
                label = stringResource(R.string.adjust_rotation_x),
                valueLabel = "${rotationX.toInt()}°",
                value = rotationX,
                onChange = onRotationXChange,
            )
            AdjustSlider(
                label = stringResource(R.string.adjust_rotation_y),
                valueLabel = "${rotationY.toInt()}°",
                value = rotationY,
                onChange = onRotationYChange,
            )
            AdjustSlider(
                label = stringResource(R.string.adjust_rotation_z),
                valueLabel = "${rotationZ.toInt()}°",
                value = rotationZ,
                onChange = onRotationZChange,
            )
            AdjustSlider(
                label = stringResource(R.string.adjust_size),
                valueLabel = String.format(Locale.US, "%.2f m", sizeMeters),
                value = sizeMeters,
                range = MIN_MODEL_SIZE_METERS..MAX_MODEL_SIZE_METERS,
                onChange = onSizeChange,
            )
            // "Elevação Z": desloca o modelo no sentido da NORMAL do marcador
            // (perpendicular ao plano da imagem impressa) — é o único eixo em que
            // o modelo sai do plano. Positivo levanta o modelo; negativo o afunda.
            AdjustSlider(
                label = stringResource(R.string.adjust_elevation_z),
                valueLabel = String.format(Locale.US, "%+.0f cm", elevationMeters * 100f),
                value = elevationMeters,
                range = MIN_ELEVATION_METERS..MAX_ELEVATION_METERS,
                steps = ((MAX_ELEVATION_METERS - MIN_ELEVATION_METERS) /
                    ELEVATION_STEP_METERS).toInt() - 1,
                onChange = onElevationChange,
            )
            TextButton(onClick = onReset, modifier = Modifier.align(Alignment.End)) {
                Text(text = stringResource(R.string.adjust_reset), color = AccentColor)
            }
        }
    }
}

/** Um slider do painel de ajustes, com rótulo e valor atual. */
@Composable
private fun AdjustSlider(
    label: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float> = -180f..180f,
    steps: Int = 0,
    onChange: (Float) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                text = valueLabel,
                color = AccentColor,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
        )
    }
}

/**
 * Métricas da bounding box do asset carregado, em **unidades do próprio arquivo**
 * (milímetros nos GLB exportados por CAD/OBJ, metros em muitos glTF modernos).
 *
 * Servem para duas coisas:
 *  1. normalizar o tamanho (`normalization` → a maior dimensão mede 1 m);
 *  2. apoiar o modelo no marcador (`anchorPosition`).
 *
 * **Por que não usar o parâmetro `centerOrigin` do `ModelNode`?**
 * No SceneView 4.38 o `centerOrigin` é aplicado UMA única vez, dentro do
 * construtor, e o deslocamento é `-(center + origin * halfExtent) * scale`
 * calculado com a escala que o nó tem NAQUELE instante — que ainda é `Scale(1f)`,
 * porque a escala declarativa do Compose só é aplicada depois
 * (`if (scaleToUnits == null) this.scale = scale`, em `SceneScope.kt`, dentro do
 * `remember` do nó). O deslocamento fica então gravado em unidades CRUAS do
 * arquivo: num modelo em milímetros com 10 m de lado isso empurra o modelo
 * centenas de metros para fora da cena — ele carrega, mas **nunca aparece**.
 *
 * Fazendo a conta aqui, multiplicada pela escala atual, o alinhamento acompanha o
 * slider de tamanho (e o pinça) e o modelo aparece exatamente sobre o marcador.
 */
private class ModelMetrics(
    /** Centro da bounding box (posição do pivô do asset, em unidades do arquivo). */
    val center: Position,
    /** Metade da extensão da bounding box em cada eixo. */
    val halfExtent: Position,
) {
    /** Maior dimensão da bounding box, em unidades do arquivo. */
    val largestDimension: Float
        get() = maxOf(halfExtent.x, halfExtent.y, halfExtent.z) * 2f

    /** Fator que faz a MAIOR dimensão medir 1 m (unidades do arquivo → metros). */
    val normalization: Float
        get() = if (largestDimension > 0.0001f) 1f / largestDimension else 1f

    /**
     * Deslocamento do nó para ancorar o modelo no marcador.
     *
     * **Atenção ao referencial do ARCore para imagens** (`AugmentedImage.getCenterPose`):
     * pelos nomes oficiais das extensões — `extentX` é a largura "measured along the
     * local X-axis" e `extentZ` é a **altura** "measured along the local Z-axis" — o
     * plano da imagem usa X = largura, Z = altura NA imagem e **Y = a normal
     * (perpendicular, saindo do plano do marcador)**. É diferente do referencial de
     * `Plane`, em que o "up" do plano horizontal é o próprio Y.
     *
     * Logo:
     *  - `x = -center.x * scale` → centraliza na largura da imagem;
     *  - `z = -center.z * scale` → centraliza na altura da imagem;
     *  - `y = -(center.y - halfExtent.y) * scale + elevationMeters` → apoia a BASE
     *    do modelo no plano do marcador e soma a **elevação** ("Elevação Z" na
     *    interface — o nome do controle; no referencial do ARCore, é o eixo Y).
     *    É este o único eixo em que o modelo sai do plano.
     *
     * Tudo multiplicado pela escala atual, de modo que o alinhamento acompanha o
     * slider de tamanho e a pinça.
     */
    fun anchorPosition(scale: Float, elevationMeters: Float): Position = Position(
        x = -center.x * scale,
        y = -(center.y - halfExtent.y) * scale + elevationMeters,
        z = -center.z * scale,
    )
}

/**
 * Lê a bounding box do asset glTF já carregado (`Model.boundingBox`, calculada a
 * partir dos acessadores do arquivo). Devolve `null` quando ela não está
 * disponível — nesse caso o modelo é exibido sem normalização e sem alinhamento.
 */
private fun computeModelMetrics(modelInstance: ModelInstance): ModelMetrics? =
    runCatching {
        val box = modelInstance.model.boundingBox
        ModelMetrics(
            center = Position(box.center[0], box.center[1], box.center[2]),
            halfExtent = Position(box.halfExtent[0], box.halfExtent[1], box.halfExtent[2]),
        )
    }.getOrNull()

/**
 * Salva a imagem de um marcador como PNG em `Pictures/ArkZ ARModelViewer/Marcadores`,
 * para o usuário imprimir e reutilizar em outros projetos.
 */
private suspend fun saveMarkerImageToGallery(
    context: Context,
    marker: MarkerDefinition,
): Result<String> = saveBitmapToGallery(
    context = context,
    bitmap = marker.bitmap,
    fileName = "${sanitizeFileName(marker.label)}.png",
    folder = MARKER_GALLERY_FOLDER,
)

/** Deixa o rótulo seguro para virar nome de arquivo. */
private fun sanitizeFileName(label: String): String = label
    .trim()
    .replace(Regex("[\\\\/:*?\"<>|]"), "_")
    .replace(Regex("\\s+"), "_")
    .take(40)
    .ifBlank { "marcador" }

/**
 * Mantém apenas UMA imagem detectada por figura física.
 *
 * Duas entradas do `AugmentedImageDatabase` podem casar com a mesma imagem
 * física (por exemplo um marcador criado pelo usuário a partir de uma figura
 * igual à de um marcador embutido). Nesse caso o ARCore reporta dois
 * `AugmentedImage` com índices diferentes na MESMA pose e o conteúdo seria
 * desenhado duas vezes, exatamente um sobre o outro. Aqui a segunda é descartada
 * comparando a translação da pose.
 */
private fun List<AugmentedImage>.distinctByPose(
    toleranceMeters: Float = 0.03f,
): List<AugmentedImage> {
    val kept = mutableListOf<AugmentedImage>()
    for (image in this) {
        val pose = runCatching { image.centerPose }.getOrNull() ?: continue
        val duplicated = kept.any { existing ->
            val existingPose = runCatching { existing.centerPose }.getOrNull() ?: return@any false
            val dx = pose.tx() - existingPose.tx()
            val dy = pose.ty() - existingPose.ty()
            val dz = pose.tz() - existingPose.tz()
            dx * dx + dy * dy + dz * dz < toleranceMeters * toleranceMeters
        }
        if (!duplicated) kept.add(image)
    }
    return kept
}

/** Subpasta de `Pictures` onde os marcadores são exportados para impressão. */
private const val MARKER_GALLERY_FOLDER = "ArkZ ARModelViewer/Marcadores"

/** Subpasta de `Pictures` onde as capturas de tela são salvas. */
private const val SCREENSHOT_GALLERY_FOLDER = "ArkZ ARModelViewer"

/**
 * Diálogo para escolher qual marcador controla a exibição do modelo.
 *
 * Lista os marcadores embutidos e os personalizados (imagens do usuário) e
 * permite adicionar/remover marcadores personalizados. As miniaturas mostram a
 * própria imagem de referência — sem imprimir nada, dá para exibir o diálogo em
 * outro aparelho e apontar a câmera para a tela.
 */
@Composable
private fun MarkerSelectionDialog(
    selectedMarkerId: String,
    markers: List<MarkerDefinition>,
    onSelect: (String) -> Unit,
    onCreateMarker: () -> Unit,
    onExportMarker: (MarkerDefinition) -> Unit,
    onRemoveCustomMarker: (MarkerDefinition) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.marker_dialog_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.marker_dialog_hint),
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                MarkerOptionRow(
                    selected = selectedMarkerId == ALL_MARKERS_ID,
                    label = stringResource(R.string.marker_all),
                    marker = null,
                    onClick = { onSelect(ALL_MARKERS_ID) },
                    onExport = null,
                )
                markers.forEach { marker ->
                    MarkerOptionRow(
                        selected = selectedMarkerId == marker.id,
                        label = marker.label,
                        marker = marker,
                        onClick = { onSelect(marker.id) },
                        onExport = { onExportMarker(marker) },
                        onRemove = if (marker.isCustom) {
                            { onRemoveCustomMarker(marker) }
                        } else {
                            null
                        },
                    )
                }
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = onCreateMarker,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Filled.QrCode2,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.marker_create),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        },
    )
}

/** Uma linha do diálogo de marcadores: rádio + miniatura + rótulo + ações. */
@Composable
private fun MarkerOptionRow(
    selected: Boolean,
    label: String,
    marker: MarkerDefinition?,
    onClick: () -> Unit,
    onExport: (() -> Unit)?,
    onRemove: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        marker?.let { definition ->
            Image(
                bitmap = definition.bitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.marker_preview_description),
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.White, RoundedCornerShape(4.dp))
                    .padding(2.dp),
            )
            Spacer(Modifier.width(12.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        if (onExport != null) {
            IconButton(onClick = onExport) {
                Icon(
                    imageVector = Icons.Filled.Save,
                    contentDescription = stringResource(R.string.marker_export),
                )
            }
        }
        if (onRemove != null) {
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.marker_remove),
                )
            }
        }
    }
}

/**
 * "Criar/Carregar marcador": dá um nome à figura e oferece os dois caminhos.
 *
 *  - **"Criar marcador"**: o app desenha uma figura NOVA com o nome informado
 *    ([MarkerGenerator] — pseudo-QR determinístico), salva como marcador
 *    personalizado e exporta o PNG em `Pictures/ArkZ ARModelViewer/Marcadores`.
 *    É essa folha impressa (15 cm de largura) que o usuário aponta para a
 *    câmera.
 *  - **"Carregar marcador"**: abre o seletor de imagens do aparelho para usar
 *    uma figura que já existe (uma foto, um print, um QR code baixado...). Sem
 *    nome digitado, o rótulo vem do nome do próprio arquivo.
 */
@Composable
private fun CreateOrLoadMarkerDialog(
    name: String,
    onNameChange: (String) -> Unit,
    onCreateMarker: () -> Unit,
    onLoadMarker: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.marker_create_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.marker_create_hint),
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    singleLine = true,
                    label = { Text(stringResource(R.string.marker_create_name_label)) },
                    placeholder = { Text(stringResource(R.string.marker_create_name_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                // Os dois botões ficam no corpo do diálogo (largura total): os
                // rótulos são longos e, na linha de botões do AlertDialog, ficariam
                // apertados em telas estreitas.
                Button(
                    onClick = onCreateMarker,
                    enabled = name.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Filled.QrCode2,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.marker_create_confirm),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onLoadMarker,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Image,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.marker_load_confirm),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.model_dialog_cancel))
            }
        },
    )
}

/**
 * Explica os formatos suportados e abre o seletor de arquivos do sistema.
 *
 * O seletor é aberto **sem filtro de tipo MIME**: o Android não conhece os tipos
 * de `.glb`, `.obj` e `.ply` (chegam como binário genérico) e um filtro por MIME
 * deixaria esses arquivos visíveis, porém impossíveis de selecionar. A validação
 * do formato acontece por **extensão** logo após a escolha, com uma mensagem
 * explicando o motivo quando não serve.
 */
@Composable
private fun ModelSourceDialog(
    onPickModelFile: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.model_dialog_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.model_dialog_formats),
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.model_dialog_hint),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onPickModelFile) {
                Text(stringResource(R.string.model_dialog_pick))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.model_dialog_cancel))
            }
        },
    )
}

/**
 * "Ajuda": como usar o aplicativo, passo a passo.
 *
 * O texto é longo para a altura de um celular, então o corpo é rolável — assim o
 * diálogo continua legível em telas pequenas e em pé/landscape.
 */
@Composable
private fun HelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.menu_help)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = stringResource(R.string.help_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        },
    )
}

/**
 * "Sobre": quem desenvolve o app, site, e-mail de contato e a versão instalada.
 *
 * Os dois atalhos do rodapé abrem o **site** e o **cliente de e-mail** — o usuário
 * não precisa copiar os endereços da tela.
 */
@Composable
private fun AboutDialog(
    onOpenSite: () -> Unit,
    onSendEmail: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.menu_about)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.about_description),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.about_developer),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(R.string.about_site),
                    color = AccentColor,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = stringResource(R.string.about_email),
                    color = AccentColor,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(
                        R.string.about_version,
                        context.appVersionLabel(),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onOpenSite) {
                    Text(stringResource(R.string.about_open_site))
                }
                TextButton(onClick = onSendEmail) {
                    Text(stringResource(R.string.about_send_email))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        },
    )
}

/** Rótulo da opção de qualidade da captura (depende do idioma do aparelho). */
private fun captureQualityLabel(context: Context, maxLongSide: Int?): String = when (maxLongSide) {
    null -> context.getString(R.string.capture_quality_max)
    CAPTURE_QUALITY_STANDARD -> context.getString(R.string.capture_quality_standard)
    else -> context.getString(R.string.capture_quality_high)
}

/**
 * "Qualidade da captura": escolhe o lado maior da imagem capturada.
 *
 * A escolha fica nas preferências e vale para as próximas capturas. Vale saber o
 * motivo das opções: o espelhamento desenha a cena **de novo** em um buffer, e o
 * quadro ainda vira um Bitmap — em tela cheia isso são dezenas de megabytes, o que
 * pode falhar em aparelhos com pouca memória livre.
 */
@Composable
private fun CaptureQualityDialog(
    currentMaxLongSide: Int?,
    onSelect: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.capture_quality_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.capture_quality_hint),
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                CAPTURE_QUALITY_OPTIONS.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = currentMaxLongSide == option,
                            onClick = { onSelect(option) },
                        )
                        Text(
                            text = captureQualityLabel(context, option),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.model_dialog_cancel))
            }
        },
    )
}

/**
 * "Idioma": força o idioma do aplicativo (ou volta a seguir o do aparelho).
 *
 * Os nomes aparecem no **próprio idioma** (English, Español, 中文…): é assim que
 * o usuário reconhece a opção, mesmo quando o app está num idioma que ele não lê.
 * O idioma escolhido é aplicado por [AppLocaleProvider], sem recriar a Activity —
 * a cena de RA continua intacta.
 */
@Composable
private fun LanguageDialog(
    currentTag: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.language_dialog_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = stringResource(R.string.language_dialog_hint),
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                appLanguages.forEach { language ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(language.tag) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = currentTag == language.tag,
                            onClick = { onSelect(language.tag) },
                        )
                        Text(
                            text = stringResource(language.labelRes),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.model_dialog_cancel))
            }
        },
    )
}
