package com.arkz.armodelviewer.model

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import io.github.sceneview.core.obj.ObjLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Tag do Logcat usada pelo app (`adb logcat -s ArkZARModelViewer`). */
internal const val AR_LOG_TAG = "ArkZARModelViewer"

/**
 * Um modelo já copiado para o armazenamento privado do app.
 *
 * @property file Arquivo local no cache do app.
 * @property displayName Nome original do arquivo (mostrado na interface).
 */
data class StagedModel(
    val file: File,
    val displayName: String,
) {
    /**
     * Localização aceita por `rememberModelInstance(modelLoader, location)`.
     *
     * Usamos uma URI `file://` (e não a `content://` original) porque a
     * permissão de leitura concedida pelo seletor de arquivos expira quando a
     * Activity é recriada/reiniciada; a cópia no cache é estável.
     */
    val location: String get() = Uri.fromFile(file).toString()
}

/**
 * Copia para o armazenamento privado do app um modelo escolhido pelo usuário via
 * `ACTION_OPEN_DOCUMENT` (Storage Access Framework).
 *
 * O SceneView carrega arquivos glTF/GLB/OBJ/STL/PLY/3MF diretamente de `assets`,
 * `file://`, `content://` e `https://`; ainda assim a cópia é feita para manter o
 * modelo disponível durante toda a sessão de RA (a URI `content://` de uma seleção
 * pontual pode perder a permissão de leitura) e porque a **localização do arquivo
 * é a chave que dispara o carregamento** — cada escolha gera um nome único.
 *
 * Observação: `.gltf` pode referenciar arquivos externos (`.bin`, texturas).
 * Prefira `.glb` (binário auto-contido) — um `.gltf` isolado carrega, mas sem
 * seus recursos externos.
 */
object ModelFileStaging {

    /**
     * Extensões aceitas. Todas são carregadas nativamente pelo SceneView 4.x:
     * glTF/GLB (`.glb`/`.gltf`), OBJ (`.obj`), STL (`.stl`), PLY (`.ply`) e
     * 3MF (`.3mf`).
     *
     * `.dae` (COLLADA) **não** é suportado: o Filament (renderizador do
     * SceneView) só lê glTF/GLB, e os conversores incluídos na biblioteca cobrem
     * apenas OBJ/STL/PLY/3MF. Converta o `.dae` para `.glb` (Blender:
     * Arquivo ▸ Exportar ▸ glTF 2.0) — veja [unsupportedFormatMessage].
     */
    val supportedExtensions: Set<String> = setOf("glb", "gltf", "obj", "stl", "ply", "3mf")

    /**
     * Tipos MIME usados no seletor de arquivos.
     *
     * É o filtro "todos os tipos" de propósito: o `ACTION_OPEN_DOCUMENT` filtra
     * por tipo MIME, e o Android **não** reconhece os tipos de `.glb`, `.obj` e
     * `.ply` (chegam como `application/octet-stream`). Com um filtro por MIME
     * esses arquivos apareceriam esmaecidos e **não selecionáveis** — na prática
     * só `.stl` podia ser escolhido. Sem filtro, a validação é feita por
     * **extensão** em [stage].
     */
    val pickerMimeTypes: Array<String> = arrayOf("*/*")

    /** Mensagem de ajuda para formatos que o SceneView não carrega. */
    fun unsupportedFormatMessage(extension: String): String = when (extension.lowercase()) {
        "dae" ->
            "O formato .dae (COLLADA) não é suportado pelo SceneView/Filament. " +
                "Converta o arquivo para .glb (Blender: Arquivo ▸ Exportar ▸ glTF 2.0) " +
                "e carregue o .glb."
        "fbx" ->
            "O formato .fbx não é suportado. Exporte como .glb (glTF 2.0)."
        else ->
            "Formato \".$extension\" não suportado. Use .glb, .gltf, .obj, .stl, .ply ou .3mf."
    }

    /**
     * Copia o arquivo apontado por [uri] para o cache do app.
     *
     * Executa a I/O fora da thread principal. O resultado é um [Result] para que
     * a camada de UI possa exibir mensagens específicas (formato inválido,
     * arquivo ilegível, etc.).
     */
    suspend fun stage(context: Context, uri: Uri): Result<StagedModel> =
        withContext(Dispatchers.IO) {
            runCatching {
                val displayName = queryDisplayName(context, uri)
                val extension = displayName.substringAfterLast('.', "").lowercase()
                require(extension in supportedExtensions) {
                    unsupportedFormatMessage(extension.ifEmpty { "desconhecido" })
                }

                val directory = directory(context)
                // Nome ÚNICO por escolha — e não "opened_model.glb" fixo. A
                // localização do arquivo é a chave observada pelo carregamento:
                // com o mesmo caminho para todos os `.glb`, escolher um segundo
                // modelo não disparava recarregamento nenhum e o primeiro
                // continuava na cena.
                val target = File(directory, "modelo_${System.currentTimeMillis()}.$extension")

                val input = context.contentResolver.openInputStream(uri)
                    ?: error("Não foi possível ler o arquivo selecionado.")
                input.use { source ->
                    target.outputStream().use { output -> source.copyTo(output) }
                }

                // `.obj` que o SceneView não reconhece sozinho precisa ser
                // convertido aqui — veja [convertObjWhenSniffFails].
                val loadable = if (extension == "obj") convertObjWhenSniffFails(target) else target
                Log.i(
                    AR_LOG_TAG,
                    "Arquivo preparado: ${displayName} -> ${loadable.name} " +
                        "(${loadable.length()} bytes)",
                )

                StagedModel(file = loadable, displayName = displayName)
            }
        }

    /**
     * Converte o `.obj` para `.glb` QUANDO o SceneView não conseguiria lê-lo.
     *
     * O `ModelLoader` do SceneView decide o formato "farejando" os primeiros 4 KB
     * do arquivo (`ObjLoader.isObj`) e, se não reconhecer OBJ ali, entrega os
     * bytes crus ao Filament como se fossem glTF — que falha e o modelo **não
     * abre**, com a mensagem "não foi possível ler o modelo".
     *
     * Só que praticamente todo OBJ exportado por Blender/SketchUp lista TODOS os
     * vértices antes da primeira face `f`, e essa lista passa fácil dos 4 KB
     * (≈120 vértices). O próprio KDoc de `ObjLoader.isObj` diz o que fazer:
     * *"Large files whose first face falls outside the prefix require explicit
     * conversion"* — e é exatamente o que fazemos aqui.
     *
     * A conversão é a mesma que o SceneView faria: unidades em milímetros e
     * material cinza opaco (o SceneView 4.38 ainda não aplica as texturas do
     * `.mtl`).
     *
     * Se o arquivo já é reconhecido ou se a conversão falhar (por exemplo um
     * arquivo que não é OBJ de verdade), devolvemos o original e deixamos o
     * SceneView reportar o erro.
     */
    private fun convertObjWhenSniffFails(file: File): File = runCatching {
        val bytes = file.readBytes()
        if (ObjLoader.isObj(bytes)) return@runCatching file

        val glb = ObjLoader.toGlb(bytes)
        File(file.parentFile, "${file.nameWithoutExtension}.glb").apply {
            writeBytes(glb)
        }.also {
            Log.i(
                AR_LOG_TAG,
                "OBJ convertido para GLB (o sniffing do SceneView não reconhecia " +
                    "'${file.name}' como OBJ): ${it.name} (${it.length()} bytes)",
            )
        }
    }.getOrElse { error ->
        Log.w(
            AR_LOG_TAG,
            "Falha ao converter o OBJ '${file.name}' para GLB: " +
                "${error.message ?: error.javaClass.simpleName}",
        )
        file
    }

    /** Lê o nome original do arquivo (coluna `OpenableColumns.DISPLAY_NAME`). */
    private fun queryDisplayName(context: Context, uri: Uri): String {
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) {
                    cursor.getString(index)?.let { return it }
                }
            }
        return uri.lastPathSegment ?: "modelo.glb"
    }

    private const val MODELS_DIRECTORY = "opened_models"

    /**
     * Pasta privada do app com as cópias dos modelos.
     *
     * É `filesDir` e **não** `cacheDir`: o Android pode apagar arquivos de cache
     * a qualquer momento sob pressão de armazenamento — inclusive no meio de um
     * carregamento, o que fazia o modelo "às vezes" não carregar.
     */
    private fun directory(context: Context): File =
        File(context.filesDir, MODELS_DIRECTORY).apply { mkdirs() }

    /**
     * Apaga as cópias antigas dos modelos.
     *
     * Chamado **uma vez**, ao abrir a tela — e nunca durante uma cópia, para não
     * remover o arquivo de um carregamento em andamento.
     */
    fun cleanup(context: Context) {
        runCatching {
            directory(context).listFiles()?.forEach { file -> file.delete() }
        }
    }
}
