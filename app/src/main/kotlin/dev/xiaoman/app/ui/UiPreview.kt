package dev.xiaoman.app.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.xiaoman.api.ModelRole
import dev.xiaoman.api.Skill
import dev.xiaoman.app.session.ChatItem
import dev.xiaoman.app.settings.SettingsForm
import dev.xiaoman.mcp.CatalogServer
import dev.xiaoman.mcp.McpSelection
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/*
 * Compose Previews: open this file in Android Studio and switch to the Split / Design tab.
 * No emulator or phone is needed. Combined Preview (top-right) renders every preview at once.
 */

private const val PhoneWidth = 412
private const val PhoneHeight = 892

@Preview(name = "聊天 · 首页", widthDp = PhoneWidth, heightDp = PhoneHeight, showBackground = true)
@Composable
private fun ChatHomePreview() = XiaomanTheme { ChatHome() }

@Preview(
    name = "聊天 · 对话",
    widthDp = PhoneWidth,
    heightDp = PhoneHeight,
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ChatConversationPreview() = XiaomanTheme { ChatConversation() }

@Preview(name = "技能", widthDp = PhoneWidth, heightDp = PhoneHeight, showBackground = true)
@Composable
private fun SkillsPreview() = XiaomanTheme {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        SkillsContent(
            state = SkillsState(skills = demoSkills, loading = false),
            skillsDir = "/sdcard/Android/data/dev.xiaoman.app/files/skills",
            onReload = {},
            onAuthorSkill = {},
        )
    }
}

@Preview(name = "记忆", widthDp = PhoneWidth, heightDp = PhoneHeight, showBackground = true)
@Composable
private fun MemoryPreview() = XiaomanTheme {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        MemoryListContent(
            state = MemoryState(
                rows = listOf(
                    MemoryRow("home-address", "家的位置", "北京市海淀区中关村大街 1 号"),
                    MemoryRow("coffee", "咖啡偏好", "只喝美式，不加糖"),
                    MemoryRow("note-2026-11", "周末计划", "周六上午去图书馆，下午买菜"),
                ),
                loading = false,
            ),
            onQuery = {},
            onClear = {},
            onOpen = {},
        )
    }
}

@Preview(name = "服务卡片", widthDp = PhoneWidth, showBackground = true)
@Composable
private fun ServiceCardPreview() = XiaomanTheme {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        ServiceCard(
            server = demoServer,
            selection = McpSelection(enabled = setOf("amap")),
            status = null,
            onChange = {},
        )
    }
}

@Preview(name = "设置 · 模型卡片", widthDp = PhoneWidth, showBackground = true)
@Composable
private fun ModelSectionPreview() = XiaomanTheme {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(ScreenPadding)) {
            ModelSection(
                title = "聊天模型",
                subtitle = "理解你的话，决定要做什么",
                icon = Icons.AutoMirrored.Outlined.Chat,
                model = SettingsForm.ModelForm(baseUrl = "https://ark.cn-beijing.volces.com/api/v3", model = "doubao-pro-32k"),
                role = ModelRole.CHAT,
                fetchModels = { _, _, _ -> throw IllegalStateException("preview") },
                probe = { _, _ -> throw IllegalStateException("preview") },
                onChange = {},
            )
        }
    }
}

@Composable
private fun ChatHome() {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            ChatHeader(ready = true, busy = false, attention = false)
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = ScreenPadding, vertical = 16.dp),
            ) {
                item { ChatWelcome {} }
            }
            ChatInputBar("", {}, busy = false, answering = false, onSend = {}, onStop = {})
        }
    }
}

@Composable
private fun ChatConversation() {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            ChatHeader(ready = true, busy = true, attention = false)
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = ScreenPadding, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                item { ChatMessage(ChatItem.User(1, "帮我在淘宝挑一本 20 元以内的 A5 笔记本，选好后给我确认。")) }
                item {
                    ChatMessage(
                        ChatItem.Assistant(
                            2,
                            "好的，我先打开淘宝搜「A5笔记本」，筛掉 **20 元以上**的，再把评价好的三本给你看。",
                        ),
                    )
                }
                item {
                    ProcessCard(
                        listOf(
                            ChatItem.Progress(3, "使用技能 shopping-taobao"),
                            ChatItem.Progress(4, "1. 打开 com.taobao.taobao", true),
                            ChatItem.Progress(5, "2. 输入「A5笔记本」", true),
                            ChatItem.Progress(6, "3. 点击 筛选", false),
                        ),
                    )
                }
                item { ChatMessage(ChatItem.Question(7, "要优先看销量高的，还是评价更好的？", false)) }
            }
            TaskStatus("第 4 步：点击「价格从低到高」", onStop = {})
            ChatInputBar("", {}, busy = true, answering = false, onSend = {}, onStop = {})
        }
    }
}

private val demoSkills = listOf(
    Skill(
        name = "taxi-home",
        description = "在高德地图里叫车回家，地址从记忆里取。",
        apps = listOf("com.autonavi.minimap"),
        instructions = "",
        source = "/skills/taxi-home",
    ),
    Skill(
        name = "shopping-taobao",
        description = "按预算和条件在淘宝挑选商品，下单前先给你确认。",
        apps = listOf("com.taobao.taobao"),
        instructions = "",
        source = "/skills/shopping-taobao",
    ),
    Skill(
        name = "open-settings-wifi",
        description = "打开系统设置里的 Wi-Fi 页面。",
        instructions = "",
        source = "/skills/open-settings-wifi",
    ),
)

private val demoServer = CatalogServer(
    id = "amap",
    title = "高德地图",
    category = "出行",
    description = "路线规划、周边搜索、打车比价。",
    official = true,
    entry = buildJsonObject { put("url", "https://mcp.amap.com/sse") },
    keys = emptyList(),
    platforms = setOf("android", "ios", "desktop"),
)
