package com.example.gpstracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.gpstracker.ui.navigation.AppNavGraph
import com.example.gpstracker.ui.onboarding.OnboardingScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            var onboardingDone by remember { mutableStateOf(false) }

            MaterialTheme {
                Surface(modifier = Modifier) {
                    if (onboardingDone) {
                        AppNavGraph()
                    } else {
                        OnboardingScreen(onFinished = { onboardingDone = true })
                    }
                }
            }
        }
    }
}
