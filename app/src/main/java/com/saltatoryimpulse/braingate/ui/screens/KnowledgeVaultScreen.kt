package com.saltatoryimpulse.braingate.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saltatoryimpulse.braingate.KnowledgeEntry
import com.saltatoryimpulse.braingate.data.IKnowledgeRepository
import org.koin.androidx.compose.get
import com.saltatoryimpulse.braingate.ui.components.BrainGateTopBar
import com.saltatoryimpulse.braingate.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KnowledgeVaultScreen(onBack: () -> Unit) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val repository: IKnowledgeRepository = get()
    val scope = rememberCoroutineScope()

    // collectAsStateWithLifecycle prevents the Room Flow
    // from querying the DB while the app is backgrounded.
    val history by repository.getAllEntries()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    var entryToDelete by remember { mutableStateOf<KnowledgeEntry?>(null) }

    Scaffold(
        containerColor = BackgroundDark,
        topBar = { BrainGateTopBar(onBack = onBack, title = "REFLECTION LOG") }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (history.isEmpty()) {
                EmptyVaultState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = history,
                        key = { it.id } // Stable keys prevent unnecessary item re-draws
                    ) { entry ->
                        KnowledgeCard(
                            entry = entry,
                            onLongClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                entryToDelete = entry
                            }
                        )
                    }
                }
            }
        }
    }

    DeleteEntryDialog(
        entry = entryToDelete,
        onDismiss = { entryToDelete = null },
        onConfirm = {
            entryToDelete?.let { entry ->
                scope.launch {
                    // Deletion is an atomic write. NonCancellable ensures
                    // the entry is purged even if the user exits the screen mid-write.
                    withContext(Dispatchers.IO + NonCancellable) {
                        repository.deleteEntry(entry)
                    }
                    entryToDelete = null
                }
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KnowledgeCard(entry: KnowledgeEntry, onLongClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            // graphicsLayer hints to the GPU to cache this card's visual state
            .graphicsLayer(clip = true)
            .combinedClickable(
                onClick = { /* Optional: Navigate to full-screen detail */ },
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = androidx.compose.foundation.BorderStroke(1.dp, OutlineSubtle)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    ),
                    color = PrimaryAccent
                )
                Text(
                    text = DateFormat.getDateInstance(DateFormat.SHORT).format(entry.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMediumEmphasis
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = OutlineSubtle, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = entry.summary,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                color = TextHighEmphasis
            )
        }
    }
}

@Composable
private fun EmptyVaultState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "THE LOG IS EMPTY",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
            color = TextMediumEmphasis
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Your mindful reflections will appear here after you unlock a gate.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = TextMediumEmphasis.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun DeleteEntryDialog(entry: KnowledgeEntry?, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    if (entry != null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = SurfaceDark,
            title = { Text("Purge Entry?") },
            text = { Text("This reflection will be permanently removed from your log.") },
            confirmButton = {
                TextButton(onClick = onConfirm) {
                    Text("PURGE", color = ErrorRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("CANCEL", color = TextMediumEmphasis)
                }
            }
        )
    }
}