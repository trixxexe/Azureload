package com.ritam.azureload.core.settings

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _wifiOnlyFlow = MutableStateFlow(getWifiOnly())
    val wifiOnlyFlow: StateFlow<Boolean> = _wifiOnlyFlow.asStateFlow()

    private val _defaultConcurrencyFlow = MutableStateFlow(getDefaultConcurrency())
    val defaultConcurrencyFlow: StateFlow<Int> = _defaultConcurrencyFlow.asStateFlow()

    private val _maxActiveTransfersFlow = MutableStateFlow(getMaxActiveTransfers())
    val maxActiveTransfersFlow: StateFlow<Int> = _maxActiveTransfersFlow.asStateFlow()

    fun getWifiOnly(): Boolean = prefs.getBoolean(KEY_WIFI_ONLY, false)

    fun setWifiOnly(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WIFI_ONLY, enabled).apply()
        _wifiOnlyFlow.value = enabled
    }

    fun getDefaultConcurrency(): Int = prefs.getInt(KEY_DEFAULT_CONCURRENCY, 4).coerceIn(1, 16)

    fun setDefaultConcurrency(concurrency: Int) {
        val clamped = concurrency.coerceIn(1, 16)
        prefs.edit().putInt(KEY_DEFAULT_CONCURRENCY, clamped).apply()
        _defaultConcurrencyFlow.value = clamped
    }

    fun getMaxActiveTransfers(): Int = prefs.getInt(KEY_MAX_ACTIVE_TRANSFERS, 3).coerceIn(1, 6)

    fun setMaxActiveTransfers(maxActive: Int) {
        val clamped = maxActive.coerceIn(1, 6)
        prefs.edit().putInt(KEY_MAX_ACTIVE_TRANSFERS, clamped).apply()
        _maxActiveTransfersFlow.value = clamped
    }

    companion object {
        private const val PREFS_NAME = "azureload_settings"
        private const val KEY_WIFI_ONLY = "pref_wifi_only"
        private const val KEY_DEFAULT_CONCURRENCY = "pref_default_concurrency"
        private const val KEY_MAX_ACTIVE_TRANSFERS = "pref_max_active_transfers"

        fun isWifiConnected(context: Context): Boolean {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            val activeNetwork = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
            return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        }

        fun isNetworkAvailable(context: Context): Boolean {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            val activeNetwork = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
    }
}
