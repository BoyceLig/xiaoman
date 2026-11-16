package dev.xiaoman.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.xiaoman.api.Skill
import dev.xiaoman.app.AppGraph
import dev.xiaoman.app.R
import dev.xiaoman.app.session.AgentSession
import dev.xiaoman.hands.XiaomanAccessibilityService

@Composable
fun ChatScreen(
    graph: AppGraph,
    onOpenSettings: () -> Unit,
    pendingDraft: String? = null,
    onDraftConsumed: () -> Unit = {},
) {
    val session = graph.session
    val items by session.items.collectAsStateWithLifecycle()
    val busy by session.busy.collectAsStateWithLifecycle()
    val status by session.status.collectAsStateWithLifecycle()
    val question by session.question.collectAsStateWithLifecycle()
    val approval by session.approval.collectAsStateWithLifecycle()
    val settings by graph.settings.settings.collectAsStateWithLifecycle()
    val service by XiaomanAccessibilityService.instance.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val notificationsGranted = rememberNotificationPermission()
    var draft by rememberSaveable { mutableStateOf("") }
    // 技能选择：进入聊天页时加载一次，供输入框「/」快捷选择使用。
    var skills by remember { mutableStateOf<List<Skill>>(emptyList()) }
    LaunchedEffect(Unit) {
        runCatching { graph.startup.await() }
        skills = runCatching { graph.skills.list() }.getOrDefault(emptyList())
    }
    // A draft handed over from the skills screen: prefill the input once (no auto-send) and clear the handoff.
    LaunchedEffect(pendingDraft) {
        if (!pendingDraft.isNullOrEmpty()) {
            draft = pendingDraft
            onDraftConsumed()
        }
    }
    val rows = remember(items) { chatTimeline(items) }
    val listState = rememberLazyListState()
    LaunchedEffect(rows.size) {
        if (rows.isNotEmpty()) listState.animateScrollToItem(rows.lastIndex)
    }
    LaunchedEffect(approval?.id) {
        if (approval != null) { focus.clearFocus(); keyboard?.hide() }
    }
    Column(Modifier.fillMaxSize()) {
        ChatHeader(ready = settings.configured && service != null, busy = busy,
            attention = approval != null || question != null)
        if (service == null) {
            Banner(Icons.Outlined.AccessibilityNew, stringResource(R.string.banner_accessibility), stringResource(R.string.banner_accessibility_action)) {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        if (!settings.configured) {
            Banner(Icons.Outlined.CloudOff, stringResource(R.string.banner_models), stringResource(R.string.banner_models_action), onOpenSettings)
        }
        if (!notificationsGranted.granted) {
            Banner(Icons.Outlined.NotificationsNone, stringResource(R.string.banner_notifications), stringResource(R.string.banner_notifications_action), notificationsGranted.request)
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = ScreenPadding, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (items.isEmpty()) item { ChatWelcome { draft = it } }
            items(rows, key = { it.id }) { row ->
                when (row) {
                    is TimelineRow.Message -> ChatMessage(row.item)
                    is TimelineRow.Process -> ProcessCard(row.entries)
                }
            }
        }
        if (approval != null) {
            ApprovalCard(approval!!, session)
        } else {
            if (busy) TaskStatus(status ?: "正在想办法…", session::stop)
            ChatInputBar(
                text = draft,
                onTextChange = { draft = it },
                busy = busy,
                answering = question != null,
                skills = skills,
                onPickSkill = { skill -> draft = skillCall(skill) },
                // Read the draft at click time: a captured String would keep the first frame's value.
                onSend = {
                    val message = draft.trim()
                    if (message.isNotEmpty() && (!busy || question != null)) {
                        when (session.submit(message)) {
                            AgentSession.Submit.Busy, AgentSession.Submit.Empty -> Unit
                            else -> {
                                draft = ""
                                focus.clearFocus()
                                keyboard?.hide()
                            }
                        }
                    }
                },
                onStop = session::stop,
            )
        }
    }
}

/** 选中技能后填进输入框的调用语句；技能名会让 agent 在技能索引里精确命中。 */
private fun skillCall(skill: Skill): String = "用技能 ${skill.name}："

/** Stateless composer bar, shared by the chat screen and Compose previews. */
@Composable
internal fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    busy: Boolean,
    answering: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    skills: List<Skill> = emptyList(),
    onPickSkill: (Skill) -> Unit = {},
) {
    val stopMode = busy && !answering
    val canSend = text.isNotBlank() && !stopMode
    val pickerQuery = if (text.startsWith("/")) text.drop(1).trim() else null
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxWidth()) {
            if (pickerQuery != null && !stopMode) {
                SkillPicker(skills = skills, query = pickerQuery, onPick = onPickSkill)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            if (answering) "告诉我就好…" else "说说你想做什么，输入 / 选技能",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                        )
                    },
                    shape = RoundedCornerShape(22.dp),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    minLines = 1,
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend() }),
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = { if (stopMode) onStop() else onSend() },
                    enabled = stopMode || canSend,
                    modifier = Modifier.padding(bottom = 4.dp).size(48.dp),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Icon(
                        if (stopMode) Icons.Default.Stop else Icons.Default.ArrowUpward,
                        contentDescription = stringResource(if (stopMode) R.string.chat_stop else R.string.chat_send),
                    )
                }
            }
        }
    }
}

/** 输入框「/」唤出的技能选择面板：按名称或描述过滤，点一行即填进输入框。 */
@Composable
private fun SkillPicker(skills: List<Skill>, query: String, onPick: (Skill) -> Unit) {
    val matched = remember(skills, query) {
        if (query.isEmpty()) {
            skills
        } else {
            skills.filter { it.name.contains(query, ignoreCase = true) || it.description.contains(query, ignoreCase = true) }
        }
    }
    Column(Modifier.fillMaxWidth().heightIn(max = 260.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (query.isEmpty()) "选择技能" else "匹配「$query」",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text("${matched.size} 个", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
        }
        if (matched.isEmpty()) {
            Text(
                if (skills.isEmpty()) "还没有技能，去「技能」页看看" else "没有匹配的技能",
                Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn {
                items(matched, key = { it.name }) { skill ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { onPick(skill) }
                            .padding(horizontal = 18.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MonogramTile(skill.name, size = 30.dp)
                        Column(Modifier.weight(1f).padding(start = 11.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(skill.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1)
                            Text(
                                skill.description,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Banner(icon: ImageVector, text: String, action: String, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenPadding, vertical = 4.dp),
    ) {
        Row(
            Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(18.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
            )
            TextButton(onClick = onClick) {
                Text(action, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
        }
    }
}

private class NotificationPermission(val granted: Boolean, val request: () -> Unit)

/** POST_NOTIFICATIONS on Android 13+: asked once automatically, then via the banner. */
@Composable
private fun rememberNotificationPermission(): NotificationPermission {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return NotificationPermission(true) {}
    val context = LocalContext.current
    fun check() = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    var granted by remember { mutableStateOf(check()) }
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) granted = check() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) {
        if (!granted && !asked) {
            asked = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    return NotificationPermission(granted) { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
}
