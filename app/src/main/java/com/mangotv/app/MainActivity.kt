package com.mangotv.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.mangotv.app.navigation.MangoNavHost
import com.mangotv.app.ui.theme.MangoTvTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MangoTvTheme {
                MangoNavHost()
            }
        }
    }
}
