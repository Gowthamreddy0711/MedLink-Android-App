package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.FirebaseRepository
import com.example.data.model.*
import com.example.data.network.GeminiAssistantManager
import com.example.data.network.GeminiContent
import com.example.data.network.GeminiPart
import com.example.data.repository.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MedLinkViewModel(application: Application) : AndroidViewModel(application) {

    private val firebaseRepository = FirebaseRepository()
    private val sessionManager = SessionManager(application)
    
    // Repositories
    val authRepository = AuthRepository(firebaseRepository, sessionManager)
    val appointmentRepository = AppointmentRepository(firebaseRepository)
    val queueRepository = QueueRepository(firebaseRepository)
    val prescriptionRepository = PrescriptionRepository(firebaseRepository)
    val reviewRepository = ReviewRepository(firebaseRepository)
    val notificationRepository = NotificationRepository(firebaseRepository)
    val scheduleRepository = ScheduleRepository(firebaseRepository)
    val leaveCoverageRepository = LeaveCoverageRepository(firebaseRepository)

    // AI Assistant Manager
    private val geminiAssistant = GeminiAssistantManager()

    // ----------------------------------------------------
    // AUTHENTICATION & PROFILE DATA STATE
    // ----------------------------------------------------
    val currentUser = authRepository.activeUser
    
    private val _userDetails = MutableStateFlow<User?>(null)
    val userDetails: StateFlow<User?> = _userDetails.asStateFlow()

    private val _doctorsList = MutableStateFlow<List<User>>(emptyList())
    val doctorsList: StateFlow<List<User>> = _doctorsList.asStateFlow()

    private val _pendingDoctors = MutableStateFlow<List<User>>(emptyList())
    val pendingDoctors: StateFlow<List<User>> = _pendingDoctors.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _loading = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            currentUser.collect { user ->
                if (user.isLoggedIn && user.id.isNotEmpty()) {
                    _userDetails.value = authRepository.getUserDetails(user.id)
                    loadUserNotifications(user.id)
                } else {
                    _userDetails.value = null
                }
            }
        }

        viewModelScope.launch {
            authRepository.getApprovedDoctorsFlow().collect { _doctorsList.value = it }
        }

        viewModelScope.launch {
            authRepository.getPendingDoctorsFlow().collect { _pendingDoctors.value = it }
        }
    }

    fun signupPatient(email: String, name: String, pass: String, phone: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _loading.value = true
            _authError.value = null
            val res = authRepository.signup(email, name, pass, "PATIENT", phoneNumber = phone)
            if (res.isSuccess) onSuccess() else _authError.value = res.exceptionOrNull()?.message
            _loading.value = false
        }
    }

    fun signupDoctor(
        email: String, name: String, pass: String, specialty: String, license: String,
        reg: String, gov: String, phone: String, loc: String,
        exp: Int = 0, fees: Double = 0.0, onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _loading.value = true
            _authError.value = null
            val res = authRepository.signup(
                email, name, pass, "DOCTOR", specialty, license,
                reg, gov, phone, loc, exp, fees
            )
            if (res.isSuccess) onSuccess() else _authError.value = res.exceptionOrNull()?.message
            _loading.value = false
        }
    }

    fun login(email: String, pass: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _loading.value = true
            _authError.value = null
            val res = authRepository.login(email, pass)
            if (res.isSuccess) onSuccess() else _authError.value = res.exceptionOrNull()?.message
            _loading.value = false
        }
    }

    fun logout() { authRepository.logout() }
    fun clearAuthErrors() { _authError.value = null }
    fun updateUserProfile(user: User) { viewModelScope.launch { authRepository.updateUserProfile(user); _userDetails.value = user } }
    fun updateClinicStatus(status: String) {
        viewModelScope.launch {
            val doctorId = currentUser.value.id
            if (doctorId.isNotEmpty()) {
                authRepository.updateClinicStatus(doctorId, status)
                _userDetails.value = _userDetails.value?.copy(clinicStatus = status)
            }
        }
    }
    fun deleteAccount() { viewModelScope.launch { _userDetails.value?.let { authRepository.deleteAccount(it.id) } } }

    // ----------------------------------------------------
    // APPOINTMENTS
    // ----------------------------------------------------
    private val _patientAppointments = MutableStateFlow<List<Appointment>>(emptyList())
    val patientAppointments: StateFlow<List<Appointment>> = _patientAppointments.asStateFlow()

    private val _doctorAppointments = MutableStateFlow<List<Appointment>>(emptyList())
    val doctorAppointments: StateFlow<List<Appointment>> = _doctorAppointments.asStateFlow()

    private val _completedAppointments = MutableStateFlow<List<Appointment>>(emptyList())
    val completedAppointments: StateFlow<List<Appointment>> = _completedAppointments.asStateFlow()

    private val _cancelledAppointments = MutableStateFlow<List<Appointment>>(emptyList())
    val cancelledAppointments: StateFlow<List<Appointment>> = _cancelledAppointments.asStateFlow()

    private val _todayAppointments = MutableStateFlow<List<Appointment>>(emptyList())
    val todayAppointments: StateFlow<List<Appointment>> = _todayAppointments.asStateFlow()

    private val _upcomingAppointments = MutableStateFlow<List<Appointment>>(emptyList())
    val upcomingAppointments: StateFlow<List<Appointment>> = _upcomingAppointments.asStateFlow()

    fun loadPatientAppointments(patientId: String) {
        viewModelScope.launch {
            appointmentRepository.getAppointmentsForPatient(patientId).collect { _patientAppointments.value = it }
        }
    }

    fun loadDoctorAppointments(doctorId: String) {
        viewModelScope.launch {
            appointmentRepository.getAppointmentsForDoctor(doctorId).collect { list ->
                // All active/pending appointments for statistics
                _doctorAppointments.value = list.filter { it.status == "PENDING" || it.status == "ACCEPTED" }
                
                val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                
                // Properly populate tabs for Apps screen
                _todayAppointments.value = list.filter { it.dateStr == todayStr && (it.status == "PENDING" || it.status == "ACCEPTED") }
                _upcomingAppointments.value = list.filter { it.dateStr > todayStr && (it.status == "PENDING" || it.status == "ACCEPTED") }.sortedBy { it.timestampLong }
                _completedAppointments.value = list.filter { it.status == "COMPLETED" }
                _cancelledAppointments.value = list.filter { it.status == "CANCELLED" || it.status == "REJECTED" }
            }
        }
    }

    fun updateAppointmentStatus(appointmentId: String, status: String) {
        viewModelScope.launch {
            val doctorId = currentUser.value.id
            if (doctorId.isNotEmpty()) {
                appointmentRepository.updateAppointmentStatus(appointmentId, doctorId, status)
            }
        }
    }

    fun bookAppointment(doctorId: String, notes: String, timeInMillis: Long, slotId: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            val patientId = currentUser.value.id
            if (patientId.isNotEmpty()) {
                val res = appointmentRepository.bookAppointment(patientId, doctorId, notes, timeInMillis, slotId)
                if (res.isSuccess) onComplete()
            }
        }
    }

    // ----------------------------------------------------
    // SCHEDULE
    // ----------------------------------------------------
    private val _doctorSlots = MutableStateFlow<List<DoctorSlot>>(emptyList())
    val doctorSlots: StateFlow<List<DoctorSlot>> = _doctorSlots.asStateFlow()

    fun loadDoctorSlots(doctorId: String, date: String) {
        viewModelScope.launch {
            scheduleRepository.getSlotsFlow(doctorId, date).collect { _doctorSlots.value = it }
        }
    }

    fun addDoctorSlot(slot: DoctorSlot) {
        viewModelScope.launch { scheduleRepository.addSlot(slot) }
    }

    fun deleteDoctorSlot(doctorId: String, slotId: String) {
        viewModelScope.launch { scheduleRepository.deleteSlot(doctorId, slotId) }
    }

    fun generateSlots(
        date: String,
        startTime: String,
        endTime: String,
        durationMinutes: Int,
        consultationType: String,
        maxPatients: Int
    ) {
        viewModelScope.launch {
            val doctorId = currentUser.value.id
            if (doctorId.isEmpty()) return@launch

            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            val startCal = Calendar.getInstance().apply { time = sdf.parse(startTime)!! }
            val endCal = Calendar.getInstance().apply { time = sdf.parse(endTime)!! }

            while (startCal.before(endCal)) {
                val slotStart = sdf.format(startCal.time)
                startCal.add(Calendar.MINUTE, durationMinutes)
                if (startCal.after(endCal)) break
                val slotEnd = sdf.format(startCal.time)

                val slot = DoctorSlot(
                    doctorId = doctorId,
                    date = date,
                    startTime = slotStart,
                    endTime = slotEnd,
                    consultationType = consultationType,
                    maxPatients = maxPatients,
                    status = "Enabled"
                )
                scheduleRepository.addSlot(slot)
            }
        }
    }

    // ----------------------------------------------------
    // QUEUE
    // ----------------------------------------------------
    private val _activeQueue = MutableStateFlow<QueueStatus?>(null)
    val activeQueue: StateFlow<QueueStatus?> = _activeQueue.asStateFlow()

    private val _waitingQueueItems = MutableStateFlow<List<QueueItem>>(emptyList())
    val waitingQueueItems: StateFlow<List<QueueItem>> = _waitingQueueItems.asStateFlow()

    private val _completedQueueItems = MutableStateFlow<List<QueueItem>>(emptyList())
    val completedQueueItems: StateFlow<List<QueueItem>> = _completedQueueItems.asStateFlow()

    fun listenToQueueForDoctor(doctorId: String) {
        viewModelScope.launch {
            queueRepository.getQueueStatusFlow(doctorId).collect { _activeQueue.value = it }
        }
        viewModelScope.launch {
            queueRepository.getWaitingQueueItemsFlow(doctorId).collect { _waitingQueueItems.value = it }
        }
        viewModelScope.launch {
            queueRepository.getCompletedQueueItemsFlow(doctorId).collect { _completedQueueItems.value = it }
        }
    }

    fun callNextPatient(doctorId: String) {
        viewModelScope.launch {
            val waiting = _waitingQueueItems.value
            if (waiting.isNotEmpty()) {
                val nextPatient = waiting.first()
                queueRepository.updateQueueItemStatus(nextPatient.id, "ACTIVE")
                queueRepository.updateQueueStatus(
                    doctorId,
                    QueueStatus(
                        doctorId = doctorId,
                        currentPatientId = nextPatient.appointmentId,
                        currentPatientName = nextPatient.patientName,
                        currentQueueNumber = nextPatient.queueNumber,
                        estimatedWaitMinutes = (waiting.size - 1) * 15
                    )
                )
            }
        }
    }

    fun completeCurrentPatient(doctorId: String) {
        viewModelScope.launch {
            val active = _activeQueue.value
            if (active != null) {
                // Find the active item in some way, or just clear status
                // For simplicity, we search waiting list for "ACTIVE" items if we had them, 
                // but our repository filters by status.
                // Let's just clear the queue status for now.
                queueRepository.updateQueueStatus(doctorId, QueueStatus(doctorId = doctorId))
            }
        }
    }

    // ----------------------------------------------------
    // PRESCRIPTIONS
    // ----------------------------------------------------
    private val _patientPrescriptions = MutableStateFlow<List<Prescription>>(emptyList())
    val patientPrescriptions: StateFlow<List<Prescription>> = _patientPrescriptions.asStateFlow()

    private val _doctorPrescriptions = MutableStateFlow<List<Prescription>>(emptyList())
    val doctorPrescriptions: StateFlow<List<Prescription>> = _doctorPrescriptions.asStateFlow()

    fun loadPatientPrescriptions(patientId: String) {
        viewModelScope.launch {
            prescriptionRepository.getPrescriptionsForPatient(patientId).collect { _patientPrescriptions.value = it }
        }
    }

    fun loadDoctorPrescriptions(doctorId: String) {
        viewModelScope.launch {
            prescriptionRepository.getPrescriptionsForDoctor(doctorId).collect { _doctorPrescriptions.value = it }
        }
    }

    fun issuePrescription(
        patientId: String,
        patientName: String,
        appointmentId: String,
        hospitalName: String,
        diagnoses: String,
        symptoms: String,
        medications: String,
        dosage: String,
        instructions: String,
        nextVisitDate: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            val doctor = _userDetails.value
            if (doctor != null) {
                prescriptionRepository.createPrescription(
                    patientId, patientName, doctor.id, doctor.name,
                    appointmentId, hospitalName, diagnoses, symptoms,
                    medications, dosage, instructions, nextVisitDate
                )
                onSuccess()
            }
        }
    }

    fun submitDoctorReview(doctorId: String, rating: Int, comment: String) {
        viewModelScope.launch {
            val patient = _userDetails.value
            if (patient != null) {
                reviewRepository.submitReview(
                    Review(
                        patientId = patient.id,
                        patientName = patient.name,
                        doctorId = doctorId,
                        rating = rating,
                        comment = comment
                    )
                )
            }
        }
    }

    fun openPrescriptionPDF(context: Context, pdfPath: String) {
        // PDF View Logic
        println("Opening PDF at $pdfPath")
    }

    // ----------------------------------------------------
    // AI ASSISTANT
    // ----------------------------------------------------
    private val _aiChatHistory = MutableStateFlow<List<GeminiContent>>(emptyList())
    val aiChatHistory: StateFlow<List<GeminiContent>> = _aiChatHistory.asStateFlow()

    private val _aiConsultationLoading = MutableStateFlow(false)
    val aiConsultationLoading: StateFlow<Boolean> = _aiConsultationLoading.asStateFlow()

    fun initiateAIConsultationMessage(message: String) {
        if (message.isBlank()) return
        viewModelScope.launch {
            _aiConsultationLoading.value = true
            val userMsg = GeminiContent(role = "user", parts = listOf(GeminiPart(text = message)))
            _aiChatHistory.value += userMsg
            val response = geminiAssistant.chat(_aiChatHistory.value, message)
            val modelMsg = GeminiContent(role = "model", parts = listOf(GeminiPart(text = response)))
            _aiChatHistory.value += modelMsg
            _aiConsultationLoading.value = false
        }
    }

    // ----------------------------------------------------
    // NOTIFICATIONS
    // ----------------------------------------------------
    private val _userNotifications = MutableStateFlow<List<Notification>>(emptyList())
    val userNotifications: StateFlow<List<Notification>> = _userNotifications.asStateFlow()

    fun loadUserNotifications(userId: String) {
        viewModelScope.launch {
            notificationRepository.getNotificationsForUser(userId).collect { _userNotifications.value = it }
        }
    }

    fun markNotificationsAsRead() {
        viewModelScope.launch {
            _userDetails.value?.let { notificationRepository.markAllAsRead(it.id) }
        }
    }

    // ----------------------------------------------------
    // LEAVE & COVERAGE
    // ----------------------------------------------------
    private val _doctorLeaveRequests = MutableStateFlow<List<LeaveRequest>>(emptyList())
    val doctorLeaveRequests: StateFlow<List<LeaveRequest>> = _doctorLeaveRequests.asStateFlow()

    private val _coverageRequests = MutableStateFlow<List<CoverageRequest>>(emptyList())
    val coverageRequests: StateFlow<List<CoverageRequest>> = _coverageRequests.asStateFlow()

    fun loadDoctorLeaveRequests(doctorId: String) {
        viewModelScope.launch {
            leaveCoverageRepository.getLeaveRequestsFlow(doctorId).collect { _doctorLeaveRequests.value = it }
        }
    }

    fun submitLeaveRequest(doctorId: String, start: Long, end: Long, reason: String) {
        viewModelScope.launch {
            leaveCoverageRepository.submitLeaveRequest(LeaveRequest(doctorId = doctorId, startDate = start, endDate = end, reason = reason))
        }
    }

    fun loadCoverageRequestsForDoctor(doctorId: String) {
        viewModelScope.launch {
            leaveCoverageRepository.getCoverageRequestsFlow(doctorId, true).collect { _coverageRequests.value = it }
        }
    }

    fun submitCoverageRequest(doctorId: String, coveringDoctorId: String, date: Long) {
        viewModelScope.launch {
            leaveCoverageRepository.submitCoverageRequest(CoverageRequest(doctorId = doctorId, coveringDoctorId = coveringDoctorId, date = date))
        }
    }

    fun reviewDoctorLicense(doctorId: String, isApproved: Boolean) {
        viewModelScope.launch {
            authRepository.verifyDoctor(doctorId, isApproved)
        }
    }
}
