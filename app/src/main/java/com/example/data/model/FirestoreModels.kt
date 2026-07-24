package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName

data class User(
    val id: String = "",
    val email: String = "",
    val name: String = "",
    val role: String = "", // "PATIENT", "DOCTOR", "ADMIN"
    val phoneNumber: String = "",
    val avatarUrl: String? = null,
    
    @get:PropertyName("verified") @set:PropertyName("verified")
    var verified: Boolean = false,
    
    // Alias for verified to fix "No setter/field for isVerified found"
    @get:JvmName("isVerifiedProperty")
    @set:JvmName("setIsVerifiedProperty")
    @get:PropertyName("isVerified") @set:PropertyName("isVerified")
    var isVerified: Boolean = false,

    // Doctor specific fields
    val specialty: String? = null,
    val licenseNumber: String? = null,
    val registrationNumber: String? = null,
    val governmentId: String? = null,
    
    // To fix "No setter/field for governmentIdUrl found"
    val governmentIdUrl: String? = null,
    
    val location: String? = null,
    val consultationTimings: String? = null,
    val averageRating: Float = 0f,
    val totalReviews: Int = 0,
    val experience: Int = 0,
    val fees: Double = 0.0,
    
    // Patient specific fields
    val insuranceInfo: String? = null,
    val emergencyContact: String? = null,

    // Clinic Status
    val clinicStatus: String = "Offline" // "Available", "In Consultation", "Busy", "Away", "Offline"
)

data class DoctorSlot(
    val id: String = "",
    val doctorId: String = "",
    val date: String = "", // YYYY-MM-DD
    val startTime: String = "", // HH:mm
    val endTime: String = "", // HH:mm
    val status: String = "Enabled", // "Enabled", "Disabled"
    val booked: Boolean = false,
    val bookedBy: String? = null,
    val appointmentId: String? = null,
    val consultationType: String = "Offline", // "Online", "Offline"
    val maxPatients: Int = 1
)

data class Appointment(
    val id: String = "",
    val patientId: String = "",
    val patientName: String = "",
    val patientEmail: String = "",
    val patientPhoto: String? = null,
    val patientAge: Int = 0,
    val patientGender: String = "",
    val patientPhone: String = "",
    val doctorId: String = "",
    val doctorName: String = "",
    val doctorPhoto: String? = null,
    val doctorSpecialty: String? = null,
    val consultationType: String = "Offline",
    
    // Firestore might return Timestamp or Long.
    @get:PropertyName("timestamp") @set:PropertyName("timestamp")
    var timestamp: Any = 0L,
    
    val dateStr: String = "", // YYYY-MM-DD
    val timeStr: String = "", // HH:mm
    val status: String = "PENDING", // "PENDING", "ACCEPTED", "COMPLETED", "CANCELLED", "REJECTED"
    val notes: String = "",
    val queueNumber: Int = 0,
    
    // To fix "No setter/field for tokenNumber found"
    val tokenNumber: Int = 0,

    @get:PropertyName("createdAt") @set:PropertyName("createdAt")
    var createdAt: Any = System.currentTimeMillis()
) {
    @get:Exclude
    val timestampLong: Long
        get() = when (val t = timestamp) {
            is Long -> t
            is Timestamp -> t.toDate().time
            else -> 0L
        }

    @get:Exclude
    val createdAtLong: Long
        get() = when (val t = createdAt) {
            is Long -> t
            is Timestamp -> t.toDate().time
            else -> 0L
        }
}

data class Prescription(
    val id: String = "",
    val patientId: String = "",
    val patientName: String = "",
    val doctorId: String = "",
    val doctorName: String = "",
    val hospitalName: String = "",
    val diagnoses: String = "",
    val symptoms: String = "",
    val medicationsJson: String = "",
    val dosage: String = "",
    val instructions: String = "",
    val nextVisitDate: String = "",
    val pdfPath: String? = null,
    val appointmentId: String? = null,
    
    @get:PropertyName("timestamp") @set:PropertyName("timestamp")
    var timestamp: Any = System.currentTimeMillis()
) {
    @get:Exclude
    val timestampLong: Long
        get() = when (val t = timestamp) {
            is Long -> t
            is Timestamp -> t.toDate().time
            else -> 0L
        }
}

data class Review(
    val id: String = "",
    val patientId: String = "",
    val patientName: String = "",
    val doctorId: String = "",
    val rating: Int = 0,
    val comment: String = "",
    
    @get:PropertyName("timestamp") @set:PropertyName("timestamp")
    var timestamp: Any = System.currentTimeMillis()
) {
    @get:Exclude
    val timestampLong: Long
        get() = when (val t = timestamp) {
            is Long -> t
            is Timestamp -> t.toDate().time
            else -> 0L
        }
}

data class Notification(
    val id: String = "",
    val userId: String = "",
    val title: String = "",
    val message: String = "",
    
    @get:PropertyName("timestamp") @set:PropertyName("timestamp")
    var timestamp: Any = System.currentTimeMillis(),
    
    @get:PropertyName("isRead") @set:PropertyName("isRead")
    var isRead: Boolean = false,
    
    val type: String = "" // "APPOINTMENT", "PRESCRIPTION", etc.
) {
    @get:Exclude
    val timestampLong: Long
        get() = when (val t = timestamp) {
            is Long -> t
            is Timestamp -> t.toDate().time
            else -> 0L
        }
}

data class LeaveRequest(
    val id: String = "",
    val doctorId: String = "",
    val startDate: Long = 0,
    val endDate: Long = 0,
    val reason: String = "",
    val status: String = "PENDING"
)

data class CoverageRequest(
    val id: String = "",
    val doctorId: String = "",
    val coveringDoctorId: String = "",
    val date: Long = 0,
    val status: String = "PENDING"
)

data class QueueStatus(
    val doctorId: String = "",
    val currentPatientId: String? = null,
    val currentPatientName: String? = null,
    val currentQueueNumber: Int = 0,
    val estimatedWaitMinutes: Int = 0
)

data class QueueItem(
    val id: String = "",
    val doctorId: String = "",
    val appointmentId: String = "",
    val patientName: String = "",
    val queueNumber: Int = 0,
    val status: String = "WAITING", // "WAITING", "ACTIVE", "COMPLETED"
    
    @get:PropertyName("timestamp") @set:PropertyName("timestamp")
    var timestamp: Any = System.currentTimeMillis()
) {
    @get:Exclude
    val timestampLong: Long
        get() = when (val t = timestamp) {
            is Long -> t
            is Timestamp -> t.toDate().time
            else -> 0L
        }
}
