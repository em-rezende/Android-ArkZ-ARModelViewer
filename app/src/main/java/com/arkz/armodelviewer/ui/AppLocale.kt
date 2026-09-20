package com.arkz.armodelviewer.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import com.arkz.armodelviewer.R
import java.util.Locale

/**
 * Aplica o idioma escolhido no menu **"Idioma"** a todo o conteúdo Compose.
 *
 * Por que forçar o idioma? Porque o idioma do aparelho nem sempre é o desejado e,
 * principalmente, porque o Android resolve recursos por **língua** antes de cair
 * no padrão: um aparelho configurado em `pt-BR` prefere `values-pt-rPT` (mesma
 * língua, outra região) ao `values/` padrão — foi exatamente o que fez o app
 * aparecer em português de Portugal num celular brasileiro. Com esta opção o
 * usuário decide dentro do app.
 *
 * [languageTag] `null`/vazio significa "seguir o sistema" (nada é sobrescrito).
 * Os textos vêm das pastas `values-<idioma>/`, então o idioma forçado funciona nos dois
 * sentidos (inclusive trocar de um idioma traduzido para o padrão).
 *
 * @see LocalResources
 */
@Composable
fun AppLocaleProvider(
    languageTag: String?,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    // Configuração vigente (do aparelho). Lida AQUI, antes do provider, para não
    // ler o valor que nós mesmos fornecemos logo abaixo.
    val systemConfiguration = LocalConfiguration.current

    // A estrutura da composição NÃO muda entre "Sistema" e um idioma forçado —
    // o provider está sempre presente e só os valores mudam. Isso é essencial na
    // tela de RA: trocar de idioma não pode descartar a subárvore (e nem recriar a
    // cena/recarregar o modelo).
    val (configuration, resources) = remember(languageTag, systemConfiguration) {
        if (languageTag.isNullOrBlank()) {
            // "Sistema": devolve exatamente o que já estava valendo.
            systemConfiguration to context.resources
        } else {
            val localized = localizedConfiguration(context, languageTag)
            localized to context.createConfigurationContext(localized).resources
        }
    }

    CompositionLocalProvider(
        // `LocalResources` é o que `stringResource`/`pluralStringResource` leem;
        // `LocalConfiguration` mantém a direção de escrita e dispara a recomposição
        // quando o idioma muda.
        LocalResources provides resources,
        LocalConfiguration provides configuration,
    ) {
        content()
    }
}

/** Configuração com [languageTag] aplicado (idioma + direção de escrita). */
private fun localizedConfiguration(context: Context, languageTag: String): Configuration {
    val locale = Locale.forLanguageTag(languageTag)
    return Configuration(context.resources.configuration).apply {
        setLocale(locale)
        setLayoutDirection(locale)
    }
}

/**
 * Uma opção do diálogo "Idioma".
 *
 * @property tag código BCP-47 (`pt-BR`, `zh-CN`...) ou `null` para o sistema.
 * @property labelRes rótulo exibido — em geral o **endônimo** (o nome do idioma
 *   no próprio idioma), para o usuário reconhecer a opção sem tradução.
 */
data class AppLanguage(
    val tag: String?,
    val labelRes: Int,
)

/**
 * Idiomas oferecidos. A ordem é a das traduções disponíveis no projeto; manter
 * esta lista e as pastas `values-<idioma>/` em sincronia é o único cuidado necessário
 * para acrescentar um idioma novo.
 */
val appLanguages: List<AppLanguage> = listOf(
    AppLanguage(tag = null, labelRes = R.string.language_system),
    AppLanguage(tag = "pt-BR", labelRes = R.string.language_pt_br),
    AppLanguage(tag = "pt-PT", labelRes = R.string.language_pt_pt),
    AppLanguage(tag = "en", labelRes = R.string.language_en),
    AppLanguage(tag = "es", labelRes = R.string.language_es),
    AppLanguage(tag = "fr", labelRes = R.string.language_fr),
    AppLanguage(tag = "de", labelRes = R.string.language_de),
    AppLanguage(tag = "it", labelRes = R.string.language_it),
    AppLanguage(tag = "zh-CN", labelRes = R.string.language_zh),
)

/**
 * Rótulo do idioma atualmente escolhido (o endônimo da opção, ou o texto
 * localizado de "Sistema").
 */
fun appLanguageLabelRes(languageTag: String?): Int =
    appLanguages.firstOrNull { it.tag == languageTag }?.labelRes
        ?: R.string.language_system
