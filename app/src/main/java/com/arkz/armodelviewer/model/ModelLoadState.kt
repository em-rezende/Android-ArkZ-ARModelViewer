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

package com.arkz.armodelviewer.model

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.model.ModelInstance
import io.github.sceneview.math.halfExtentSize
// `model` é uma extension property declarada no pacote io.github.sceneview.model
// (`val ModelInstance.model get() = asset`): sem este import o compilador não a
// encontra — `ModelInstance` é apenas um typealias de FilamentInstance.
import io.github.sceneview.model.model
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull

/** Resultado do carregamento de um arquivo de modelo escolhido pelo usuário. */
sealed interface ModelLoadState {

    /** Nenhum arquivo escolhido ainda. */
    data object Idle : ModelLoadState

    /** Lendo/convertendo o arquivo (a UI mostra o indicador de progresso). */
    data object Loading : ModelLoadState

    /** Pronto para ser renderizado na cena. */
    data class Loaded(
        val instance: ModelInstance,
        /** Quanto tempo o carregamento levou (diagnóstico na interface). */
        val durationMillis: Long,
        /**
         * Maior dimensão medida na bounding box do arquivo, em **unidades do
         * próprio arquivo** (0 quando o asset não informa dimensões).
         *
         * O glTF define metros, mas exportadores de CAD/SketchUp e a conversão de
         * OBJ do SceneView gravam **milímetros** — por isso o número exibido na
         * barra de status pode ser "estranho" (ex.: 870 para um modelo de mesa).
         * É exatamente esse valor que explica um modelo que carrega e não aparece:
         * o app normaliza a maior dimensão para 1 m antes de aplicar o tamanho
         * escolhido, então a unidade do arquivo não afeta o resultado — a não ser
         * que o deslocamento de ancoragem seja calculado sem essa escala.
         */
        val largestDimensionUnits: Float,
    ) : ModelLoadState

    /** Falhou — a [message] é exibida ao usuário. */
    data class Failed(
        val message: String,
        val durationMillis: Long,
    ) : ModelLoadState
}

/**
 * Carrega um modelo a partir de [fileLocation] e expõe o estado do carregamento.
 *
 * **Por que não usar `rememberModelInstance`?**
 * `rememberModelInstance(modelLoader, path)` tem duas sobrecargas com a mesma
 * assinatura de 2 parâmetros e o compilador escolhe a versão de **assets**
 * (`context.assets.readBuffer(path)`). Para um arquivo escolhido pelo usuário
 * (URI `file://` ou `content://`) essa sobrecarga falha **silenciosamente**
 * (devolve `null` para sempre), deixando a interface presa em "Carregando
 * modelo…". Aqui chamamos explicitamente [ModelLoader.loadModelInstance], que
 * resolve assets, `file://`, `content://`, `android.resource://` e `http(s)://`
 * — e reportamos o erro em vez de escondê-lo.
 *
 * **Robustez:** cancelamentos vindos de dentro da biblioteca são tratados (e
 * reportados quando o carregamento ainda está ativo), de modo que um arquivo
 * problemático nunca deixa a tela presa no indicador de progresso. **Não** há
 * limite curto de tempo: a criação do asset roda na thread principal e cancelar
 * no meio DESTRUIRIA um modelo que ainda ia concluir — veja
 * [LOAD_WATCHDOG_MILLIS].
 *
 * O [ModelInstance] anterior é destruído (recursos de GPU liberados) quando o
 * arquivo muda ou quando a composição sai de cena.
 *
 * @param fileLocation localização do arquivo (ex.: `file:///data/.../modelo.glb`)
 *   ou `null` quando nenhum arquivo foi escolhido.
 */
@Composable
fun rememberModelLoadState(
    modelLoader: ModelLoader,
    fileLocation: String?,
): ModelLoadState {
    val state by produceState<ModelLoadState>(
        initialValue = ModelLoadState.Idle,
        modelLoader,
        fileLocation,
    ) {
        if (fileLocation == null) {
            value = ModelLoadState.Idle
            return@produceState
        }

        value = ModelLoadState.Loading
        val startedAt = SystemClock.elapsedRealtime()
        value = try {
            // NÃO use um timeout curto aqui.
            //
            // A criação do asset do glTF acontece na THREAD PRINCIPAL — é um
            // contrato do Filament imposto pela própria biblioteca
            // (`createOrDestroyOnCancel` roda em `Dispatchers.Main`, e o KDoc do
            // ModelLoader avisa que chamar `createModel*` fora da main é
            // inseguro). Consequência: um modelo grande bloqueia a thread de UI
            // por dezenas de segundos — e cancelar nesse intervalo DESTRÓI o
            // modelo que ainda ia concluir. Era exatamente isso que fazia
            // "os .glb/.obj não abrirem" enquanto um STL pequeno abria.
            //
            // O limite abaixo é só uma rede de segurança contra travamento real.
            var loadedInstance: ModelInstance? = null
            val finished = withTimeoutOrNull(LOAD_WATCHDOG_MILLIS) {
                loadedInstance = modelLoader.loadModelInstance(fileLocation)
            } != null
            val elapsed = SystemClock.elapsedRealtime() - startedAt

            val instance = loadedInstance
            when {
                !finished -> ModelLoadState.Failed(
                    "O carregamento passou de ${LOAD_WATCHDOG_MILLIS / 60_000} min e foi " +
                        "interrompido. O arquivo é grande demais para este aparelho.",
                    elapsed,
                )

                instance == null -> ModelLoadState.Failed(
                    "Não foi possível ler o modelo. Verifique se o arquivo não está " +
                        "corrompido e se o formato é suportado (.glb, .gltf, .obj, .stl, " +
                        ".ply, .3mf).",
                    elapsed,
                )

                else -> ModelLoadState.Loaded(
                    instance = instance,
                    durationMillis = elapsed,
                    largestDimensionUnits = instance.largestDimensionUnits(),
                )
            }
        } catch (cancellation: CancellationException) {
            // Se a coroutine AINDA está ativa, o cancelamento veio de dentro do
            // carregamento (e não de uma troca de arquivo/saída de cena): nesse
            // caso reportamos, em vez de deixar "Carregando…" eternamente.
            val elapsed = SystemClock.elapsedRealtime() - startedAt
            if (currentCoroutineContext().isActive) {
                ModelLoadState.Failed(
                    "O carregamento foi interrompido. Escolha o arquivo novamente.",
                    elapsed,
                )
            } else {
                throw cancellation
            }
        } catch (error: Throwable) {
            val elapsed = SystemClock.elapsedRealtime() - startedAt
            ModelLoadState.Failed(
                error.message ?: "Falha ao carregar o modelo (${error.javaClass.simpleName}).",
                elapsed,
            )
        }
    }

    // `produceState` não descarta o valor produzido anteriormente: sem isto o
    // Model do arquivo anterior continuaria ocupando memória de GPU (e, com dois
    // modelos grandes ao mesmo tempo, o carregamento do novo ficaria lento).
    val loadedInstance = (state as? ModelLoadState.Loaded)?.instance
    DisposableEffect(loadedInstance) {
        onDispose { loadedInstance?.let { modelLoader.destroyModel(it.model) } }
    }

    return state
}

/**
 * Maior dimensão da bounding box do asset carregado, em **unidades do próprio
 * arquivo** (metros no glTF, milímetros nos arquivos vindos de CAD/SketchUp).
 *
 * Lida da geometria já criada no Filament (`Model.boundingBox`); devolve 0 quando
 * o asset não informa dimensões.
 */
private fun ModelInstance.largestDimensionUnits(): Float {
    val halfExtent = runCatching { model.boundingBox.halfExtentSize }.getOrNull() ?: return 0f
    return maxOf(halfExtent.x, halfExtent.y, halfExtent.z) * 2f
}

/**
 * Rede de segurança contra travamento real: só interrompe carregamentos
 * absurdamente longos (5 minutos). Veja a nota em [rememberModelLoadState] sobre
 * por que um timeout curto é prejudicial.
 */
private const val LOAD_WATCHDOG_MILLIS = 5L * 60L * 1_000L
