package dev.xiaoman.app.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.xiaoman.api.MemoryStore
import dev.xiaoman.api.Note
import dev.xiaoman.app.AppGraph
import dev.xiaoman.app.R
import dev.xiaoman.memory.BasicMemoryFormat
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A row of the memory list: every note when the query is blank, search hits otherwise. */
data class MemoryRow(val permalink: String, val title: String, val detail: String)

data class MemoryState(
    val query: String = "",
    val rows: List<MemoryRow> = emptyList(),
    val loading: Boolean = true,
    /** The opened note, rendered as Markdown text. */
    val open: Note? = null,
    val openMarkdown: String = "",
    val openMissing: Boolean = false,
)

@OptIn(FlowPreview::class)
class MemoryViewModel(private val memory: MemoryStore) : ViewModel() {
    private val _state = MutableStateFlow(MemoryState())
    val state: StateFlow<MemoryState> = _state.asStateFlow()
    private val query = MutableStateFlow("")

    init {
        viewModelScope.launch { query.debounce(250).collect { refresh() } }
    }

    fun setQuery(text: String) {
        _state.update { it.copy(query = text) }
        query.value = text
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val q = _state.value.query.trim()
            val rows = runCatching {
                if (q.isEmpty()) {
                    memory.list().sortedByDescending { it.updatedAtMillis }.map { MemoryRow(it.permalink, it.title, it.permalink) }
                } else {
                    memory.search(q, limit = 50).map { MemoryRow(it.permalink, it.title, it.snippet.replace('\n', ' ')) }
                }
            }.getOrDefault(emptyList())
            _state.update { it.copy(rows = rows, loading = false) }
        }
    }

    fun open(permalink: String) {
        viewModelScope.launch {
            val note = runCatching { memory.read(permalink) }.getOrNull()
            _state.update { it.copy(open = note, openMarkdown = note?.let(BasicMemoryFormat::render).orEmpty(), openMissing = note == null) }
        }
    }

    fun close() = _state.update { it.copy(open = null, openMarkdown = "", openMissing = false) }

    fun delete(permalink: String) {
        viewModelScope.launch {
            runCatching { memory.delete(permalink) }
            close()
            refresh()
        }
    }
}

@Composable
fun MemoryScreen(graph: AppGraph) {
    val vm = viewModel { MemoryViewModel(graph.memory) }
    val state by vm.state.collectAsStateWithLifecycle()
    val note = state.open
    if (note != null) NoteView(note, state.openMarkdown, onBack = vm::close, onDelete = { vm.delete(note.permalink) }) else MemoryList(state, vm)
}

@Composable
private fun MemoryList(state: MemoryState, vm: MemoryViewModel) {
    LaunchedEffect(Unit) { vm.refresh() }
    MemoryListContent(
        state = state,
        onQuery = vm::setQuery,
        onClear = { vm.setQuery("") },
        onOpen = vm::open,
    )
}

@Composable
internal fun MemoryListContent(
    state: MemoryState,
    onQuery: (String) -> Unit,
    onClear: () -> Unit,
    onOpen: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "记忆",
            subtitle = when {
                state.loading -> "正在读取…"
                state.query.isBlank() -> "${state.rows.size} 条笔记"
                else -> "${state.rows.size} 条搜索结果"
            },
            eyebrow = "memory",
        )
        TextField(
            value = state.query,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenPadding),
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    TextButton(onClick = onClear) { Text(stringResource(R.string.clear)) }
                }
            },
            placeholder = { Text(stringResource(R.string.memory_search_hint), style = MaterialTheme.typography.bodyMedium) },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        )
        if (state.loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = ScreenPadding, vertical = 8.dp))
        }
        if (!state.loading && state.rows.isEmpty()) {
            EmptyState(
                icon = if (state.query.isBlank()) Icons.Outlined.Psychology else Icons.Outlined.SearchOff,
                title = stringResource(if (state.query.isBlank()) R.string.memory_empty else R.string.memory_no_hits),
                body = if (state.query.isBlank()) "和小满聊天时让它记住的事，会出现在这里。" else "换个关键词试试，或直接告诉小满去记。",
            )
        }
        if (state.openMissing) {
            Text(
                stringResource(R.string.memory_not_found),
                modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 8.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = ScreenPadding, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(state.rows, key = { it.permalink }) { row ->
                Surface(
                    onClick = { onOpen(row.permalink) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconTile(Icons.Outlined.Description, size = 36.dp)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                row.title,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                row.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Icon(
                            Icons.Outlined.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteView(note: Note, markdown: String, onBack: () -> Unit, onDelete: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
        ) {
            FilledTonalIconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(note.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("记忆笔记", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { confirm = true }) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.memory_delete),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        SelectionContainer(Modifier.weight(1f)) {
            Surface(
                modifier = Modifier.fillMaxSize().padding(horizontal = ScreenPadding, vertical = 8.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(
                    Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(markdown, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.memory_delete_title)) },
            text = { Text(stringResource(R.string.memory_delete_message, note.title)) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    onDelete()
                }) { Text(stringResource(R.string.memory_delete_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
