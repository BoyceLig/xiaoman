package dev.xiaoman.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.xiaoman.app.AppGraph
import dev.xiaoman.app.R
import dev.xiaoman.app.settings.ModelSettings
import dev.xiaoman.app.settings.SettingsForm
import dev.xiaoman.api.ModelRole
import dev.xiaoman.llm.ModelCatalog
import dev.xiaoman.llm.ModelProbe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(graph: AppGraph) {
    val store = graph.settings
    var form by remember { mutableStateOf(SettingsForm.of(store.current)) }
    var saved by remember { mutableStateOf(false) }
    val parsed = form.toSettings()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader(stringResource(R.string.settings_title), "模型、任务与本地文件夹", eyebrow = "settings")
        Column(
            Modifier.padding(start = ScreenPadding, end = ScreenPadding, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ModelSection(
                title = stringResource(R.string.settings_chat_model),
                subtitle = "理解你的话，决定要做什么",
                icon = Icons.AutoMirrored.Outlined.Chat,
                model = form.chat,
                role = ModelRole.CHAT,
                fetchModels = ModelCatalog::list,
                probe = graph::testModel,
            ) {
                form = form.copy(chat = it)
                saved = false
            }
            ModelSection(
                title = stringResource(R.string.settings_operator_model),
                subtitle = "看屏幕，决定下一步点哪里",
                icon = Icons.Outlined.Image,
                model = form.operator,
                role = ModelRole.OPERATOR,
                fetchModels = ModelCatalog::list,
                probe = graph::testModel,
            ) {
                form = form.copy(operator = it)
                saved = false
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = {
                    form = form.copy(
                        operator = form.operator.copy(
                            baseUrl = form.chat.baseUrl,
                            model = form.chat.model,
                            apiKey = form.chat.apiKey,
                            temperature = form.chat.temperature,
                        ),
                    )
                    saved = false
                }) { Text(stringResource(R.string.settings_copy_chat)) }
            }

            SectionCard {
                SectionCardHeader(Icons.Outlined.Tune, stringResource(R.string.settings_agent), "小满执行任务时的行为")
                OutlinedTextField(
                    value = form.maxSteps,
                    onValueChange = {
                        form = form.copy(maxSteps = it)
                        saved = false
                    },
                    label = { Text(stringResource(R.string.settings_max_steps)) },
                    isError = !form.maxStepsValid,
                    supportingText = { if (!form.maxStepsValid) Text(stringResource(R.string.settings_invalid_number)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(R.string.settings_learn), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "成功路径会写进记忆，下次更省步骤",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = form.learnFromSuccess, onCheckedChange = {
                        form = form.copy(learnFromSuccess = it)
                        saved = false
                    })
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    enabled = parsed != null,
                    onClick = {
                        parsed?.let {
                            store.save(it)
                            saved = true
                        }
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                ) { Text(stringResource(R.string.settings_save)) }
                if (saved) {
                    Pill(
                        stringResource(R.string.settings_saved),
                        container = MaterialTheme.colorScheme.primaryContainer,
                        content = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }

            SectionCard {
                SectionCardHeader(Icons.Outlined.Folder, stringResource(R.string.settings_folders), "都存在手机上，可直接拷贝")
                FolderRow("技能", graph.skillsDir.path)
                FolderRow("记忆", graph.memoryDir.path)
                FolderRow("轨迹", graph.tracesDir.path)
            }
        }
    }
}

@Composable
private fun FolderRow(label: String, path: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            path,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * One endpoint. The refresh button next to 模型名称 asks the endpoint's `/models` and offers the
 * answer as a list, so the model id does not have to be typed from memory. 测试连接 sends one real
 * request and reports either the reply or the reason it failed.
 */
@Composable
internal fun ModelSection(
    title: String,
    subtitle: String,
    icon: ImageVector,
    model: SettingsForm.ModelForm,
    role: ModelRole,
    fetchModels: suspend (baseUrl: String, apiKey: String, needImage: Boolean) -> ModelCatalog.Catalog,
    probe: suspend (settings: ModelSettings, role: ModelRole) -> ModelProbe.Report,
    onChange: (SettingsForm.ModelForm) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showKey by remember { mutableStateOf(false) }
    var fetching by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    var filledOne by remember { mutableStateOf(false) }
    var choices by remember { mutableStateOf<ModelCatalog.Catalog?>(null) }
    var testing by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<ModelProbe.Report?>(null) }

    // Any edit makes the previous verdict stale.
    fun update(next: SettingsForm.ModelForm) {
        report = null
        onChange(next)
    }

    // Both buttons do network work on the composition scope; nothing may escape it (that used to be
    // a crash), and the button spinner must always stop.
    fun launchGuarded(onError: (String) -> Unit, onFinally: () -> Unit, block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError((e.message ?: e.javaClass.simpleName).take(200))
            } finally {
                onFinally()
            }
        }
    }

    fun test() {
        val settings = model.toSettings()
        if (settings == null) {
            report = ModelProbe.Report(false, context.getString(R.string.settings_invalid_number))
            return
        }
        testing = true
        report = null
        launchGuarded(
            onError = { report = ModelProbe.Report(false, context.getString(R.string.settings_probe_failed, it)) },
            onFinally = { testing = false },
        ) {
            report = withContext(Dispatchers.IO) { probe(settings, role) }
        }
    }

    fun refresh() {
        fetching = true
        problem = null
        filledOne = false
        val baseUrl = model.baseUrl
        val apiKey = model.apiKey
        val needImage = role == ModelRole.OPERATOR
        launchGuarded(
            onError = { problem = context.getString(R.string.settings_models_failed, it) },
            onFinally = { fetching = false },
        ) {
            val catalog = fetchModels(baseUrl, apiKey, needImage)
            when {
                catalog.size == 0 -> problem = context.getString(
                    if (needImage) R.string.settings_models_no_vision else R.string.settings_models_empty,
                )
                catalog.size == 1 -> {
                    update(model.copy(model = catalog.ids.first()))
                    filledOne = true
                }
                else -> choices = catalog
            }
        }
    }

    SectionCard {
        SectionCardHeader(icon, title, subtitle)
        OutlinedTextField(
            value = model.baseUrl,
            onValueChange = { update(model.copy(baseUrl = it)) },
            label = { Text(stringResource(R.string.settings_base_url)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = model.model,
            onValueChange = {
                filledOne = false
                update(model.copy(model = it))
            },
            label = { Text(stringResource(R.string.settings_model)) },
            singleLine = true,
            isError = problem != null,
            supportingText = {
                when {
                    problem != null -> Text(problem.orEmpty())
                    filledOne -> Text(stringResource(R.string.settings_models_filled))
                }
            },
            trailingIcon = {
                if (fetching) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(enabled = model.baseUrl.isNotBlank(), onClick = ::refresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.settings_fetch_models))
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = model.apiKey,
            onValueChange = { update(model.copy(apiKey = it)) },
            label = { Text(stringResource(R.string.settings_api_key)) },
            singleLine = true,
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            trailingIcon = {
                IconButton(onClick = { showKey = !showKey }) {
                    Icon(
                        if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = stringResource(if (showKey) R.string.settings_hide_key else R.string.settings_show_key),
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = model.temperature,
            onValueChange = { update(model.copy(temperature = it)) },
            label = { Text(stringResource(R.string.settings_temperature)) },
            isError = !model.temperatureValid,
            supportingText = { if (!model.temperatureValid) Text(stringResource(R.string.settings_invalid_number)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedButton(
            enabled = !testing && model.baseUrl.isNotBlank() && model.model.isNotBlank(),
            onClick = ::test,
            modifier = Modifier.fillMaxWidth().height(44.dp),
        ) {
            if (testing) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.settings_probe_running))
            } else {
                Text(stringResource(R.string.settings_probe))
            }
        }
        val result = report
        when {
            result != null -> Text(
                result.detail,
                style = MaterialTheme.typography.bodySmall,
                color = if (result.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
            )
            !testing && (model.baseUrl.isBlank() || model.model.isBlank()) -> Text(
                stringResource(R.string.settings_probe_blank),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    choices?.let { catalog ->
        AlertDialog(
            onDismissRequest = { choices = null },
            title = { Text(stringResource(R.string.settings_models_title)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.settings_models_count, catalog.size),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (catalog.retired > 0 || catalog.unusable > 0) {
                        Text(
                            stringResource(R.string.settings_models_hidden, catalog.retired, catalog.unusable),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(catalog.entries, key = { it.id }) { entry ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                                    .clickable {
                                        update(model.copy(model = entry.id))
                                        choices = null
                                    }
                                    .padding(vertical = 12.dp),
                            ) {
                                Text(
                                    entry.id,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (entry.id == model.model) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                if (entry.image == true) {
                                    Text(
                                        stringResource(R.string.settings_models_vision),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choices = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
