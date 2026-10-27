package com.chroma.ytgrab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    private val vm: DownloadViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DownloaderScreen(vm)
                }
            }
        }
    }
}

@Composable
fun DownloaderScreen(vm: DownloadViewModel) {
    val s by vm.state.collectAsState()
    val enabled = s.ready && !s.busy

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("YTGrab", style = MaterialTheme.typography.headlineMedium)

        OutlinedTextField(
            value = s.url,
            onValueChange = vm::setUrl,
            label = { Text("Video link") },
            singleLine = true,
            enabled = !s.busy,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = s.fileName,
            onValueChange = vm::setFileName,
            label = { Text("File name (optional)") },
            supportingText = { Text("Leave empty to use the video's title") },
            singleLine = true,
            enabled = !s.busy,
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Mode.values().forEach { m ->
                FilterChip(
                    selected = s.mode == m,
                    onClick = { vm.setMode(m) },
                    enabled = !s.busy,
                    label = { Text(m.label) }
                )
            }
        }

        Button(
            onClick = vm::download,
            enabled = enabled && s.url.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Download") }

        if (s.busy) {
            if (s.progress < 0f) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(
                    progress = { s.progress },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Text(s.status, style = MaterialTheme.typography.bodyMedium)

        OutlinedButton(
            onClick = vm::updateYtDlp,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Update yt-dlp") }

        Text(
            "Only download content you have the right to download.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
