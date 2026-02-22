package com.chatapp.ui.screens.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chatapp.domain.model.MessageRecipientStatus
import com.chatapp.ui.viewmodel.MessageInfoViewModel
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm, dd MMM").withZone(ZoneId.systemDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageInfoScreen(
    onBack: () -> Unit,
    viewModel: MessageInfoViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Message Info") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (state.loading && state.recipients.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val readBy = state.recipients.filter { it.status == "READ" }
            val deliveredTo = state.recipients.filter { it.status == "DELIVERED" }
            val pending = state.recipients.filter { it.status == "SENT" }

            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                if (readBy.isNotEmpty()) {
                    item { SectionHeader("Read by") }
                    items(readBy) { recipient -> RecipientRow(recipient, Color(0xFF00B0FF)) }
                }

                if (deliveredTo.isNotEmpty()) {
                    item { SectionHeader("Delivered to") }
                    items(deliveredTo) { recipient -> RecipientRow(recipient, Color.Gray) }
                }

                if (pending.isNotEmpty()) {
                    item { SectionHeader("Pending") }
                    items(pending) { recipient -> RecipientRow(recipient, Color.LightGray) }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(16.dp)
    )
}

@Composable
private fun RecipientRow(recipient: MessageRecipientStatus, tint: Color) {
    ListItem(
        headlineContent = { Text(recipient.displayName) },
        supportingContent = {
            recipient.timestamp?.let {
                Text(timeFormatter.format(it))
            } ?: Text(recipient.phone)
        },
        leadingContent = {
            Surface(modifier = Modifier.size(40.dp), shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                Box(contentAlignment = Alignment.Center) {
                    Text(recipient.displayName.take(1).uppercase())
                }
            }
        },
        trailingContent = {
            val icon = if (recipient.status == "SENT") Icons.Default.Done else Icons.Default.DoneAll
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
    )
}
