package com.dessalines.thumbkey.ui.components.settings.dictation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.ModelTraining
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.dessalines.thumbkey.R
import com.dessalines.thumbkey.db.AppSettingsViewModel
import com.dessalines.thumbkey.dictation.models.ModelDownloader
import com.dessalines.thumbkey.dictation.models.ModelRepository
import com.dessalines.thumbkey.dictation.models.WhisperModel
import com.dessalines.thumbkey.dictation.models.WhisperModelCatalog
import com.dessalines.thumbkey.ui.components.settings.about.SettingsDivider
import com.dessalines.thumbkey.utils.SimpleTopAppBar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ListPreferenceType
import me.zhanghai.compose.preference.Preference
import me.zhanghai.compose.preference.ProvidePreferenceTheme
import me.zhanghai.compose.preference.SwitchPreference
import java.net.HttpURLConnection
import java.net.URL

private const val LANGUAGE_AUTO = "auto"

@OptIn(ExperimentalMaterial3Api::class)
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
    var permanentDenial by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val permissionMessage = resources.getString(R.string.dictation_permission)
    val modelInUseMessage = resources.getString(R.string.dictation_model_in_use)

    LaunchedEffect(refresh) {
        val values = withContext(Dispatchers.IO) { WhisperModelCatalog.models.associate { it.id to repository.isReady(it) } }
        readiness.putAll(values)
    }

    fun report(text: String) {
        scope.launch { snackbarHostState.showSnackbar(text) }
    }

    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            settings?.let { viewModel.update(it.copy(dictationEnabled = if (granted) 1 else 0)) }
            if (granted) {
                permanentDenial = false
            } else {
                report(permissionMessage)
                permanentDenial = true
            }
        }

    val current = settings ?: return
    val selectedModel = WhisperModelCatalog.find(current.dictationModel) ?: WhisperModelCatalog.models.first()
    val selectedReady = readiness[selectedModel.id] == true
    val languageLabels = listOf("es", "en", LANGUAGE_AUTO).associateWith { resources.getString(languageLabel(it)) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { SimpleTopAppBar(text = stringResource(R.string.dictation_title), navController = navController) },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .background(color = MaterialTheme.colorScheme.surface)
                    .imePadding(),
        ) {
            ProvidePreferenceTheme {
                SwitchPreference(
                    value = current.dictationEnabled != 0,
                    onValueChange = { enabled ->
                        when {
                            !enabled -> viewModel.update(current.copy(dictationEnabled = 0))

                            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                                PackageManager.PERMISSION_GRANTED -> viewModel.update(current.copy(dictationEnabled = 1))

                            else -> permission.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    title = { Text(stringResource(R.string.dictation_enable)) },
                    summary = { Text(stringResource(R.string.dictation_summary)) },
                    icon = { Icon(Icons.Outlined.Mic, contentDescription = null) },
                )
                ListPreference(
                    type = ListPreferenceType.DROPDOWN_MENU,
                    value = current.dictationLanguage,
                    onValueChange = { viewModel.update(current.copy(dictationLanguage = it)) },
                    values = listOf("es", "en", LANGUAGE_AUTO),
                    valueToText = { AnnotatedString(languageLabels.getValue(it)) },
                    title = { Text(stringResource(R.string.dictation_language)) },
                    summary = { Text(languageLabels[current.dictationLanguage] ?: languageLabels.getValue("es")) },
                    icon = { Icon(Icons.Outlined.Language, contentDescription = null) },
                )
                ListPreference(
                    type = ListPreferenceType.DROPDOWN_MENU,
                    value = selectedModel.id,
                    onValueChange = { viewModel.update(current.copy(dictationModel = it)) },
                    values = WhisperModelCatalog.models.map { it.id },
                    valueToText = { AnnotatedString(modelLabel(it)) },
                    title = { Text(stringResource(R.string.dictation_model)) },
                    summary = { Text(modelLabel(selectedModel.id)) },
                    icon = { Icon(Icons.Outlined.ModelTraining, contentDescription = null) },
                )
                if (selectedModel.id == "small") {
                    Preference(
                        title = { Text(stringResource(R.string.dictation_small_memory_warning)) },
                    )
                }
                Preference(
                    title = {
                        Text(
                            when {
                                downloadJob != null -> stringResource(R.string.dictation_cancel_download)
                                selectedReady -> stringResource(R.string.dictation_delete)
                                else -> stringResource(R.string.dictation_download, "${selectedModel.bytes / 1_048_576} MiB")
                            },
                        )
                    },
                    summary = { Text(modelStateText(selectedReady, progress)) },
                    icon = {
                        Icon(
                            if (selectedReady) Icons.Outlined.DeleteOutline else Icons.Outlined.Download,
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        when {
                            downloadJob != null -> {
                                downloadJob?.cancel()
                            }

                            selectedReady -> {
                                scope.launch {
                                    val deleted = withContext(Dispatchers.IO) { repository.delete(selectedModel) }
                                    if (!deleted) report(modelInUseMessage)
                                    refresh++
                                }
                            }

                            else -> {
                                progress = 0
                                downloadJob =
                                    scope.launch {
                                        val failure =
                                            runCatching {
                                                withContext(Dispatchers.IO) {
                                                    ModelDownloader(repository.file(selectedModel).parentFile!!) { openHttps(it) }
                                                        .download(selectedModel) { done, total ->
                                                            progress = ((done * 100) / total).toInt()
                                                        }
                                                }
                                            }.exceptionOrNull()
                                        if (failure != null &&
                                            failure !is CancellationException
                                        ) {
                                            report(failure.message ?: failure.toString())
                                        }
                                        progress = -1
                                        downloadJob = null
                                        refresh++
                                    }
                            }
                        }
                    },
                )
                if (permanentDenial) {
                    SettingsDivider()
                    Preference(
                        title = { Text(stringResource(R.string.dictation_open_settings)) },
                        icon = { Icon(Icons.Outlined.OpenInNew, contentDescription = null) },
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
                            )
                        },
                    )
                }
            }
        }
    }
}

private fun languageLabel(code: String): Int =
    when (code) {
        "en" -> R.string.dictation_language_en
        LANGUAGE_AUTO -> R.string.dictation_language_auto
        else -> R.string.dictation_language_es
    }

private fun modelLabel(id: String): String {
    val model = WhisperModelCatalog.find(id) ?: WhisperModelCatalog.models.first()
    return "${model.title} — ${model.bytes / 1_048_576} MiB"
}

@Composable
private fun modelStateText(
    ready: Boolean,
    progress: Int,
): String =
    when {
        progress in 0..99 -> stringResource(R.string.dictation_downloading, progress)
        ready -> stringResource(R.string.dictation_ready)
        else -> stringResource(R.string.dictation_not_ready)
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
