// Configuracao de repositorios e modulos do projeto.
// O SceneView e o ARCore sao publicados no Maven Central / Google Maven.
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ArkZ ARModelViewer"
include(":app")
