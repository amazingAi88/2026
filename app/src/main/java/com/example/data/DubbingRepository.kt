package com.example.data

import android.content.Context
import com.example.R
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.example.model.DubLine
import com.example.model.DubbingProject
import com.example.model.VoiceAgeGroup
import com.example.model.VoiceEffect
import com.example.model.VoiceGender
import com.example.model.VoiceStyleConfig
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class DubbingRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireMatchedUid(expectedUid: String): String {
        val currentUid = auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
        check(currentUid == expectedUid) { "Authenticated user mismatch; refusing cross-user access." }
        return currentUid
    }

    fun observeProjects(userId: String): Flow<List<DubbingProject>> = flow {
        val uid = requireMatchedUid(userId)
        val path = "users/$uid/projects"
        emitAll(
            db.collection("users").document(uid).collection("projects")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        DubbingProject::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeProject(userId: String, projectId: String): Flow<DubbingProject?> = flow {
        val uid = requireMatchedUid(userId)
        require(projectId.isNotBlank()) { "Project ID cannot be blank." }
        val path = "users/$uid/projects/$projectId"
        emitAll(
            db.collection("users").document(uid).collection("projects").document(projectId)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            DubbingProject::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else null
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeLines(userId: String, projectId: String): Flow<List<DubLine>> = flow {
        val uid = requireMatchedUid(userId)
        require(projectId.isNotBlank()) { "Project ID cannot be blank." }
        val path = "users/$uid/projects/$projectId/lines"
        emitAll(
            db.collection("users").document(uid).collection("projects")
                .document(projectId).collection("lines")
                .orderBy("orderIndex", Query.Direction.ASCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        DubLine::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun getLinesOnce(userId: String, projectId: String): Result<List<DubLine>> {
        val path = "users/$userId/projects/$projectId/lines"
        return try {
            val uid = requireMatchedUid(userId)
            require(projectId.isNotBlank()) { "Project ID cannot be blank." }
            val snapshot = db.collection("users").document(uid).collection("projects")
                .document(projectId).collection("lines")
                .orderBy("orderIndex", Query.Direction.ASCENDING)
                .get()
                .await()
            Result.success(
                snapshot.toObjects(
                    DubLine::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    suspend fun createProjectWithLines(
        userId: String,
        project: DubbingProject,
        lines: List<DubLine>
    ): Result<String> {
        var path = "users/$userId/projects"
        return try {
            val uid = requireMatchedUid(userId)
            val projectsCol = db.collection("users").document(uid).collection("projects")
            val projectRef = projectsCol.document()
            val projectId = projectRef.id
            path = projectRef.path

            val clampedLines = lines.take(450)
            val batch = db.batch()
            val projectPayload = project.copy(lineCount = clampedLines.size.toLong()).toCreateMap(uid)
            batch.set(projectRef, projectPayload)

            clampedLines.forEachIndexed { index, line ->
                val lineRef = projectRef.collection("lines").document()
                val linePayload = line.copy(orderIndex = index.toLong()).toCreateMap(uid, projectId)
                batch.set(lineRef, linePayload)
            }

            batch.commit().await()
            Result.success(projectId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateProjectVoiceSettings(
        userId: String,
        projectId: String,
        style: VoiceStyleConfig,
        originalAudioVolume: Double,
        applyToAllLines: Boolean,
        existingLines: List<DubLine>
    ): Result<Unit> {
        val path = "users/$userId/projects/$projectId"
        return try {
            val uid = requireMatchedUid(userId)
            require(projectId.isNotBlank()) { "Project ID cannot be blank." }
            val projectRef = db.collection("users").document(uid).collection("projects").document(projectId)
            val batch = db.batch()
            batch.update(
                projectRef,
                mapOf(
                    "defaultGender" to style.gender.name,
                    "defaultAgeGroup" to style.ageGroup.name,
                    "defaultEffect" to style.effect.name,
                    "defaultPitch" to style.pitchMultiplier.toDouble().coerceIn(0.4, 2.0),
                    "defaultSpeed" to style.speedMultiplier.toDouble().coerceIn(0.5, 2.0),
                    "matchOriginalEmotion" to style.matchOriginalEmotion,
                    "originalAudioVolume" to originalAudioVolume.coerceIn(0.0, 1.0),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            if (applyToAllLines) {
                existingLines.take(400).forEach { line ->
                    if (line.id.isNotBlank()) {
                        val lineRef = projectRef.collection("lines").document(line.id)
                        batch.update(
                            lineRef,
                            mapOf(
                                "voiceGender" to style.gender.name,
                                "ageGroup" to style.ageGroup.name,
                                "voiceEffect" to style.effect.name,
                                "pitchMultiplier" to style.pitchMultiplier.toDouble().coerceIn(0.4, 2.0),
                                "speedMultiplier" to style.speedMultiplier.toDouble().coerceIn(0.5, 2.0),
                                "matchOriginalEmotion" to style.matchOriginalEmotion,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                }
            }

            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateProjectMedia(
        userId: String,
        projectId: String,
        videoFileName: String,
        videoUriString: String,
        durationMs: Long
    ): Result<Unit> {
        val path = "users/$userId/projects/$projectId"
        return try {
            val uid = requireMatchedUid(userId)
            require(projectId.isNotBlank()) { "Project ID cannot be blank." }
            db.collection("users").document(uid).collection("projects").document(projectId)
                .update(
                    mapOf(
                        "videoFileName" to videoFileName.take(200),
                        "videoUriString" to videoUriString.take(1000),
                        "durationMs" to durationMs.coerceIn(0L, 86_400_000L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun replaceProjectLines(
        userId: String,
        projectId: String,
        newLines: List<DubLine>
    ): Result<Unit> {
        val path = "users/$userId/projects/$projectId/lines"
        return try {
            val uid = requireMatchedUid(userId)
            require(projectId.isNotBlank()) { "Project ID cannot be blank." }
            val projectRef = db.collection("users").document(uid).collection("projects").document(projectId)
            val existingSnapshot = projectRef.collection("lines").get().await()
            val batch = db.batch()
            existingSnapshot.documents.forEach { doc -> batch.delete(doc.reference) }

            val clamped = newLines.take(400)
            clamped.forEachIndexed { idx, line ->
                val lineRef = projectRef.collection("lines").document()
                batch.set(lineRef, line.copy(orderIndex = idx.toLong()).toCreateMap(uid, projectId))
            }
            batch.update(
                projectRef,
                mapOf(
                    "lineCount" to clamped.size.toLong(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun updateLine(
        userId: String,
        projectId: String,
        line: DubLine
    ): Result<Unit> {
        val path = "users/$userId/projects/$projectId/lines/${line.id}"
        return try {
            val uid = requireMatchedUid(userId)
            require(projectId.isNotBlank() && line.id.isNotBlank()) { "IDs cannot be blank." }
            val lineRef = db.collection("users").document(uid).collection("projects")
                .document(projectId).collection("lines").document(line.id)
            lineRef.update(
                mapOf(
                    "speakerName" to line.speakerName.trim().take(60).ifBlank { "Speaker" },
                    "startMs" to line.startMs.coerceIn(0L, 86_400_000L),
                    "endMs" to line.endMs.coerceIn(0L, 86_400_000L),
                    "englishText" to line.englishText.trim().take(1000),
                    "hindiText" to line.hindiText.trim().take(1000).ifBlank { "..." },
                    "emotion" to line.emotion,
                    "emotionIntensity" to line.emotionIntensity.coerceIn(0.0, 1.0),
                    "voiceGender" to VoiceGender.fromRaw(line.voiceGender).name,
                    "ageGroup" to VoiceAgeGroup.fromRaw(line.ageGroup).name,
                    "voiceEffect" to VoiceEffect.fromRaw(line.voiceEffect).name,
                    "pitchMultiplier" to line.pitchMultiplier.coerceIn(0.4, 2.0),
                    "speedMultiplier" to line.speedMultiplier.coerceIn(0.5, 2.0),
                    "matchOriginalEmotion" to line.matchOriginalEmotion,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun saveBatchHindiTextEdits(
        userId: String,
        projectId: String,
        edits: Map<String, String>
    ): Result<Int> {
        val path = "users/$userId/projects/$projectId/lines"
        return try {
            val uid = requireMatchedUid(userId)
            require(projectId.isNotBlank()) { "Project ID cannot be blank." }
            val validEdits = edits.entries.filter { it.key.isNotBlank() }.take(450)
            if (validEdits.isEmpty()) return Result.success(0)

            val projectRef = db.collection("users").document(uid).collection("projects").document(projectId)
            val batch = db.batch()
            validEdits.forEach { (lineId, rawHindi) ->
                val lineRef = projectRef.collection("lines").document(lineId)
                val cleanHindi = rawHindi.trim().take(1000).ifBlank { "..." }
                batch.update(
                    lineRef,
                    mapOf(
                        "hindiText" to cleanHindi,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.update(
                projectRef,
                mapOf("updatedAt" to FieldValue.serverTimestamp())
            )
            batch.commit().await()
            Result.success(validEdits.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun addLine(
        userId: String,
        projectId: String,
        line: DubLine,
        newTotalCount: Long
    ): Result<Unit> {
        val path = "users/$userId/projects/$projectId/lines"
        return try {
            val uid = requireMatchedUid(userId)
            require(projectId.isNotBlank()) { "Project ID cannot be blank." }
            val projectRef = db.collection("users").document(uid).collection("projects").document(projectId)
            val lineRef = projectRef.collection("lines").document()
            val batch = db.batch()
            batch.set(lineRef, line.toCreateMap(uid, projectId))
            batch.update(
                projectRef,
                mapOf(
                    "lineCount" to newTotalCount.coerceIn(0L, 500L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteLine(
        userId: String,
        projectId: String,
        lineId: String,
        newTotalCount: Long
    ): Result<Unit> {
        val path = "users/$userId/projects/$projectId/lines/$lineId"
        return try {
            val uid = requireMatchedUid(userId)
            require(projectId.isNotBlank() && lineId.isNotBlank()) { "IDs cannot be blank." }
            val projectRef = db.collection("users").document(uid).collection("projects").document(projectId)
            val lineRef = projectRef.collection("lines").document(lineId)
            val batch = db.batch()
            batch.delete(lineRef)
            batch.update(
                projectRef,
                mapOf(
                    "lineCount" to newTotalCount.coerceIn(0L, 500L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteProject(userId: String, projectId: String): Result<Unit> {
        val path = "users/$userId/projects/$projectId"
        return try {
            val uid = requireMatchedUid(userId)
            require(projectId.isNotBlank()) { "Project ID cannot be blank." }
            val projectRef = db.collection("users").document(uid).collection("projects").document(projectId)
            val linesSnapshot = projectRef.collection("lines").get().await()
            val batch = db.batch()
            linesSnapshot.documents.forEach { doc -> batch.delete(doc.reference) }
            batch.delete(projectRef)
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }
}
