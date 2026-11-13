package dev.xiaoman.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.xiaoman.api.Skill
import dev.xiaoman.api.SkillChannel
import dev.xiaoman.api.SkillProblem
import dev.xiaoman.api.SkillRepository
import dev.xiaoman.app.AppGraph
import dev.xiaoman.app.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SkillsState(val skills: List<Skill> = emptyList(), val problems: List<SkillProblem> = emptyList(), val loading: Boolean = true)

class SkillsViewModel(
    private val repository: SkillRepository,
    private val ready: suspend () -> Unit,
    private val onReloaded: (Int, List<SkillProblem>) -> Unit,
) : ViewModel() {
    private val _state = MutableStateFlow(SkillsState())
    val state: StateFlow<SkillsState> = _state.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            ready()
            val result = repository.reload()
            onReloaded(result.skills.size, result.problems)
            _state.value = SkillsState(result.skills, result.problems, loading = false)
        }
    }
}

@Composable
fun SkillsScreen(graph: AppGraph, onAuthorSkill: (String) -> Unit) {
    val vm = viewModel { SkillsViewModel(graph.skills, { graph.startup.await() }, graph::logSkillProblems) }
    val state by vm.state.collectAsStateWithLifecycle()
    // Reload whenever the screen comes back to the foreground (the user may have pushed skills over adb).
    val lifecycleOwner = LocalLifecycleOwner.current
    var skipFirstResume by remember { mutableStateOf(true) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (skipFirstResume) skipFirstResume = false else vm.reload()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    SkillsContent(state, graph.skillsDir.path, vm::reload, onAuthorSkill)
}

@Composable
internal fun SkillsContent(state: SkillsState, skillsDir: String, onReload: () -> Unit, onAuthorSkill: (String) -> Unit) {
    val newSkillPrompt = stringResource(R.string.chat_new_skill_prompt)
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = stringResource(R.string.skills_title),
            subtitle = if (state.loading) "正在加载…" else "已加载 ${state.skills.size} 个技能",
            eyebrow = "skills",
            actions = {
                FilledTonalIconButton(onClick = { onAuthorSkill(newSkillPrompt) }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.skills_new), modifier = Modifier.size(20.dp))
                }
                FilledTonalIconButton(onClick = onReload, enabled = !state.loading) {
                    Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.skills_reload), modifier = Modifier.size(20.dp))
                }
            },
        )
        if (state.loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = ScreenPadding, vertical = 4.dp))
        }
        LazyColumn(
            contentPadding = PaddingValues(horizontal = ScreenPadding, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!state.loading && state.skills.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.Extension,
                        title = "还没有技能",
                        body = stringResource(R.string.skills_empty, skillsDir),
                    )
                }
            }
            items(state.skills, key = { it.name }) { skill ->
                val isUserSkill = skill.source.startsWith(skillsDir)
                val editPrompt = stringResource(R.string.chat_edit_skill_prompt, skill.name)
                SkillCard(skill, isUserSkill) { onAuthorSkill(editPrompt) }
            }
            if (state.problems.isNotEmpty()) {
                item { SectionLabel(stringResource(R.string.skills_problems)) }
                items(state.problems) { problem -> ProblemCard(problem) }
            }
        }
    }
}

@Composable
private fun SkillCard(skill: Skill, isUserSkill: Boolean, onEdit: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MonogramTile(skill.name, size = 42.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        skill.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isUserSkill) {
                        Pill(stringResource(R.string.skills_mine), container = MaterialTheme.colorScheme.primaryContainer, content = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    channelLabel(skill.channel)?.let { Pill(it) }
                }
                Text(
                    skill.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (skill.apps.isNotEmpty()) {
                    Text(
                        stringResource(R.string.skills_apps, skill.apps.joinToString("、")),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    skill.source.substringAfterLast('/'),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (isUserSkill) {
                FilledTonalIconButton(onClick = onEdit, modifier = Modifier.align(Alignment.CenterVertically).size(34.dp)) {
                    Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.skills_edit), modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

private fun channelLabel(channel: SkillChannel): String? = when (channel) {
    SkillChannel.GUI -> null
    SkillChannel.API -> "接口"
    SkillChannel.CLI -> "命令"
    SkillChannel.DEEPLINK -> "直达"
    SkillChannel.DEEPLINK_THEN_GUI -> "直达"
    SkillChannel.MIXED -> "混合"
    SkillChannel.CHAT -> "对话"
}

@Composable
private fun ProblemCard(problem: SkillProblem) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(
                Icons.Outlined.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(18.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    problem.source + (problem.line?.let { ":$it" } ?: ""),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(problem.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}
