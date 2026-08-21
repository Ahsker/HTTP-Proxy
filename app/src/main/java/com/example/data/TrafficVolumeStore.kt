package com.example.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar

data class VolumeCheckpoint(
    val timestamp: Long,
    val downloadBytes: Long,   // proxy -> client
    val uploadBytes: Long,     // client -> proxy
    val sessionCount: Int
)

/**
 * Persists cumulative traffic volume to a file, one line per checkpoint.
 * Retention: current calendar month only (matches cellular plan reset).
 * Line format: epochMillis|downloadBytes|uploadBytes|sessionCount
 */
object TrafficVolumeStore {

    private const val FILE_NAME = "traffic_volume_log.csv"
    private var file: File? = null

    private val _checkpoints = MutableStateFlow<List<VolumeCheckpoint>>(emptyList())
    val checkpoints: StateFlow<List<VolumeCheckpoint>> = _checkpoints.asStateFlow()

    fun init(context: Context) {
        if (file != null) return
        file = File(context.filesDir, FILE_NAME)
        CoroutineScope(Dispatchers.IO).launch {
            loadAndPurge()
        }
    }

    private fun startOfMonthMillis(): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            clear(Calendar.MINUTE); clear(Calendar.SECOND); clear(Calendar.MILLISECOND)
        }
        return cal.timeInMillis
    }

    private fun loadAndPurge() {
        val f = file ?: return
        try {
            val cutoff = startOfMonthMillis()
            val kept = mutableListOf<VolumeCheckpoint>()
            if (f.exists()) {
                f.readLines().forEach { line ->
                    val p = line.split("|")
                    if (p.size != 4) return@forEach
                    val ts = p[0].toLongOrNull() ?: return@forEach
                    if (ts >= cutoff) {
                        kept.add(VolumeCheckpoint(
                            ts,
                            p[1].toLongOrNull() ?: 0L,
                            p[2].toLongOrNull() ?: 0L,
                            p[3].toIntOrNull() ?: 0
                        ))
                    }
                }
            }
            _checkpoints.value = kept
            rewriteFile(kept) // compaction: physically drop purged lines
        } catch (_: Exception) {}
    }

    @Synchronized
    fun appendCheckpoint(downloadBytes: Long, uploadBytes: Long, sessionCount: Int) {
        val f = file ?: return
        if (sessionCount <= 0) return
        try {
            val cp = VolumeCheckpoint(System.currentTimeMillis(), downloadBytes, uploadBytes, sessionCount)
            f.appendText("${cp.timestamp}|${cp.downloadBytes}|${cp.uploadBytes}|${cp.sessionCount}\n")
            _checkpoints.value = _checkpoints.value + cp
        } catch (_: Exception) {}
    }

    private fun rewriteFile(list: List<VolumeCheckpoint>) {
        val f = file ?: return
        try {
            val text = list.joinToString("\n") { "${it.timestamp}|${it.downloadBytes}|${it.uploadBytes}|${it.sessionCount}" }
            f.writeText(if (text.isEmpty()) "" else "$text\n")
        } catch (_: Exception) {}
    }
}
