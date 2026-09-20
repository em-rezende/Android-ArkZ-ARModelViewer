# Regras do R8/ProGuard.
# O projeto nao usa minificacao por padrao (isMinifyEnabled = false).
# Ao ativar a minificacao, mantenha o ARCore e o SceneView intactos, pois usam
# JNI/reflexao internamente.

-keep class com.google.ar.core.** { *; }
-keep class com.google.android.filament.** { *; }
-keep class io.github.sceneview.** { *; }
-keep class dev.romainguy.kotlin.math.** { *; }

-dontwarn com.google.ar.core.**
