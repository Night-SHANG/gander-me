package com.arjun.gander.ui.reader

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arjun.gander.R
import io.legado.app.constant.PageAnim
import kotlinx.coroutines.launch

private enum class ReaderSettingsPage(@StringRes val labelRes: Int) {
    PAGE_TURN(R.string.vaultshelf_reader_turn_mode),
    FONT(R.string.vaultshelf_reader_font_size),
    LAYOUT(R.string.vaultshelf_reader_layout),
    THEME(R.string.vaultshelf_reader_theme),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> MatureReaderSettingsSheet(
    pageAnimation: Int,
    fontSizeSp: Float,
    lineSpacing: Float,
    marginDp: Int,
    themeMode: T,
    themeChoices: List<Pair<String, T>>,
    onPageAnimation: (Int) -> Unit,
    onFontSize: (Float) -> Unit,
    onLineSpacing: (Float) -> Unit,
    onMargin: (Int) -> Unit,
    onTheme: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    var pageName by rememberSaveable { mutableStateOf(ReaderSettingsPage.PAGE_TURN.name) }
    val page = remember(pageName) {
        runCatching { ReaderSettingsPage.valueOf(pageName) }
            .getOrDefault(ReaderSettingsPage.PAGE_TURN)
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.vaultshelf_reader_settings),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ReaderSettingsPage.entries.forEach { item ->
                    val selected = item == page
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        onClick = { pageName = item.name },
                    ) {
                        Text(
                            text = stringResource(item.labelRes),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            maxLines = 1,
                        )
                    }
                }
            }

            HorizontalDivider()

            when (page) {
                ReaderSettingsPage.PAGE_TURN -> {
                    SettingsSectionTitle(stringResource(R.string.vaultshelf_reader_turn_mode))
                    ReaderChoiceRow(
                        choices = listOf(
                            stringResource(R.string.vaultshelf_reader_mode_cover) to PageAnim.coverPageAnim,
                            stringResource(R.string.vaultshelf_reader_mode_slide) to PageAnim.slidePageAnim,
                            stringResource(R.string.vaultshelf_reader_mode_simulation) to PageAnim.simulationPageAnim,
                        ),
                        selected = pageAnimation,
                        onSelected = onPageAnimation,
                    )
                    ReaderChoiceRow(
                        choices = listOf(
                            stringResource(R.string.vaultshelf_reader_mode_scroll) to PageAnim.scrollPageAnim,
                            stringResource(R.string.vaultshelf_reader_mode_none) to PageAnim.noAnim,
                        ),
                        selected = pageAnimation,
                        onSelected = onPageAnimation,
                    )
                }

                ReaderSettingsPage.FONT -> {
                    SettingsSectionTitle(stringResource(R.string.vaultshelf_reader_font_size))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            onClick = { onFontSize(fontSizeSp - 1f) },
                        ) {
                            Text(
                                text = stringResource(R.string.vaultshelf_reader_font_smaller),
                                modifier = Modifier.padding(vertical = 12.dp),
                                textAlign = TextAlign.Center,
                            )
                        }
                        Text(
                            text = stringResource(
                                R.string.vaultshelf_reader_font_value,
                                fontSizeSp.toInt(),
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            onClick = { onFontSize(fontSizeSp + 1f) },
                        ) {
                            Text(
                                text = stringResource(R.string.vaultshelf_reader_font_larger),
                                modifier = Modifier.padding(vertical = 12.dp),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }

                ReaderSettingsPage.LAYOUT -> {
                    SettingsSectionTitle(stringResource(R.string.vaultshelf_reader_line_spacing))
                    ReaderChoiceRow(
                        choices = listOf(
                            stringResource(R.string.vaultshelf_reader_compact) to 1.35f,
                            stringResource(R.string.vaultshelf_reader_standard) to 1.55f,
                            stringResource(R.string.vaultshelf_reader_relaxed) to 1.78f,
                        ),
                        selected = lineSpacing,
                        onSelected = onLineSpacing,
                    )
                    SettingsSectionTitle(stringResource(R.string.vaultshelf_reader_margins))
                    ReaderChoiceRow(
                        choices = listOf(
                            stringResource(R.string.vaultshelf_reader_narrow) to 16,
                            stringResource(R.string.vaultshelf_reader_standard) to 24,
                            stringResource(R.string.vaultshelf_reader_wide) to 36,
                        ),
                        selected = marginDp,
                        onSelected = onMargin,
                    )
                }

                ReaderSettingsPage.THEME -> {
                    SettingsSectionTitle(stringResource(R.string.vaultshelf_reader_theme))
                    ReaderChoiceRow(
                        choices = themeChoices,
                        selected = themeMode,
                        onSelected = onTheme,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatureReaderContentsSheet(
    chapterTitles: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filteredIndices = remember(chapterTitles, query) {
        val normalized = query.trim()
        chapterTitles.indices.filter { index ->
            normalized.isEmpty() || chapterTitles[index].contains(normalized, ignoreCase = true)
        }
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(selectedIndex, query, filteredIndices) {
        if (query.isBlank()) {
            val itemIndex = filteredIndices.indexOf(selectedIndex)
            if (itemIndex >= 0) listState.scrollToItem(itemIndex)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.72f)
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.vaultshelf_reader_contents),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (chapterTitles.isNotEmpty()) {
                    Text(
                        text = stringResource(
                            R.string.vaultshelf_reader_chapter_position,
                            (selectedIndex + 1).coerceAtMost(chapterTitles.size),
                            chapterTitles.size,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.vaultshelf_reader_contents_search)) },
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(
                    enabled = filteredIndices.isNotEmpty(),
                    onClick = {
                        if (filteredIndices.isNotEmpty()) {
                            scope.launch { listState.animateScrollToItem(0) }
                        }
                    },
                ) {
                    Text(stringResource(R.string.vaultshelf_reader_contents_first))
                }
                TextButton(
                    enabled = selectedIndex in chapterTitles.indices && query.isBlank(),
                    onClick = {
                        val itemIndex = filteredIndices.indexOf(selectedIndex)
                        if (itemIndex >= 0) {
                            scope.launch { listState.animateScrollToItem(itemIndex) }
                        }
                    },
                ) {
                    Text(stringResource(R.string.vaultshelf_reader_contents_current))
                }
                TextButton(
                    enabled = filteredIndices.isNotEmpty(),
                    onClick = {
                        if (filteredIndices.isNotEmpty()) {
                            scope.launch { listState.animateScrollToItem(filteredIndices.lastIndex) }
                        }
                    },
                ) {
                    Text(stringResource(R.string.vaultshelf_reader_contents_last))
                }
            }

            HorizontalDivider()

            if (filteredIndices.isEmpty()) {
                Text(
                    text = stringResource(R.string.vaultshelf_reader_contents_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(filteredIndices, key = { it }) { index ->
                        val selected = index == selectedIndex
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            onClick = { onSelect(index) },
                        ) {
                            Text(
                                text = chapterTitles[index],
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                                maxLines = 2,
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
private fun SettingsSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun <T> ReaderChoiceRow(
    choices: List<Pair<String, T>>,
    selected: T,
    onSelected: (T) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        choices.forEach { (label, value) ->
            val active = value == selected
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                color = if (active) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                onClick = { onSelected(value) },
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 11.dp),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
