package com.vicherarr.camespdroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vicherarr.camespdroid.ui.MainScreen
import com.vicherarr.camespdroid.ui.theme.CamEspDroidTheme
import com.vicherarr.camespdroid.viewmodel.MainViewModel
import coil.ImageLoader
import coil.ImageLoaderFactory
import okhttp3.Dispatcher
import okhttp3.OkHttpClient

import coil.Coil

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val imageLoader = ImageLoader.Builder(this)
            .okHttpClient {
                val dispatcher = Dispatcher().apply {
                    maxRequestsPerHost = 1
                }
                OkHttpClient.Builder()
                    .dispatcher(dispatcher)
                    .build()
            }
            .build()
        Coil.setImageLoader(imageLoader)
        enableEdgeToEdge()
        setContent {
            CamEspDroidTheme {
                val mainViewModel: MainViewModel = viewModel()
                MainScreen(viewModel = mainViewModel)
            }
        }
    }
}