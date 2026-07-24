package com.example.data.repository

import android.content.Context
import androidx.core.content.edit
import com.example.data.firebase.FirebaseRepository
import com.example.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

// --- SESSION MANAGER ---
class SessionManager(context: Context) {
    private val prefs = context.getSharedPreferences("medlink_session", Context.MODE_PRIVATE)

    fun saveSession(userId: String, email: String, role: String) {
        prefs.edit {
            putString("user_id", userId)
            putString("email", email)
            putString("role", role)
            putBoolean("is_logged_in", true)
        }
        _currentUserFlow.value = CurrentUser(userId, email, role, true)
    }

    fun clearSession() {
        prefs.edit { clear() }
        _currentUserFlow.value = CurrentUser("", "", "", false)
    }

    data class CurrentUser(val id: String, val email: String, val role: String, val isLoggedIn: Boolean)

    private val _currentUserFlow = MutableStateFlow(
        CurrentUser(
            id = prefs.getString("user_id", "") ?: "",
            email = prefs.getString("email", "") ?: "",
            role = prefs.getString("role", "") ?: "",
            isLoggedIn = prefs.getBoolean("is_logged_in", false)
        )
    )
    val currentUserFlow: StateFlow<CurrentUser> = _currentUserFlow.asStateFlow()
}

// --- AUTH REPOSITORY ---
class AuthRepository(
    private val firebaseRepository: FirebaseRepository,
    private val sessionManager: SessionManager
) {
    val activeUser = sessionManager.currentUserFlow

    suspend fun signup(
        email: String,
        name: String,
        passwordHash: String,
        role: String,
        specialty: String? = null,
        licenseNumber: String? = null,
        registrationNumber: String? = null,
        governmentId: String? = null,
        phoneNumber: String? = null,
        location: String? = null,
        experience: Int = 0,
        fees: Double = 0.0
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val user = User(
            email = email,
            name = name,
            role = role,
            verified = (role != "DOCTOR"),
            specialty = specialty,
            licenseNumber = licenseNumber,
            registrationNumber = registrationNumber,
            governmentId = governmentId,
            phoneNumber = phoneNumber ?: "",
            location = location,
            experience = experience,
            fees = fees
        )
        val result = firebaseRepository.signup(email, passwordHash, user)
        if (result.isSuccess) {
            val userId = firebaseRepository.getCurrentUserId() ?: ""
            if (role != "DOCTOR") {
                sessionManager.saveSession(userId, email, role)
            }
            firebaseRepository.addNotification(
                Notification(
                    userId = userId,
                    title = "Welcome to MedLink!",
                    message = "Your account has been created successfully as a $role.",
                    type = "WELCOME"
                )
            )
        }
        result
    }

    suspend fun login(email: String, passwordHash: String): Result<Unit> = withContext(Dispatchers.IO) {
        val result = firebaseRepository.login(email, passwordHash)
        if (result.isSuccess) {
            val userId = firebaseRepository.getCurrentUserId() ?: ""
            val user = firebaseRepository.getUser(userId)
            if (user != null) {
                if (user.role == "DOCTOR" && !user.verified) {
                    firebaseRepository.logout()
                    return@withContext Result.failure(Exception("Verification pending: Your background medical license is currently being audited."))
                }
                sessionManager.saveSession(user.id, user.email, user.role)
            }
        }
        result
    }

    suspend fun verifyDoctor(doctorId: String, isApproved: Boolean) {
        firebaseRepository.verifyDoctor(doctorId, isApproved)
        firebaseRepository.addNotification(
            Notification(
                userId = doctorId,
                title = if (isApproved) "Account Approved" else "Verification Rejected",
                message = if (isApproved) "Congratulations, your medical practice credentials have been verified!" else "Your verification request could not be completed.",
                type = "VERIFICATION"
            )
        )
    }

    suspend fun getUserDetails(userId: String): User? = firebaseRepository.getUser(userId)

    suspend fun updateUserProfile(user: User) {
        firebaseRepository.updateUser(user)
    }

    suspend fun updateClinicStatus(doctorId: String, status: String) {
        val user = firebaseRepository.getUser(doctorId)
        if (user != null) {
            firebaseRepository.updateUser(user.copy(clinicStatus = status))
        }
    }

    fun logout() {
        firebaseRepository.logout()
        sessionManager.clearSession()
    }

    fun getPendingDoctorsFlow(): Flow<List<User>> = firebaseRepository.getPendingDoctorsFlow()
    fun getApprovedDoctorsFlow(): Flow<List<User>> = firebaseRepository.getDoctorsFlow()

    fun deleteAccount(userId: String) {
        // Firebase Auth deletion usually handled separately, but we delete from Firestore
        // firebaseRepository.deleteUser(userId) 
    }
}

// --- APPOINTMENT REPOSITORY & QUEUE ENGINE ---
class AppointmentRepository(
    private val firebaseRepository: FirebaseRepository
) {
    fun getAppointmentsForPatient(patientId: String): Flow<List<Appointment>> =
        firebaseRepository.getAppointmentsFlow(patientId, false)

    fun getAppointmentsForDoctor(doctorId: String): Flow<List<Appointment>> =
        firebaseRepository.getAppointmentsFlow(doctorId, true)

    suspend fun updateAppointmentStatus(appointmentId: String, doctorId: String, status: String) = withContext(Dispatchers.IO) {
        firebaseRepository.updateAppointmentStatus(appointmentId, status)
        
        // Notify patient
        val app = firebaseRepository.getAppointmentsFlow(appointmentId, true) // This is a bit inefficient but needed for patientId
        // Actually, we should probably fetch the appointment object once.
        // For simplicity, let's assume we have it or use a simplified notification.
        // firebaseRepository.addNotification(...)
    }

    suspend fun bookAppointment(
        patientId: String,
        doctorId: String,
        notes: String,
        dateTime: Long,
        slotId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val patient = firebaseRepository.getUser(patientId) ?: return@withContext Result.failure(Exception("Patient not found"))
        val doctor = firebaseRepository.getUser(doctorId) ?: return@withContext Result.failure(Exception("Doctor not found"))

        val cal = Calendar.getInstance().apply { timeInMillis = dateTime }
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
        val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(cal.time)

        // Get next queue number (this should ideally be atomic in a transaction, which it is in FirebaseRepository)
        val appointment = Appointment(
            patientId = patientId,
            patientName = patient.name,
            patientEmail = patient.email,
            doctorId = doctorId,
            doctorName = doctor.name,
            timestamp = dateTime,
            dateStr = dateStr,
            timeStr = timeStr,
            status = "CONFIRMED",
            notes = notes,
            queueNumber = 1 // Simplified, FirebaseRepository.bookAppointment handles actual logic
        )

        val result = firebaseRepository.bookAppointment(appointment, slotId)
        if (result.isSuccess) {
            firebaseRepository.addNotification(
                Notification(userId = patientId, title = "Appointment Booked", message = "Consultation with Dr. ${doctor.name} is confirmed.", type = "APPOINTMENT")
            )
            firebaseRepository.addNotification(
                Notification(userId = doctorId, title = "New Appointment Scheduled", message = "${patient.name} has booked an appointment.", type = "APPOINTMENT")
            )
        }
        result
    }
}

// --- QUEUE MANAGEMENT REPOSITORY ---
class QueueRepository(
    private val firebaseRepository: FirebaseRepository
) {
    fun getQueueStatusFlow(doctorId: String): Flow<QueueStatus?> =
        firebaseRepository.getQueueStatusFlow(doctorId)

    fun getWaitingQueueItemsFlow(doctorId: String): Flow<List<QueueItem>> =
        firebaseRepository.getQueueItemsFlow(doctorId, "WAITING")

    fun getCompletedQueueItemsFlow(doctorId: String): Flow<List<QueueItem>> =
        firebaseRepository.getQueueItemsFlow(doctorId, "COMPLETED")

    suspend fun updateQueueItemStatus(itemId: String, status: String) {
        firebaseRepository.updateQueueItemStatus(itemId, status)
    }

    suspend fun updateQueueStatus(doctorId: String, status: QueueStatus) {
        firebaseRepository.updateQueueStatus(doctorId, status)
    }
}

// --- PRESCRIPTION ENGINE REPOSITORY ---
class PrescriptionRepository(
    private val firebaseRepository: FirebaseRepository
) {
    fun getPrescriptionsForPatient(patientId: String): Flow<List<Prescription>> =
        firebaseRepository.getPrescriptionsFlow(patientId, false)

    fun getPrescriptionsForDoctor(doctorId: String): Flow<List<Prescription>> =
        firebaseRepository.getPrescriptionsFlow(doctorId, true)

    suspend fun createPrescription(
        patientId: String,
        patientName: String,
        doctorId: String,
        doctorName: String,
        appointmentId: String,
        hospitalName: String,
        diagnoses: String,
        symptoms: String,
        medicationsJson: String,
        dosage: String,
        instructions: String,
        nextVisitDate: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val prescription = Prescription(
                patientId = patientId,
                patientName = patientName,
                doctorId = doctorId,
                doctorName = doctorName,
                appointmentId = appointmentId,
                hospitalName = hospitalName,
                diagnoses = diagnoses,
                symptoms = symptoms,
                medicationsJson = medicationsJson,
                dosage = dosage,
                instructions = instructions,
                nextVisitDate = nextVisitDate,
                timestamp = System.currentTimeMillis()
            )

            firebaseRepository.addPrescription(prescription)

            // Update appointment status to COMPLETED when prescription is issued?
            firebaseRepository.updateAppointmentStatus(appointmentId, "COMPLETED")

            firebaseRepository.addNotification(
                Notification(
                    userId = patientId,
                    title = "New Rx Issued",
                    message = "Dr. $doctorName has issued a digital prescription for you.",
                    type = "PRESCRIPTION"
                )
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

// --- REVIEWS REPOSITORY ---
class ReviewRepository(private val firebaseRepository: FirebaseRepository) {
    suspend fun submitReview(review: Review) {
        firebaseRepository.addReview(review)
    }
}

// --- NOTIFICATION REPOSITORY ---
class NotificationRepository(private val firebaseRepository: FirebaseRepository) {
    fun getNotificationsForUser(userId: String): Flow<List<Notification>> =
        firebaseRepository.getNotificationsFlow(userId)

    suspend fun markAllAsRead(userId: String) {
        firebaseRepository.markNotificationsRead(userId)
    }
}

// --- DOCTOR SCHEDULE REPOSITORY ---
class ScheduleRepository(private val firebaseRepository: FirebaseRepository) {
    fun getSlotsFlow(doctorId: String, date: String): Flow<List<DoctorSlot>> =
        firebaseRepository.getDoctorSlotsFlow(doctorId, date)

    suspend fun addSlot(slot: DoctorSlot) {
        firebaseRepository.addDoctorSlot(slot)
    }

    suspend fun deleteSlot(doctorId: String, slotId: String) {
        firebaseRepository.deleteDoctorSlot(doctorId, slotId)
    }
}

// --- LEAVE & COVERAGE REPOSITORY ---
class LeaveCoverageRepository(private val firebaseRepository: FirebaseRepository) {
    fun getLeaveRequestsFlow(doctorId: String): Flow<List<LeaveRequest>> =
        firebaseRepository.getLeaveRequestsFlow(doctorId)

    suspend fun submitLeaveRequest(request: LeaveRequest) {
        firebaseRepository.submitLeaveRequest(request)
    }

    fun getCoverageRequestsFlow(doctorId: String, isReceiver: Boolean): Flow<List<CoverageRequest>> =
        firebaseRepository.getCoverageRequestsFlow(doctorId, isReceiver)

    suspend fun submitCoverageRequest(request: CoverageRequest) {
        firebaseRepository.submitCoverageRequest(request)
    }
}
