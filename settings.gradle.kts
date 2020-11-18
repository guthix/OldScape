import groovy.util.FileNameFinder
import java.nio.file.Path
import java.nio.file.Paths

pluginManagement {
    val kotlinVersion by extra("1.4.10")
    val ktSerVersion by extra(kotlinVersion)
    val dokkaVersion by extra(kotlinVersion)

    plugins {
        kotlin("jvm") version kotlinVersion
        kotlin("plugin.serialization") version ktSerVersion
        id("org.jetbrains.dokka") version dokkaVersion
    }

    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "oldscape-server"

includeModules("plugins")

include("dumper")

fun includeModules(module: String) {
    val pluginRootDir: Path = rootProject.projectDir.toPath().resolve(module)
    if (pluginRootDir.toFile().exists()) {
        val gradleBuildFiles = FileNameFinder().getFileNames("$pluginRootDir", "**/*.gradle.kts")
        gradleBuildFiles.forEach { filename ->
            val buildFilePath = Paths.get(filename)
            val moduleDir = buildFilePath.parent
            val relativePath = pluginRootDir.relativize(moduleDir)
            val pluginName = "$relativePath".replace(File.separator, ":")
            include(":$module:$pluginName")
        }
    }
}