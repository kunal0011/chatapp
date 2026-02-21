package com.chatapp.ui.screens.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chatapp.domain.model.ChatMessage
import com.chatapp.domain.model.MessageStatus
import com.chatapp.ui.viewmodel.ChatViewModel
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.text.style.TextAlign

private val timeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

private val REACTION_EMOJIS = listOf("👍", "❤️", "😂", "😮", "😢", "🙏")
private val QUICK_EMOJIS = listOf("😀", "😂", "🥰", "😮", "😢", "😡", "👍", "🙏", "🔥", "✨", "🎉", "💯")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val messageInput by viewModel.messageInput.collectAsStateWithLifecycle()
    val error = state.error
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var showChatMenu by remember { mutableStateOf(false) }
    var showEmojiBar by remember { mutableStateOf(false) }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text = state.contactName, style = MaterialTheme.typography.titleMedium)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (state.isOtherTyping) {
                                Text(
                                    text = "typing...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontStyle = FontStyle.Italic
                                )
                            } else {
                                if (state.isConnected) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(
                                                color = MaterialTheme.colorScheme.primary,
                                                shape = CircleShape
                                            )
                                    )
                                    Text(
                                        text = "Online",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                } else {
                                    Text(
                                        text = formatLastSeen(state.messages.firstOrNull()?.createdAt ?: Instant.now()),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showChatMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                        }
                        DropdownMenu(expanded = showChatMenu, onDismissRequest = { showChatMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Block User") },
                                onClick = {
                                    showChatMenu = false
                                    viewModel.blockUser()
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                windowInsets = WindowInsets.safeDrawing
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
        ) {
            if (state.loading && state.messages.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(12.dp)
                ) {
                    item {
                        if (state.nextCursor != null) {
                            ElevatedButton(
                                onClick = viewModel::loadOlderMessages,
                                enabled = !state.loadingOlder,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (state.loadingOlder) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Text("Load older messages")
                                }
                            }
                        }
                    }

                    itemsIndexed(state.messages, key = { _, it -> it.id }) { index, message ->
                        val isMine = message.senderId == state.currentUserId
                        MessageBubble(
                            message = message,
                            isMine = isMine,
                            onDelete = { viewModel.unsendMessage(message.id) },
                            onReply = { viewModel.onReplyClick(message) },
                            onEdit = { viewModel.onEditClick(message) },
                            onReaction = { emoji -> viewModel.sendReaction(message.id, emoji) },
                            onQuoteClick = { parentId ->
                                val parentIndex = state.messages.indexOfFirst { it.id == parentId }
                                if (parentIndex != -1) {
                                    scope.launch { listState.animateScrollToItem(parentIndex) }
                                }
                            }
                        )
                    }
                }
            }

            if (error != null) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            Surface(tonalElevation = 3.dp, shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.navigationBarsPadding()) {

                    // Reply/Edit Preview...
                    if (state.replyingTo != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Replying to ${if (state.replyingTo?.senderId == state.currentUserId) "yourself" else state.replyingTo?.senderName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = state.replyingTo?.content ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = viewModel::cancelReply) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel Reply", modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    if (state.editingMessage != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.05f))
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Editing message",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = viewModel::cancelEdit) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel Edit", modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    // Emoji Bar
                    if (showEmojiBar) {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            items(QUICK_EMOJIS) { emoji ->
                                Text(
                                    text = emoji,
                                    modifier = Modifier
                                        .padding(horizontal = 12.dp)
                                        .clickable {
                                            viewModel.onMessageInputChangeNew(messageInput + emoji)
                                        },
                                    fontSize = 24.sp
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = messageInput,
                            onValueChange = viewModel::onMessageInputChangeNew,
                            placeholder = { Text("Type a message...") },
                            modifier = Modifier.weight(1f),
                            maxLines = 5,
                            shape = RoundedCornerShape(24.dp),
                            leadingIcon = {
                                IconButton(onClick = { showEmojiBar = !showEmojiBar }) {
                                    Icon(
                                        imageVector = if (showEmojiBar) Icons.Default.Keyboard else Icons.Default.SentimentSatisfiedAlt,
                                        contentDescription = "Emojis",
                                        tint = if (showEmojiBar) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        )

                        IconButton(
                            onClick = {
                                viewModel.sendMessage()
                                showEmojiBar = false
                            },
                            enabled = !state.sending && messageInput.isNotBlank() && state.isConnected,
                            modifier = Modifier
                                .background(
                                    color = if (messageInput.isNotBlank() && state.isConnected)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant,
                                    shape = CircleShape
                                )
                                .size(48.dp)
                        ) {
                            if (state.sending) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                val icon = if (state.editingMessage != null) Icons.Default.Check else Icons.Default.Send
                                Icon(imageVector = icon, contentDescription = "Send", tint = if (messageInput.isNotBlank() && state.isConnected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatLastSeen(lastSeen: Instant): String {
    val now = Instant.now()
    val duration = Duration.between(lastSeen, now)
    return when {
        duration.toMinutes() < 1 -> "Last seen just now"
        duration.toMinutes() < 60 -> "Last seen ${duration.toMinutes()}m ago"
        duration.toHours() < 24 -> "Last seen ${duration.toHours()}h ago"
        else -> "Last seen long ago"
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: ChatMessage,
    isMine: Boolean,
    onDelete: () -> Unit,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onReaction: (String) -> Unit,
    onQuoteClick: (String) -> Unit
) {
    var showOptions by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
    ) {
        Box {
            Surface(
                color = if (message.isDeleted) Color.LightGray.copy(alpha = 0.4f) else if (isMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(
                    topStart = 16.dp, topEnd = 16.dp,
                    bottomStart = if (isMine) 16.dp else 4.dp,
                    bottomEnd = if (isMine) 4.dp else 16.dp
                ),
                tonalElevation = 2.dp,
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = { if (!message.isDeleted) showOptions = true }
                    )
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {

                    if (message.parentId != null && !message.isDeleted) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.05f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .padding(bottom = 4.dp)
                                .fillMaxWidth()
                                .clickable { onQuoteClick(message.parentId!!) }
                        ) {
                            Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                                Box(modifier = Modifier.width(4.dp).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(
                                        text = message.parentSenderName ?: "Unknown",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = message.parentContent ?: "",
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    if (!message.isDeleted) {
                        Text(
                            text = if (isMine) "You" else message.senderName,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isMine) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                        )
                    }
                    Text(
                        text = if (message.isDeleted) "This message was deleted" else message.content,
                        style = MaterialTheme.typography.bodyLarge,
                        fontStyle = if (message.isDeleted) FontStyle.Italic else FontStyle.Normal,
                        color = if (message.isDeleted) Color.Gray else if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 2.dp)
                    )

                    Row(
                        modifier = Modifier.align(Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (message.isEdited && !message.isDeleted) {
                            Text(
                                text = "edited",
                                style = MaterialTheme.typography.labelSmall,
                                fontStyle = FontStyle.Italic,
                                color = if (isMine) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                        }

                        Text(
                            text = timeFormatter.format(message.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isMine) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
                            modifier = Modifier.padding(top = 2.dp)
                        )

                        if (isMine && !message.isDeleted) {
                            val statusIcon = when (message.status) {
                                MessageStatus.SENT -> Icons.Default.Done
                                else -> Icons.Default.DoneAll
                            }
                            val statusColor = when (message.status) {
                                MessageStatus.READ -> Color(0xFF00B0FF)
                                else -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                            }
                            Icon(imageVector = statusIcon, contentDescription = null, modifier = Modifier.size(14.dp), tint = statusColor)
                        }
                    }
                }
            }

            DropdownMenu(expanded = showOptions, onDismissRequest = { showOptions = false }) {
                Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    REACTION_EMOJIS.forEach { emoji ->
                        Text(
                            text = emoji,
                            modifier = Modifier
                                .padding(4.dp)
                                .clickable {
                                    showOptions = false
                                    onReaction(emoji)
                                },
                            fontSize = 24.sp
                        )
                    }
                }
                Divider()
                DropdownMenuItem(
                    text = { Text("Reply") },
                    onClick = {
                        showOptions = false
                        onReply()
                    },
                    leadingIcon = { Icon(Icons.Default.Reply, contentDescription = null) }
                )
                if (isMine) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        onClick = {
                            showOptions = false
                            onEdit()
                        },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete for everyone") },
                        onClick = {
                            showOptions = false
                            onDelete()
                        },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
                    )
                }
            }
        }

        if (message.reactions.isNotEmpty() && !message.isDeleted) {
            Row(
                modifier = Modifier
                    .padding(top = 2.dp, start = 4.dp, end = 4.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                message.reactions.groupBy { it.emoji }.forEach { (emoji, list) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = emoji, fontSize = 12.sp)
                        if (list.size > 1) {
                            Text(
                                text = list.size.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(start = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
