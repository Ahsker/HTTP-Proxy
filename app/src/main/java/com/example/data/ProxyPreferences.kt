package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.ControlMode
import com.example.model.ProxyConfig
import com.example.model.ThemeMode

class ProxyPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("http_proxy_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_HOST = "key_host"
        private const val KEY_PORT = "key_port"
        private const val KEY_AUTH_ENABLED = "key_auth_enabled"
        private const val KEY_USERNAME = "key_username"
        private const val KEY_PASSWORD = "key_password"
        private const val KEY_POWER_SAVE = "key_power_save"
        private const val KEY_AUTO_START = "key_auto_start"
        private const val KEY_KEEP_CPU_AWAKE = "key_keep_cpu_awake"
        private const val KEY_THEME_MODE = "key_theme_mode"
        private const val KEY_CONTROL_MODE = "key_control_mode"
    }

    fun loadConfig(): ProxyConfig {
        return ProxyConfig(
            host = prefs.getString(KEY_HOST, "0.0.0.0") ?: "0.0.0.0",
            port = prefs.getInt(KEY_PORT, 8080),
            authEnabled = prefs.getBoolean(KEY_AUTH_ENABLED, false),
            username = prefs.getString(KEY_USERNAME, "") ?: "",
            password = prefs.getString(KEY_PASSWORD, "") ?: "",
            powerSave = prefs.getBoolean(KEY_POWER_SAVE, true),
            autoStartOnBoot = prefs.getBoolean(KEY_AUTO_START, false),
            keepCpuAwake = prefs.getBoolean(KEY_KEEP_CPU_AWAKE, true)
        )
    }

    fun saveConfig(config: ProxyConfig) {
        prefs.edit()
            .putString(KEY_HOST, config.host)
            .putInt(KEY_PORT, config.port)
            .putBoolean(KEY_AUTH_ENABLED, config.authEnabled)
            .putString(KEY_USERNAME, config.username)
            .putString(KEY_PASSWORD, config.password)
            .putBoolean(KEY_POWER_SAVE, config.powerSave)
            .putBoolean(KEY_AUTO_START, config.autoStartOnBoot)
            .putBoolean(KEY_KEEP_CPU_AWAKE, config.keepCpuAwake)
            .apply()
    }

    fun loadThemeMode(): ThemeMode {
        val name = prefs.getString(KEY_THEME_MODE, ThemeMode.LIGHT.name) ?: ThemeMode.LIGHT.name
        return try {
            ThemeMode.valueOf(name)
        } catch (e: Exception) {
            ThemeMode.LIGHT
        }
    }

    fun saveThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    fun loadControlMode(): ControlMode {
        val name = prefs.getString(KEY_CONTROL_MODE, ControlMode.USB_SINGLE_USER.name) ?: ControlMode.USB_SINGLE_USER.name
        return try {
            ControlMode.valueOf(name)
        } catch (e: Exception) {
            ControlMode.USB_SINGLE_USER
        }
    }

    fun saveControlMode(mode: ControlMode) {
        prefs.edit().putString(KEY_CONTROL_MODE, mode.name).apply()
    }
}
