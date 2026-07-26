package com.vicherarr.camespdroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vicherarr.camespdroid.ui.MainScreen
import com.vicherarr.camespdroid.ui.theme.CamEspDroidTheme
import com.vicherarr.camespdroid.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CamEspDroidTheme {
                val mainViewModel: MainViewModel = viewModel()
                MainScreen(viewModel = mainViewModel)
            }
        }
    }
}