package com.lactose.textme

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.lactose.textme.presentation.navigation.TextMeNavHost
import com.lactose.textme.ui.theme.TextMeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TextMeTheme {
                TextMeNavHost()
            }
        }
    }
}