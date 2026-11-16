package dev.xiaoman.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.xiaoman.app.AppGraph
import dev.xiaoman.app.R
import dev.xiaoman.mcp.CatalogServer
import dev.xiaoman.mcp.ConfirmPolicy
import dev.xiaoman.mcp.CustomMcpServer
import dev.xiaoman.mcp.McpCatalog
import dev.xiaoman.mcp.McpPlatform
import dev.xiaoman.mcp.McpSelection
import dev.xiaoman.mcp.ServerStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Services that work on Android, grouped by category. */
private val catalog: List<CatalogServer> by lazy { McpCatalog.builtIn.availableOn(McpPlatform.ANDROID) }

/** Ids ticked by 推荐组合: free or easy to obtain, read-only. */
private val RECOMMENDED = setOf("amap", "aihot")

/** One-tap MCP services: tick a service, paste its key, save. Changes apply from the next chat message. */
@Composable
fun McpScreen(graph: AppGraph) {
    val store = graph.mcpSettings
    val context = LocalContext.current
    var selection by remember { mutableStateOf(store.current) }
    var saved by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var testProblem by remember { mutableStateOf<String?>(null) }
    var testNotes by remember { mutableStateOf<List<String>>(emptyList()) }
    val status = remember { mutableStateMapOf<String, ServerStatus>() }
    val scope = rememberCoroutineScope()

    fun update(next: McpSelection) {
        selection = next
        saved = false
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader(stringResource(R.string.mcp_title), "勾选即启用，从下一条消息开始生效", eyebrow = "services")
        Column(
            Modifier.padding(start = ScreenPadding, end = ScreenPadding, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                stringResource(R.string.mcp_intro),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconTile(Icons.Outlined.Hub)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            stringResource(R.string.mcp_enabled_count, selection.enabled.size + selection.custom.size),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            "会改数据或产生费用的操作，仍会先问你",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { update(selection.copy(enabled = selection.enabled + RECOMMENDED)) }) {
                        Text(stringResource(R.string.mcp_recommended))
                    }
                }
            }

            catalog.groupBy { it.category }.forEach { (category, servers) ->
                SectionLabel(category)
                servers.forEach { server ->
                    ServiceCard(
                        server = server,
                        selection = selection,
                        status = status[server.id],
                        onChange = ::update,
                        onTest = {
                            // 只测这一家：临时选择里只留当前服务，避免把别的服务一起连。
                            val single = selection.copy(enabled = setOf(server.id), custom = emptyList())
                            graph.testMcp(single) to graph.mcpConfig(single).warnings
                        },
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            CustomServers(selection, status, ::update)

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        store.save(selection)
                        saved = true
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                ) { Text(stringResource(R.string.settings_save)) }
                OutlinedButton(
                    enabled = !testing,
                    onClick = {
                        testing = true
                        testProblem = null
                        testNotes = emptyList()
                        // Everything here (config files, keystore, network) can fail; a failure must show up in
                        // the row below, never as an app crash from an uncaught coroutine exception.
                        scope.launch {
                            try {
                                status.clear()
                                val (result, notes) = withContext(Dispatchers.IO) {
                                    graph.testMcp(selection) to graph.mcpConfig(selection).warnings
                                }
                                result.forEach { status[it.name] = it }
                                testNotes = notes
                                if (result.isEmpty()) testProblem = context.getString(R.string.mcp_test_nothing)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Throwable) {
                                testProblem = context.getString(
                                    R.string.mcp_test_failed,
                                    (e.message ?: e.javaClass.simpleName).take(160),
                                )
                            } finally {
                                testing = false
                            }
                        }
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                ) { Text(stringResource(if (testing) R.string.mcp_testing else R.string.mcp_test)) }
            }
            if (saved) {
                Text(stringResource(R.string.settings_saved), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            testProblem?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            if (testNotes.isNotEmpty()) {
                Text(
                    stringResource(R.string.mcp_test_skipped) + "：" + testNotes.joinToString("；"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun ServiceCard(
    server: CatalogServer,
    selection: McpSelection,
    status: ServerStatus?,
    onChange: (McpSelection) -> Unit,
    onTest: (suspend () -> Pair<List<ServerStatus>, List<String>>)? = null,
) {
    val enabled = server.id in selection.enabled
    val uri = LocalUriHandler.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var testing by remember(server.id) { mutableStateOf(false) }
    var testResult by remember(server.id) { mutableStateOf<ServerStatus?>(null) }
    var testProblem by remember(server.id) { mutableStateOf<String?>(null) }
    // Key 一改，上一次的测试结论就不再作数，直接清掉，避免「缺 Key」的旧提示一直挂着。
    val keySignature = server.keys.joinToString("|") { selection.keys[it.name].orEmpty() }
    LaunchedEffect(keySignature) {
        testProblem = null
        testResult = null
    }
    SectionCard {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MonogramTile(server.title, size = 40.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(server.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Pill(
                        stringResource(if (server.official) R.string.mcp_official else R.string.mcp_community),
                        container = MaterialTheme.colorScheme.primaryContainer,
                        content = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Text(
                    server.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = enabled, onCheckedChange = { on ->
                onChange(selection.copy(enabled = if (on) selection.enabled + server.id else selection.enabled - server.id))
            })
        }
        if (enabled) {
            server.keys.forEach { key ->
                OutlinedTextField(
                    value = selection.keys[key.name].orEmpty(),
                    onValueChange = { onChange(selection.copy(keys = selection.keys + (key.name to it.trim()))) },
                    label = { Text(key.label) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
                key.help?.let { help ->
                    TextButton(onClick = { uri.openUri(help) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                        Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.size(15.dp))
                        Text(stringResource(R.string.mcp_apply_key), modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
            val missing = server.keys.filter { selection.keys[it.name].isNullOrBlank() }.map { it.label }
            if (missing.isNotEmpty()) {
                Text(
                    stringResource(R.string.mcp_missing_key, missing.joinToString("、")),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (onTest != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(
                        enabled = !testing,
                        onClick = {
                            // 缺 Key 时直接提示，不去发请求。
                            if (missing.isNotEmpty()) {
                                testResult = null
                                testProblem = context.getString(R.string.mcp_test_need_key, missing.joinToString("、"))
                                return@OutlinedButton
                            }
                            testProblem = null
                            testResult = null
                            testing = true
                            scope.launch {
                                try {
                                    val (statuses, warnings) = onTest()
                                    testResult = statuses.firstOrNull()
                                    if (statuses.isEmpty()) {
                                        testProblem = warnings.joinToString("；")
                                            .ifBlank { context.getString(R.string.mcp_test_nothing) }
                                    }
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Throwable) {
                                    testProblem = context.getString(
                                        R.string.mcp_test_failed,
                                        (e.message ?: e.javaClass.simpleName).take(160),
                                    )
                                } finally {
                                    testing = false
                                }
                            }
                        },
                        modifier = Modifier.height(36.dp),
                    ) {
                        Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(
                            stringResource(if (testing) R.string.mcp_testing else R.string.mcp_test_this),
                            modifier = Modifier.padding(start = 6.dp),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
                testProblem?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                testResult?.let { StatusLine(it) }
            }
        }
        status?.let { StatusLine(it) }
    }
}

@Composable
private fun StatusLine(status: ServerStatus) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        StatusDot(if (status.connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        Text(
            if (status.connected) {
                stringResource(R.string.mcp_connected, status.toolCount)
            } else {
                stringResource(R.string.mcp_failed, status.error ?: "?")
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (status.connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun CustomServers(selection: McpSelection, status: Map<String, ServerStatus>, onChange: (McpSelection) -> Unit) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var auth by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(ConfirmPolicy.WRITES) }
    val problem = url.takeIf { it.isNotBlank() }?.let { McpCatalog.validateCustomUrl(it) }

    SectionLabel(stringResource(R.string.mcp_custom_title))
    Text(
        stringResource(R.string.mcp_custom_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    selection.custom.forEachIndexed { i, c ->
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconTile(Icons.Outlined.Link, size = 36.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(c.title.ifBlank { c.url }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        c.url.substringBefore('?'),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TextButton(onClick = { onChange(selection.copy(custom = selection.custom.filterIndexed { j, _ -> j != i })) }) {
                    Text(stringResource(R.string.mcp_remove), color = MaterialTheme.colorScheme.error)
                }
            }
            status["custom-${i + 1}"]?.let { StatusLine(it) }
        }
    }
    OutlinedTextField(
        value = title,
        onValueChange = { title = it },
        label = { Text(stringResource(R.string.mcp_custom_name)) },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = url,
        onValueChange = { url = it.trim() },
        label = { Text(stringResource(R.string.mcp_custom_url)) },
        isError = problem != null,
        supportingText = { problem?.let { Text(stringResource(R.string.mcp_custom_invalid, it)) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = auth,
        onValueChange = { auth = it },
        label = { Text(stringResource(R.string.mcp_custom_auth)) },
        visualTransformation = PasswordVisualTransformation(),
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    )
    Text(stringResource(R.string.mcp_custom_confirm), style = MaterialTheme.typography.bodyMedium)
    // 三选一改成一列单选：横向三个胶囊在窄屏会被挤到换行。
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        listOf(
            ConfirmPolicy.WRITES to R.string.mcp_confirm_writes,
            ConfirmPolicy.ALL to R.string.mcp_confirm_all,
            ConfirmPolicy.NONE to R.string.mcp_confirm_none,
        ).forEach { (policy, label) ->
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .selectable(
                        selected = confirm == policy,
                        role = Role.RadioButton,
                        onClick = { confirm = policy },
                    )
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = confirm == policy, onClick = null)
                Text(
                    stringResource(label),
                    modifier = Modifier.padding(start = 6.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
    AssistChip(
        enabled = url.isNotBlank() && problem == null,
        onClick = {
            val headers = auth.trim().takeIf { it.isNotEmpty() }?.let { mapOf("Authorization" to it) }.orEmpty()
            onChange(selection.copy(custom = selection.custom + CustomMcpServer(title.trim(), url, headers, confirm)))
            title = ""; url = ""; auth = ""; confirm = ConfirmPolicy.WRITES
        },
        label = { Text(stringResource(R.string.mcp_custom_add)) },
    )
}
