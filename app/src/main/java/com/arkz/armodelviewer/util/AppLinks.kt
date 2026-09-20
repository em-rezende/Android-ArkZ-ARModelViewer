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

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat

/** Site do desenvolvedor (menu → "Sobre" e "Enviar feedback"). */
const val DEVELOPER_SITE_URL = "https://em-rezende.github.io/"

/** E-mail de contato do desenvolvedor. */
const val DEVELOPER_EMAIL = "emrezende@gmail.com"

/** Endereço do app na Google Play (usado em "Avaliar no Google Play"). */
fun playStoreWebUri(packageName: String): Uri =
    Uri.parse("https://play.google.com/store/apps/details?id=$packageName")

/** Endereço do app no app da Play Store (com queda para a versão web). */
fun playStoreMarketUri(packageName: String): Uri =
    Uri.parse("market://details?id=$packageName")

/**
 * Abre [uri] em outro aplicativo (navegador, e-mail, Play Store).
 *
 * @param fallbackUri alternativa quando não há app para o esquema principal —
 *   ex.: a Play Store não instalada/no emulador, em que `market://` falha e a
 *   página web funciona.
 * @return `null` quando conseguiu abrir; caso contrário, a mensagem para a barra
 *   de status (a interface não tem como mostrar uma exceção de Intent).
 */
fun Context.openExternal(uri: Uri, fallbackUri: Uri? = null): String? = try {
    startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    null
} catch (notFound: ActivityNotFoundException) {
    fallbackUri?.let { fallback -> openExternal(fallback) }
        ?: "Nenhum aplicativo instalado consegue abrir este endereço."
} catch (error: Exception) {
    "Não foi possível abrir o endereço: ${error.message ?: error.javaClass.simpleName}"
}

/**
 * Abre o aplicativo de e-mail com destinatário, assunto e corpo prontos.
 *
 * `ACTION_SENDTO` com `mailto:` — e não `ACTION_SEND` — para que a lista mostre
 * apenas aplicativos de **e-mail**, e não mensageiros/redes sociais que também
 * aceitam texto.
 */
fun Context.sendEmail(to: String, subject: String, body: String): String? = try {
    startActivity(
        Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$to")).apply {
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    )
    null
} catch (notFound: ActivityNotFoundException) {
    "Nenhum aplicativo de e-mail instalado."
} catch (error: Exception) {
    "Não foi possível abrir o e-mail: ${error.message ?: error.javaClass.simpleName}"
}

/**
 * Compartilha um texto pelo aplicativo escolhido pelo usuário (menu →
 * "Compartilhar o app").
 *
 * `Intent.createChooser` é obrigatório aqui: sem ele o Android escolheria um
 * aplicativo arbitrário (ou mostraria uma escolha "grudenta" sem título).
 */
fun Context.shareApp(chooserTitle: String, subject: String, text: String): String? = try {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    startActivity(
        Intent.createChooser(send, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
    null
} catch (error: Exception) {
    "Não foi possível compartilhar: ${error.message ?: error.javaClass.simpleName}"
}

/** Copia [text] para a área de transferência. Devolve `false` se não conseguiu. */
fun Context.copyToClipboard(label: String, text: String): Boolean = runCatching {
    val clipboard = getSystemService(ClipboardManager::class.java) ?: return false
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    true
}.getOrDefault(false)

/**
 * Versão instalada do app (`versionName (versionCode)`).
 *
 * Lida do `PackageManager` de propósito: o projeto não habilita o `BuildConfig`
 * (o AGP 9 só o gera sob pedido), e esta via funciona em qualquer configuração.
 */
fun Context.appVersionLabel(): String = runCatching {
    @Suppress("DEPRECATION")
    val info = packageManager.getPackageInfo(packageName, PackageManager.GET_META_DATA)
    "${info.versionName ?: "?"} (${PackageInfoCompat.getLongVersionCode(info)})"
}.getOrDefault("desconhecida")

/** Descrição curta do aparelho, usada no feedback e no diagnóstico. */
fun deviceLabel(): String = "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})"
