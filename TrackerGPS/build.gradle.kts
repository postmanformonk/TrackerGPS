import java.util.Properties
import java.io.File

// ======== LOAD SECRETS FROM LOCAL FILE (NOT IN GIT) ========

val secrets = Properties().apply {
    val file = File(rootDir, "secrets.properties")
    if (file.exists()) {
        load(file.inputStream())
    }
}

// Заглушка если файла нет
val mapTilerApiKey = secrets.getProperty("MAPTILER_API_KEY", "YOUR_MAPTILER_API_KEY_HERE")

// ======== PLUGINS ========

plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}

// ======== EXTENSION TO PASS SECRETS TO SUBMODULES ========
// Это позволяет app-модулю получить ключ

ext["MAPTILER_API_KEY"] = mapTilerApiKey
