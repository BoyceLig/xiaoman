package dev.xiaoman.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.xiaoman.app.session.ChatItem

@Composable
internal fun BrandMark(size: Dp = 40.dp) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.32f))
            .background(scheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.AutoAwesome,
            contentDescription = null,
            tint = scheme.onPrimary,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}

@Composable
internal fun ChatHeader(ready: Boolean, busy: Boolean, attention: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(start = ScreenPadding, end = ScreenPadding, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrandMark(42.dp)
        Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("小满", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("你的手机小助手", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val label = when {
            attention -> "等你确认"
            busy -> "处理中"
            ready -> "已就绪"
            else -> "待设置"
        }
        val tint = when {
            attention -> MaterialTheme.colorScheme.tertiary
            busy || ready -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        Surface(
            color = Color.Transparent,
            shape = CircleShape,
            border = BorderStroke(1.dp, tint.copy(alpha = 0.3f)),
        ) {
            Row(
                Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                StatusDot(tint, size = 6.dp)
                Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
            }
        }
    }
}

@Composable
internal fun ChatWelcome(onExample: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text("今天想做点什么？", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "说一句，我来帮你动手。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))
        Text(
            "可以这样开始",
            Modifier.padding(start = 2.dp, bottom = 10.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
        )
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(Modifier.fillMaxWidth()) {
                Example(Icons.Outlined.ShoppingBag, "选一本笔记本", "20元以内，先给我确认", "帮我在淘宝挑一本20元以内的A5笔记本，选好后给我确认。", onExample)
                HorizontalDivider(Modifier.padding(start = 50.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
                Example(Icons.Outlined.Schedule, "提醒我喝水", "10分钟后提醒", "10分钟后提醒我喝水。", onExample)
                HorizontalDivider(Modifier.padding(start = 50.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
                Example(Icons.Outlined.Lightbulb, "记住我的偏好", "让助手慢慢懂你", "记住我买东西喜欢简单实用。", onExample)
            }
        }
    }
}

@Composable
private fun Example(icon: ImageVector, title: String, detail: String, prompt: String, onExample: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clickable { onExample(prompt) }
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f).padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(17.dp),
        )
    }
}

@Composable
internal fun TaskStatus(status: String, onStop: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenPadding, vertical = 4.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(
            Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
            Text(
                status,
                Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(onClick = onStop) { Text("停止", style = MaterialTheme.typography.labelMedium) }
        }
    }
}

@Composable
internal fun ProcessCard(entries: List<ChatItem.Progress>) {
    var expanded by rememberSaveable(entries.first().id) { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        shadowElevation = 1.dp,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { expanded = !expanded }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.ListAlt,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("处理过程", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                Pill("${entries.size} 条")
                Icon(
                    if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    if (expanded) "收起处理过程" else "展开处理过程",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (expanded) {
                Column(
                    Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    entries.forEach { entry ->
                        val tint = when (entry.ok) {
                            true -> MaterialTheme.colorScheme.primary
                            false -> MaterialTheme.colorScheme.error
                            null -> MaterialTheme.colorScheme.outline
                        }
                        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusDot(tint, size = 6.dp)
                            Text(
                                entry.text,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (entry.ok == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ChatMessage(item: ChatItem) {
    when (item) {
        is ChatItem.User -> Row(Modifier.fillMaxWidth().padding(start = 44.dp), horizontalArrangement = Arrangement.End) {
            Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp)) {
                Text(
                    item.text,
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
        is ChatItem.Assistant -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BrandMark(28.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "小满",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(6.dp, 20.dp, 20.dp, 20.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Text(chatRichText(item.text), Modifier.padding(horizontal = 16.dp, vertical = 13.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        is ChatItem.Question -> NoticeCard(
            icon = if (item.takeover) Icons.Outlined.PanTool else Icons.AutoMirrored.Outlined.HelpOutline,
            badge = if (item.takeover) "需要你操作" else "等你补充",
            title = if (item.takeover) "需要你帮一下" else "再告诉我一点",
            body = item.text,
            accent = MaterialTheme.colorScheme.tertiary,
            container = MaterialTheme.colorScheme.tertiaryContainer,
            content = MaterialTheme.colorScheme.onTertiaryContainer,
        )
        is ChatItem.Error -> NoticeCard(
            icon = Icons.Outlined.ErrorOutline,
            badge = "未完成",
            title = "这次没完成",
            body = item.text,
            accent = MaterialTheme.colorScheme.error,
            container = MaterialTheme.colorScheme.errorContainer,
            content = MaterialTheme.colorScheme.onErrorContainer,
        )
        is ChatItem.Progress -> Unit // handled by ProcessCard
    }
}

@Composable
private fun NoticeCard(
    icon: ImageVector,
    badge: String,
    title: String,
    body: String,
    accent: Color,
    container: Color,
    content: Color,
) {
    Surface(
        color = container,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.5f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = accent, shape = CircleShape) {
                    Text(
                        badge,
                        Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(17.dp))
            }
            Text(title, style = MaterialTheme.typography.titleSmall, color = content)
            Text(chatRichText(body), style = MaterialTheme.typography.bodyMedium, color = content)
        }
    }
}

/** Basic emphasis and lists from model replies, rendered as text rather than raw Markdown markers. */
internal fun chatRichText(raw: String) = buildAnnotatedString {
    val text = raw.replace(Regex("(?m)^#{1,6}\\s+"), "").replace(Regex("(?m)^[-*]\\s+"), "• ")
    var position = 0
    Regex("\\*\\*(.+?)\\*\\*").findAll(text).forEach { match ->
        append(text.substring(position, match.range.first))
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(match.groupValues[1]) }
        position = match.range.last + 1
    }
    append(text.substring(position))
}
