package com.chroma.ytgrab

import android.app.Application
import android.content.ContentValues
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

enum class Mode(val label: String) {
    VIDEO("Video (MP4)"),
    MP3("Audio (MP3)"),
    M4A("Audio (M4A)")
}

data class UiState(
    val url: String = "",
    val fileName: String = "",
    val mode: Mode = Mode.VIDEO,
    val ready: Boolean = false,
    val busy: Boolean = false,
    val progress: Float = -1f,
    val status: String = "Starting up..."
)

class DownloadViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                YoutubeDL.getInstance().init(getApplication())
                FFmpeg.getInstance().init(getApplication())
                _state.update { it.copy(ready = true, status = "Ready. Paste a link.") }
            } catch (e: Exception) {
                _state.update { it.copy(status = "Failed to start: ${e.message}") }
            }
        }
    }

    fun setUrl(v: String) = _state.update { it.copy(url = v) }
    fun setFileName(v: String) = _state.update { it.copy(fileName = v) }
    fun setMode(m: Mode) = _state.update { it.copy(mode = m) }

    fun download() {
        val s = _state.value
        if (s.busy || s.url.isBlank()) return
        _state.update { it.copy(busy = true, progress = -1f, status = "Fetching info...") }

        viewModelScope.launch(Dispatchers.IO) {
            val workDir = File(getApplication<Application>().cacheDir, "dl")
            try {
                workDir.deleteRecursively()
                workDir.mkdirs()

                // Remove characters that are illegal in file names, and '%' (yt-dlp template char)
                val cleanName = s.fileName
                    .replace(Regex("""[\\/:*?"<>|%]"""), "")
                    .trim()
                val base = if (cleanName.isNotEmpty()) cleanName else "%(title).150B"

                val request = YoutubeDLRequest(s.url.trim())
                request.addOption("-o", "${workDir.absolutePath}/$base.%(ext)s")
                request.addOption("--no-playlist")
                request.addOption("--no-mtime")

                when (s.mode) {
                    Mode.VIDEO -> {
                        request.addOption("-f", "bv*+ba/b")
                        request.addOption("-S", "vcodec:h264,res,acodec:m4a")
                        request.addOption("--merge-output-format", "mp4")
                    }
                    Mode.MP3 -> {
                        request.addOption("-f", "ba/b")
                        request.addOption("-x")
                        request.addOption("--audio-format", "mp3")
                        request.addOption("--audio-quality", "0")
                    }
                    Mode.M4A -> {
                        request.addOption("-f", "ba[ext=m4a]/ba/b")
                        request.addOption("-x")
                        request.addOption("--audio-format", "m4a")
                    }
                }

                YoutubeDL.getInstance().execute(request, null) { progress, _, _ ->
                    _state.update {
                        it.copy(
                            progress = if (progress < 0f) -1f else (progress / 100f).coerceIn(0f, 1f),
                            status = if (progress < 0f) "Working..." else "Downloading... ${progress.toInt()}%"
                        )
                    }
                }

                _state.update { it.copy(progress = -1f, status = "Saving to Downloads...") }

                val result = workDir.listFiles()
                    ?.filter { it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") }
                    ?.maxByOrNull { it.length() }
                    ?: throw IllegalStateException("No output file was produced")

                val savedAs = saveToDownloads(result)
                _state.update { it.copy(busy = false, progress = 1f, status = "Done. Saved to Downloads as:\n$savedAs") }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, progress = -1f, status = "Error: ${e.message}") }
            } finally {
                workDir.deleteRecursively()
            }
        }
    }

    private fun saveToDownloads(file: File): String {
        val resolver = getApplication<Application>().contentResolver
        val ext = file.extension.lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: if (ext == "m4a") "audio/mp4" else "application/octet-stream"

        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, file.name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Could not create file in Downloads")

        resolver.openOutputStream(uri)?.use { out ->
            file.inputStream().use { it.copyTo(out) }
        } ?: throw IllegalStateException("Could not write to Downloads")

        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return file.name
    }

    fun updateYtDlp() {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, progress = -1f, status = "Updating yt-dlp...") }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val res = YoutubeDL.getInstance()
                    .updateYoutubeDL(getApplication(), YoutubeDL.UpdateChannel.STABLE)
                val ver = YoutubeDL.getInstance().version(getApplication())
                _state.update {
                    it.copy(busy = false, status = "Update result: $res\nyt-dlp version: $ver")
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, status = "Update failed: ${e.message}") }
            }
        }
    }
}
