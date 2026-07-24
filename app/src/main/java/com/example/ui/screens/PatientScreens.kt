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
import com.example.data.model.User
import com.example.ui.viewmodel.MedLinkViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchDoctorsScreen(viewModel: MedLinkViewModel, onDoctorClick: (String) -> Unit) {
    val doctors by viewModel.doctorsList.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    
    val filteredDoctors = doctors.filter { 
        it.name.contains(searchQuery, ignoreCase = true) || it.specialty?.contains(searchQuery, ignoreCase = true) == true
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Find a Doctor") }) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search by name or specialty") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(filteredDoctors) { doctor ->
                    Card(
                        onClick = { onDoctorClick(doctor.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(doctor.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Text(doctor.specialty ?: "General Physician", color = Color.Gray)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoctorDetailsScreen(viewModel: MedLinkViewModel, doctorId: String, onBookClick: () -> Unit) {
    val doctors by viewModel.doctorsList.collectAsState()
    val doctor = doctors.find { it.id == doctorId }
    
    Scaffold(
        topBar = { TopAppBar(title = { Text("Doctor Details") }) }
    ) { padding ->
        if (doctor == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(modifier = Modifier.padding(padding).padding(24.dp)) {
                Text(doctor.name, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text(doctor.specialty ?: "General Physician", color = MaterialTheme.colorScheme.primary)
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text("Location: ${doctor.location ?: "N/A"}")
                Text("Timings: ${doctor.consultationTimings ?: "9:00 AM - 5:00 PM"}")
                Text("Fees: $${doctor.fees}")
                
                Spacer(modifier = Modifier.weight(1f))
                
                Button(
                    onClick = onBookClick,
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text("Book Appointment")
                }
            }
        }
    }
}
