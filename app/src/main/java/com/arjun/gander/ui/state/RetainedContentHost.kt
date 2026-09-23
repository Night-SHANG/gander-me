package com.arjun.gander.ui.state

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.arjun.gander.R

/** Loading chrome exists only before the first successful result, never on tab reentry. */
@Composable
fun <T : Any> RetainedContentHost(
    state: RetainedContent<T>,
    content: @Composable (T) -> Unit,
) {
    val snapshot by state.snapshots.collectAsState()
    Box(Modifier.fillMaxSize()) {
        val value = snapshot.value
        if (value != null) {
            content(value)
        } else if (!snapshot.failed) {
            val description = stringResource(R.string.vaultshelf_content_loading)
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp)
                    .semantics { contentDescription = description },
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(0.55f).height(32.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {}
                repeat(3) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().height(76.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {}
                }
            }
        }
        if (snapshot.failed) {
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                TextButton(onClick = state::refresh) {
                    Text(stringResource(R.string.vaultshelf_content_retry))
                }
            }
        }
    }
}
