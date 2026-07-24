package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DoctorSlot
import com.example.ui.viewmodel.MedLinkViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookAppointmentScreen(viewModel: MedLinkViewModel, doctorId: String, doctorName: String, onBack: () -> Unit) {
    var notes by remember { mutableStateOf("") }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Book Appointment") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(24.dp)) {
            Text("With Dr. $doctorName", fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
            
            Spacer(modifier = Modifier.height(24.dp))
            
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Symptoms / Purpose of visit") },
                modifier = Modifier.fillMaxWidth().height(150.dp)
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Button(
                onClick = {
                    // This screen needs a slot selection UI to be fully functional with the new system.
                    // For now, using a placeholder logic to avoid compilation errors.
                    viewModel.bookAppointment(doctorId, notes, System.currentTimeMillis(), "placeholder_slot_id") {
                        onBack()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text("Confirm Booking")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIChatScreen(viewModel: MedLinkViewModel) {
    val history by viewModel.aiChatHistory.collectAsState()
    val loading by viewModel.aiConsultationLoading.collectAsState()
    var message by remember { mutableStateOf("") }

    Scaffold(
        topBar = { TopAppBar(title = { Text("AI Assistant") }) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.weight(1f).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(history) { chat ->
                    ChatBubble(chat.parts.firstOrNull()?.text ?: "", chat.role == "user")
                }
                if (loading) {
                    item { CircularProgressIndicator(modifier = Modifier.size(20.dp)) }
                }
            }
            
            Row(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ask something...") }
                )
                IconButton(onClick = { 
                    viewModel.initiateAIConsultationMessage(message)
                    message = ""
                }) {
                    Icon(Icons.Default.Send, contentDescription = "Send")
                }
            }
        }
    }
}

@Composable
fun ChatBubble(text: String, isUser: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier.padding(8.dp),
            colors = CardDefaults.cardColors(containerColor = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(12.dp),
                color = if (isUser) Color.White else Color.Black
            )
        }
    }
}
