package com.arkz.armodelviewer.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Grava um PNG na galeria do aparelho.
 *
 * - Android 10+ (`MediaStore`): a imagem aparece na galeria/pasta de imagens, sem
 *   precisar de permissão de armazenamento.
 * - Android 7–9: pasta privada do app (`Pictures/<folder>` no armazenamento
 *   externo do app), acessível por gerenciador de arquivos/USB.
 *
 * @param folder subpasta de `Pictures` (ex.: `ArkZ ARModelViewer` ou
 *   `ArkZ ARModelViewer/Marcadores`).
 * @return o caminho legível do arquivo salvo.
 */
suspend fun saveBitmapToGallery(
    context: Context,
    bitmap: Bitmap,
    fileName: String,
    folder: String,
): Result<String> = withContext(Dispatchers.IO) {
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/$folder",
                )
            }
            val uri: Uri = context.contentResolver
                .insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("Não foi possível criar a imagem na galeria.")

            context.contentResolver.openOutputStream(uri)?.use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            } ?: error("Não foi possível gravar a imagem na galeria.")

            "${Environment.DIRECTORY_PICTURES}/$folder/$fileName"
        } else {
            val directory = File(
                context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                folder,
            ).apply { mkdirs() }
            val file = File(directory, fileName)
            file.outputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
            file.absolutePath
        }
    }
}
