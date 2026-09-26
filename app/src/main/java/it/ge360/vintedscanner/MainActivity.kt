package it.ge360.vintedscanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import it.ge360.vintedscanner.ui.VintedScannerApp
import it.ge360.vintedscanner.ui.theme.VintedScannerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VintedScannerTheme {
                VintedScannerApp()
            }
        }
    }
}
