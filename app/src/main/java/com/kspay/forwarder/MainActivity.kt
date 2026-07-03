package com.kspay.forwarder

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.kspay.forwarder.ui.navigation.ForwarderNavHost
import com.kspay.forwarder.ui.theme.KSPForwarderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // This runs as the single foreground app on a POS terminal — the screen must never sleep
        // mid-sale.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        setContent {
            KSPForwarderTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ForwarderNavHost(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}