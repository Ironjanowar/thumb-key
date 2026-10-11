package com.dessalines.thumbkey.ui.components.settings.dictation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.dessalines.thumbkey.R
import com.dessalines.thumbkey.db.AppSettingsViewModel
import com.dessalines.thumbkey.dictation.models.ModelDownloader
import com.dessalines.thumbkey.dictation.models.ModelRepository
import com.dessalines.thumbkey.dictation.models.WhisperModel
import com.dessalines.thumbkey.dictation.models.WhisperModelCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

@Composable
fun DictationSettingsScreen(
    navController: NavController,
    viewModel: AppSettingsViewModel,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val settings by viewModel.appSettings.observeAsState()
    val scope = rememberCoroutineScope()
    val repository = remember { ModelRepository(context.noBackupFilesDir.resolve("whisper-models")) }
    val readiness = remember { mutableStateMapOf<String, Boolean>() }
    var refresh by remember { mutableIntStateOf(0) }
    var progress by remember { mutableIntStateOf(-1) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }

    LaunchedEffect(refresh) {
        val values = withContext(Dispatchers.IO) { WhisperModelCatalog.models.associate { it.id to repository.isReady(it) } }
        readiness.putAll(values)
    }

    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            settings?.let { viewModel.update(it.copy(dictationEnabled = if (granted) 1 else 0)) }
            if (!granted) {
                message = resources.getString(R.string.dictation_permission)
                showSettings = true
            }
        }
    val current = settings ?: return
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = { navController.popBackStack() }) { Text("←") }
        Text(stringResource(R.string.dictation_title))
        Text(stringResource(R.string.dictation_summary))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(current.dictationEnabled != 0, onCheckedChange = { enabled ->
                if (!enabled) {
                    viewModel.update(current.copy(dictationEnabled = 0))
                } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
                ) {
                    viewModel.update(current.copy(dictationEnabled = 1))
                } else {
                    permission.launch(Manifest.permission.RECORD_AUDIO)
                }
            })
            Text(stringResource(R.string.dictation_enable))
        }
        Text(stringResource(R.string.dictation_language))
        listOf(
            "es" to R.string.dictation_language_es,
            "en" to R.string.dictation_language_en,
            "auto" to R.string.dictation_language_auto,
        ).forEach { (code, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(current.dictationLanguage == code, onClick = { viewModel.update(current.copy(dictationLanguage = code)) })
                Text(stringResource(label))
            }
        }
        Text(stringResource(R.string.dictation_model))
        WhisperModelCatalog.models.forEach { model ->
            val ready = readiness[model.id] == true
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(current.dictationModel == model.id, onClick = { viewModel.update(current.copy(dictationModel = model.id)) })
                    Text("${model.title} — ${model.bytes / 1_048_576} MiB")
                }
                if (model.id == "small") Text(stringResource(R.string.dictation_small_memory_warning))
                if (current.dictationModel == model.id) {
                    Text(stringResource(if (ready) R.string.dictation_ready else R.string.dictation_not_ready))
                    if (!ready) {
                        if (downloadJob?.isActive == true) {
                            Text(stringResource(R.string.dictation_downloading, progress.coerceAtLeast(0)))
                            Button(onClick = { downloadJob?.cancel() }) { Text(stringResource(R.string.dictation_cancel_download)) }
                        } else {
                            Button(onClick = {
                                progress = 0
                                message = null
                                downloadJob =
                                    scope.launch {
                                        runCatching {
                                            withContext(Dispatchers.IO) {
                                                ModelDownloader(repository.file(model).parentFile!!) { openHttps(it) }
                                                    .download(model) { done, total ->
                                                        val percent = ((done * 100) / total).toInt()
                                                        scope.launch { progress = percent }
                                                    }
                                            }
                                        }.onFailure { if (it !is kotlinx.coroutines.CancellationException) message = it.message }
                                        progress = -1
                                        downloadJob = null
                                        refresh++
                                    }
                            }) { Text(stringResource(R.string.dictation_download, "${model.bytes / 1_048_576} MiB")) }
                        }
                    } else {
                        Button(onClick = {
                            scope.launch {
                                val deleted = withContext(Dispatchers.IO) { repository.delete(model) }
                                if (!deleted) message = resources.getString(R.string.dictation_model_in_use)
                                refresh++
                            }
                        }) { Text(stringResource(R.string.dictation_delete)) }
                    }
                }
            }
        }
        message?.let { Text(it) }
        if (showSettings) {
            Button(onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
                )
            }) { Text(stringResource(R.string.dictation_open_settings)) }
        }
    }
}

private fun openHttps(model: WhisperModel): java.io.InputStream {
    var url = URL(model.url)
    repeat(6) {
        require(url.protocol == "https") { "Insecure model redirect rejected" }
        val connection = url.openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        val code = connection.responseCode
        if (code in 300..399) {
            url = URL(url, connection.getHeaderField("Location") ?: error("Redirect without location"))
            connection.disconnect()
        } else {
            check(code == 200) { "Model download failed (HTTP $code)" }
            return connection.inputStream
        }
    }
    error("Too many model redirects")
}
