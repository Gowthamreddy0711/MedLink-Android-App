package com.example.data.firebase

import android.net.Uri
import com.example.data.model.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.toObjects
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

class FirebaseRepository {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    // --- Authentication ---
    fun getCurrentUserId(): String? = auth.currentUser?.uid

    suspend fun signup(email: String, pass: String, user: User): Result<Unit> {
        return try {
            val res = auth.createUserWithEmailAndPassword(email, pass).await()
            val userId = res.user?.uid ?: throw Exception("Signup failed")
            val finalUser = user.copy(id = userId)
            db.collection("users").document(userId).set(finalUser).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun login(email: String, pass: String): Result<Unit> {
        return try {
            auth.signInWithEmailAndPassword(email, pass).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun logout() = auth.signOut()

    suspend fun getUser(userId: String): User? {
        return try {
            db.collection("users").document(userId).get().await().toObject(User::class.java)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun updateUser(user: User) {
        db.collection("users").document(user.id).set(user).await()
    }

    // --- Doctors ---

    fun getDoctorsFlow(): Flow<List<User>> = callbackFlow {
        val listener = db.collection("users")
            .whereEqualTo("role", "DOCTOR")
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val doctors = snapshot?.toObjects<User>() ?: emptyList()
                trySend(doctors.filter { it.verified })
            }
        awaitClose { listener.remove() }
    }

    fun getPendingDoctorsFlow(): Flow<List<User>> = callbackFlow {
        val listener = db.collection("users")
            .whereEqualTo("role", "DOCTOR")
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val doctors = snapshot?.toObjects<User>() ?: emptyList()
                trySend(doctors.filter { !it.verified })
            }
        awaitClose { listener.remove() }
    }

    suspend fun verifyDoctor(doctorId: String, isApproved: Boolean) {
        db.collection("users").document(doctorId).update("verified", isApproved).await()
    }

    // --- Scheduling ---

    suspend fun addDoctorSlot(slot: DoctorSlot) {
        val id = if (slot.id.isEmpty()) UUID.randomUUID().toString() else slot.id
        db.collection("doctorSchedules").document(slot.doctorId)
            .collection("slots").document(id).set(slot.copy(id = id)).await()
    }

    suspend fun deleteDoctorSlot(doctorId: String, slotId: String) {
        db.collection("doctorSchedules").document(doctorId)
            .collection("slots").document(slotId).delete().await()
    }

    fun getDoctorSlotsFlow(doctorId: String, date: String): Flow<List<DoctorSlot>> = callbackFlow {
        val listener = db.collection("doctorSchedules").document(doctorId)
            .collection("slots")
            .whereEqualTo("date", date)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                trySend(snapshot?.toObjects<DoctorSlot>() ?: emptyList())
            }
        awaitClose { listener.remove() }
    }

    // --- Appointments ---

    suspend fun updateAppointmentStatus(appointmentId: String, status: String) {
        db.collection("appointments").document(appointmentId).update("status", status).await()
    }

    suspend fun bookAppointment(appointment: Appointment, slotId: String): Result<Unit> {
        return try {
            val id = UUID.randomUUID().toString()
            val finalApp = appointment.copy(id = id, status = "PENDING")
            
            db.runTransaction { transaction ->
                val slotRef = db.collection("doctorSchedules").document(appointment.doctorId)
                    .collection("slots").document(slotId)
                
                val slot = transaction.get(slotRef).toObject(DoctorSlot::class.java)
                if (slot == null || slot.booked) {
                    throw Exception("Slot already booked or unavailable")
                }
                
                transaction.set(db.collection("appointments").document(id), finalApp)
                transaction.update(slotRef, "booked", true, "appointmentId", id, "bookedBy", appointment.patientId)

                // Add to Queue Item
                val qId = UUID.randomUUID().toString()
                val qItem = QueueItem(
                    id = qId,
                    doctorId = appointment.doctorId,
                    appointmentId = id,
                    patientName = appointment.patientName,
                    queueNumber = appointment.queueNumber,
                    status = "WAITING"
                )
                transaction.set(db.collection("queueItems").document(qId), qItem)
            }.await()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getAppointmentsFlow(userId: String, isDoctor: Boolean): Flow<List<Appointment>> = callbackFlow {
        val field = if (isDoctor) "doctorId" else "patientId"
        val listener = db.collection("appointments")
            .whereEqualTo(field, userId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val apps = snapshot?.toObjects<Appointment>() ?: emptyList()
                trySend(apps)
            }
        awaitClose { listener.remove() }
    }

    // --- Queue Management ---

    fun getQueueStatusFlow(doctorId: String): Flow<QueueStatus?> = callbackFlow {
        val listener = db.collection("queueStatus").document(doctorId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                trySend(snapshot?.toObject(QueueStatus::class.java))
            }
        awaitClose { listener.remove() }
    }

    fun getQueueItemsFlow(doctorId: String, status: String): Flow<List<QueueItem>> = callbackFlow {
        val listener = db.collection("queueItems")
            .whereEqualTo("doctorId", doctorId)
            .whereEqualTo("status", status)
            .orderBy("queueNumber", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                trySend(snapshot?.toObjects<QueueItem>() ?: emptyList())
            }
        awaitClose { listener.remove() }
    }

    suspend fun updateQueueItemStatus(itemId: String, status: String) {
        db.collection("queueItems").document(itemId).update("status", status).await()
    }

    suspend fun updateQueueStatus(doctorId: String, status: QueueStatus) {
        db.collection("queueStatus").document(doctorId).set(status).await()
    }

    // --- Leave & Coverage ---

    suspend fun submitLeaveRequest(request: LeaveRequest) {
        val id = if (request.id.isEmpty()) UUID.randomUUID().toString() else request.id
        db.collection("leaveRequests").document(id).set(request.copy(id = id)).await()
    }

    fun getLeaveRequestsFlow(doctorId: String): Flow<List<LeaveRequest>> = callbackFlow {
        val listener = db.collection("leaveRequests")
            .whereEqualTo("doctorId", doctorId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                trySend(snapshot?.toObjects<LeaveRequest>() ?: emptyList())
            }
        awaitClose { listener.remove() }
    }

    suspend fun submitCoverageRequest(request: CoverageRequest) {
        val id = if (request.id.isEmpty()) UUID.randomUUID().toString() else request.id
        db.collection("coverageRequests").document(id).set(request.copy(id = id)).await()
    }

    fun getCoverageRequestsFlow(userId: String, isReceiver: Boolean): Flow<List<CoverageRequest>> = callbackFlow {
        val field = if (isReceiver) "coveringDoctorId" else "doctorId"
        val listener = db.collection("coverageRequests")
            .whereEqualTo(field, userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                trySend(snapshot?.toObjects<CoverageRequest>() ?: emptyList())
            }
        awaitClose { listener.remove() }
    }

    // --- Prescriptions ---

    suspend fun addPrescription(prescription: Prescription) {
        val id = if (prescription.id.isEmpty()) UUID.randomUUID().toString() else prescription.id
        db.collection("prescriptions").document(id).set(prescription.copy(id = id)).await()
    }

    fun getPrescriptionsFlow(userId: String, isDoctor: Boolean): Flow<List<Prescription>> = callbackFlow {
        val field = if (isDoctor) "doctorId" else "patientId"
        val listener = db.collection("prescriptions")
            .whereEqualTo(field, userId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                trySend(snapshot?.toObjects<Prescription>() ?: emptyList())
            }
        awaitClose { listener.remove() }
    }

    // --- Notifications ---

    suspend fun addNotification(notification: Notification) {
        val id = if (notification.id.isEmpty()) UUID.randomUUID().toString() else notification.id
        db.collection("notifications").document(id).set(notification.copy(id = id)).await()
    }

    fun getNotificationsFlow(userId: String): Flow<List<Notification>> = callbackFlow {
        val listener = db.collection("notifications")
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val notifications = snapshot?.toObjects<Notification>() ?: emptyList()
                // Sort in memory to avoid needing a composite index in Firestore for now
                trySend(notifications.sortedByDescending { it.timestampLong })
            }
        awaitClose { listener.remove() }
    }

    suspend fun markNotificationsRead(userId: String) {
        val batch = db.batch()
        val unread = db.collection("notifications")
            .whereEqualTo("userId", userId)
            .whereEqualTo("isRead", false)
            .get().await()
        for (doc in unread.documents) {
            batch.update(doc.reference, "isRead", true)
        }
        batch.commit().await()
    }

    // --- Reviews ---

    suspend fun addReview(review: Review) {
        val id = if (review.id.isEmpty()) UUID.randomUUID().toString() else review.id
        db.collection("reviews").document(id).set(review.copy(id = id)).await()
    }

    fun getReviewsFlow(doctorId: String): Flow<List<Review>> = callbackFlow {
        val listener = db.collection("reviews")
            .whereEqualTo("doctorId", doctorId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                trySend(snapshot?.toObjects<Review>() ?: emptyList())
            }
        awaitClose { listener.remove() }
    }

    // --- Storage ---
    suspend fun uploadProfileImage(userId: String, uri: Uri): String {
        val ref = storage.reference.child("profiles/$userId.jpg")
        ref.putFile(uri).await()
        return ref.downloadUrl.await().toString()
    }
}
