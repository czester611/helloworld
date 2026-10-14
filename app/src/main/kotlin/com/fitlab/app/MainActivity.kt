package com.fitlab.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.fitlab.app.ui.nav.FitLabNavHost
import com.fitlab.app.ui.theme.FitLabTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FitLabTheme { FitLabNavHost() }
        }
    }
}
