package com.vicherarr.camespdroid.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Enlace WiFi LOCAL al AP de la cámara mediante `WifiNetworkSpecifier` (Android 10+).
 *
 * Conecta el móvil al SoftAP del ESP **solo para esta app**, sin cambiar la red WiFi normal del
 * teléfono ni "recordarla" (evita que el móvil salte de red cuando el AP desaparece). El tráfico
 * HTTP se enruta por este enlace usando el `socketFactory` de la Network obtenida.
 */
class WifiLink(private val context: Context) {

    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var callback: ConnectivityManager.NetworkCallback? = null
    @Volatile private var network: Network? = null

    /**
     * Solicita el enlace al AP `ssid`/`pass`. La primera vez el sistema muestra un diálogo para
     * aprobar la conexión. `onResult(true)` cuando el enlace está disponible; `false` si falla.
     */
    fun connect(ssid: String, pass: String, onResult: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            onResult(false)
            return
        }
        release()
        val specifier = WifiNetworkSpecifier.Builder()
            .setSsid(ssid)
            .setWpa2Passphrase(pass)
            .build()
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(specifier)
            .build()
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(net: Network) {
                network = net
                onResult(true)
            }
            override fun onUnavailable() {
                network = null
                onResult(false)
            }
            override fun onLost(net: Network) {
                if (network == net) network = null
            }
        }
        callback = cb
        cm.requestNetwork(request, cb)
    }

    /** Cliente OkHttp enrutado por el enlace local (o null si aún no hay enlace). */
    fun httpClient(): OkHttpClient? {
        val net = network ?: return null
        return OkHttpClient.Builder()
            .socketFactory(net.socketFactory)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val credential = okhttp3.Credentials.basic("admin", "001989")
                val request = chain.request().newBuilder()
                    .header("Authorization", credential)
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    fun isLinked(): Boolean = network != null

    fun release() {
        callback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        callback = null
        network = null
    }
}
