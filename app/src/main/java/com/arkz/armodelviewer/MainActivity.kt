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

package com.arkz.armodelviewer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.arkz.armodelviewer.ui.ARViewScreen
import com.arkz.armodelviewer.ui.AppLocaleProvider
import com.arkz.armodelviewer.util.AppPreferences

/**
 * Activity única do app.
 *
 * Responsabilidades:
 *  1. pedir/validar a permissão de câmera (obrigatória para o ARCore);
 *  2. hospedar a UI Compose que contém a tela de Realidade Aumentada.
 *
 * O ciclo de vida do ARCore/Filament é gerenciado pelo próprio SceneView
 * (`ARSceneView`), que acompanha o ciclo de vida da composição — não é
 * necessário criar/pausar a `Session` manualmente aqui.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // O viewport de RA ocupa a tela inteira, inclusive atrás das barras
        // de status/navegação.
        enableEdgeToEdge()

        setContent {
            // Idioma escolhido no menu "Idioma" (fica nas preferências do app).
            // O provider reescreve os recursos de texto APENAS na composição, então
            // a troca é imediata, sem recriar a Activity nem perder o modelo em RA.
            val preferences = remember { AppPreferences(this) }
            var languageTag by remember { mutableStateOf(preferences.languageTag) }

            AppLocaleProvider(languageTag) {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        ArkZARModelViewerApp(
                            onExit = { finish() },
                            languageTag = languageTag,
                            onLanguageChange = { tag ->
                                languageTag = tag
                                preferences.languageTag = tag
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Decide entre a tela de RA e a tela que explica/permite o acesso à câmera.
 *
 * @param languageTag idioma forçado no menu (ou `null` = idioma do aparelho).
 * @param onLanguageChange grava a escolha do usuário (preferências + estado).
 */
@Composable
private fun ArkZARModelViewerApp(
    onExit: () -> Unit,
    languageTag: String?,
    onLanguageChange: (String?) -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity

    fun isCameraGranted(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    var cameraGranted by remember { mutableStateOf(isCameraGranted()) }
    var requestAttempted by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        requestAttempted = true
        cameraGranted = granted
    }

    // Primeira execução: pede a permissão assim que a UI aparece.
    LaunchedEffect(Unit) {
        if (!cameraGranted && !requestAttempted) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Ao voltar das configurações do sistema, revalida a permissão.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        cameraGranted = isCameraGranted()
    }

    if (cameraGranted) {
        // A tela de RA só é composta depois da permissão concedida: o ARCore
        // precisa da câmera para criar a Session.
        ARViewScreen(
            onExit = onExit,
            languageTag = languageTag,
            onLanguageChange = onLanguageChange,
        )
    } else {
        // "Negado permanentemente" = o sistema não mostra mais o diálogo; nesse
        // caso só é possível resolver pelas configurações do app.
        val permanentlyDenied = requestAttempted && activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.CAMERA
            )

        CameraPermissionScreen(
            permanentlyDenied = permanentlyDenied,
            onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            onOpenSettings = { context.openAppSettings() },
            onExit = onExit,
        )
    }
}

/**
 * Explicação exibida quando a permissão de câmera não está concedida.
 */
@Composable
private fun CameraPermissionScreen(
    permanentlyDenied: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    onExit: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.permission_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.permission_rationale),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        if (permanentlyDenied) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.permission_denied_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(32.dp))
        Button(onClick = onRequest, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.permission_grant))
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.permission_open_settings))
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_exit))
        }
    }
}

/** Abre a tela de configurações do app (necessária quando a permissão é negada permanentemente). */
private fun Context.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    )
}

