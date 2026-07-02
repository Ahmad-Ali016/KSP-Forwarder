package com.kspay.forwarder

import android.os.Bundle
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