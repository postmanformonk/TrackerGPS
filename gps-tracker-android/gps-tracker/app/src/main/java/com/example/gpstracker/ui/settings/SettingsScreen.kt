package com.example.gpstracker.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.gpstracker.util.LocaleHelper

private data class LanguageOption(val tag: String, val label: String)

private val LANGUAGES = listOf(
    LanguageOption("ru", "Русский"),
    LanguageOption("en", "English")
)

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(LocaleHelper.getCurrentLocaleTag()) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Язык приложения", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        LANGUAGES.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = selected == option.tag,
                        onClick = {
                            selected = option.tag
                            LocaleHelper.setAppLocale(context, option.tag)
                            // AppCompatDelegate сам пересоздаёт activity при смене локали —
                            // дополнительный recreate() не требуется.
                        }
                    )
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = selected == option.tag, onClick = null)
                Spacer(modifier = Modifier.width(12.dp))
                Text(option.label)
            }
        }
    }
}
