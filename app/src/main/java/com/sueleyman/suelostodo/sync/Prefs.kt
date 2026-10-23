package com.sueleyman.suelostodo.sync

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Simple SharedPreferences wrapper with observable sync state. */
object SyncPrefs {
    private const val FILE = "sync"
    private fun sp(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isConnected(c: Context) = sp(c).getBoolean("connected", false)
    fun setConnected(c: Context, v: Boolean) = sp(c).edit().putBoolean("connected", v).apply()

    fun autoSync(c: Context) = sp(c).getBoolean("auto", true)
    fun setAutoSync(c: Context, v: Boolean) = sp(c).edit().putBoolean("auto", v).apply()

    fun account(c: Context) = sp(c).getString("account", null)
    fun setAccount(c: Context, v: String?) = sp(c).edit().putString("account", v).apply()

    fun lastSync(c: Context) = sp(c).getLong("last", 0L)
    fun setLastSync(c: Context, v: Long) = sp(c).edit().putLong("last", v).apply()

    fun lastError(c: Context) = sp(c).getString("error", null)
    fun setLastError(c: Context, v: String?) = sp(c).edit().putString("error", v).apply()
}

sealed interface SyncStatus {
    data object Idle : SyncStatus
    data object Running : SyncStatus
    data class Done(val at: Long) : SyncStatus
    data class Failed(val message: String) : SyncStatus
}

object SyncState {
    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val status: StateFlow<SyncStatus> = _status
    internal fun set(s: SyncStatus) { _status.value = s }
}
