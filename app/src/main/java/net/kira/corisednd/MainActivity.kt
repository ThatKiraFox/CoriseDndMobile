package net.kira.corisednd

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import net.kira.corisednd.ui.AppRoot
import net.kira.corisednd.ui.theme.CoriseDndTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as CoriseApp).container
        setContent {
            CoriseDndTheme {
                Surface { AppRoot(container) }
            }
        }
    }
}