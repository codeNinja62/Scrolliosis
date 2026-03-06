package com.saltatoryimpulse.scrolliosis.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
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
import com.saltatoryimpulse.scrolliosis.KnowledgeEntry
import com.saltatoryimpulse.scrolliosis.data.IKnowledgeRepository
import org.koin.androidx.compose.get
import com.saltatoryimpulse.scrolliosis.ui.components.ScrolliosisTopBar
import com.saltatoryimpulse.scrolliosis.ui.theme.*
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
    // BUG-04: flag to show the "add custom prompt" dialog
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = BackgroundDark,
        topBar = { ScrolliosisTopBar(onBack = onBack, title = "KNOWLEDGE VAULT") },
        // BUG-04: FAB lets users write their own prompts that appear in the Gatekeeper
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = PrimaryAccent,
                contentColor = BackgroundDark
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Add knowledge prompt")
            }
        }
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

    // BUG-04: dialog for adding user-authored knowledge prompts to the vault
    if (showAddDialog) {
        AddPromptDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { title, body ->
                scope.launch {
                    withContext(Dispatchers.IO + NonCancellable) {
                        repository.insertEntry(
                            KnowledgeEntry(title = title, summary = body, isCustomPrompt = true)
                        )
                    }
                    showAddDialog = false
                }
            }
        )
    }
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
            text = "THE VAULT IS EMPTY",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
            color = TextMediumEmphasis
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Tap + to write your own prompts — facts, goals, or questions that challenge you at the gate. Reflections saved during unlocks also appear here.",
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

// BUG-04: dialog for writing a user-authored knowledge prompt.
// "Title" = the label shown in the gatekeeper header (e.g. "Spanish Vocab", "Daily Goal").
// "Prompt" = the text shown to you at the gate — a question, fact, or challenge.
@Composable
private fun AddPromptDialog(onDismiss: () -> Unit, onConfirm: (title: String, body: String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    val canSave = title.isNotBlank() && body.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        title = {
            Text("ADD KNOWLEDGE PROMPT", fontWeight = FontWeight.Bold, color = TextHighEmphasis)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Write a prompt that will challenge you before accessing a blocked app.",
                    color = TextMediumEmphasis,
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Label (e.g. \"Daily Goal\")", color = TextMediumEmphasis) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryAccent,
                        unfocusedBorderColor = OutlineSubtle,
                        focusedTextColor = TextHighEmphasis,
                        unfocusedTextColor = TextHighEmphasis,
                        focusedContainerColor = BackgroundDark,
                        unfocusedContainerColor = BackgroundDark
                    )
                )
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("Your prompt or question", color = TextMediumEmphasis) },
                    minLines = 3,
                    maxLines = 6,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryAccent,
                        unfocusedBorderColor = OutlineSubtle,
                        focusedTextColor = TextHighEmphasis,
                        unfocusedTextColor = TextHighEmphasis,
                        focusedContainerColor = BackgroundDark,
                        unfocusedContainerColor = BackgroundDark
                    )
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(title.trim(), body.trim()) }, enabled = canSave) {
                Text("SAVE PROMPT", color = if (canSave) PrimaryAccent else TextMediumEmphasis.copy(alpha = 0.4f), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = TextMediumEmphasis)
            }
        }
    )
}