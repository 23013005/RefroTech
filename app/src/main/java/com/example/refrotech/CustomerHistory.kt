package com.example.refrotech

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

/**
 * CustomerHistory — robust version
 *
 * Behavior:
 * - Prefers userId from intent extras ("userId")
 * - If missing/blank, resolves Firestore user document ID by inspecting the currently authenticated Firebase user:
 *     1) try to find a user doc whose document ID equals auth.uid
 *     2) try to find user doc where field "authUid" == auth.uid (if you store it)
 *     3) try to find user doc where field "email" == auth.currentUser.email
 *     4) try to find user doc where field "phone" == auth.currentUser.phoneNumber
 * - Only after a Firestore userDocId is obtained, starts realtime listener:
 *     db.collection("requests").whereEqualTo("customerId", firestoreUserDocId)
 *
 * This keeps your existing UI and navigation intact while making the history query resilient.
 */
class CustomerHistory : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private lateinit var recyclerHistory: androidx.recyclerview.widget.RecyclerView
    private lateinit var adapter: HistoryAdapter
    private lateinit var userId: String // this will hold the Firestore user document id used in requests.customerId

    // NAVIGATION
    private lateinit var navHome: LinearLayout
    private lateinit var navHistory: LinearLayout

    private var listener: ListenerRegistration? = null

    private val TAG = "CustomerHistory"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_customer_history)

        // Initialize UI
        recyclerHistory = findViewById(R.id.recyclerHistory)
        recyclerHistory.layoutManager = LinearLayoutManager(this)
        adapter = HistoryAdapter(mutableListOf())
        recyclerHistory.adapter = adapter

        navHome = findViewById(R.id.navHome)
        navHistory = findViewById(R.id.navHistory)

        setupNavigation()

        // Attempt to resolve the Firestore userId to use for queries.
        // 1) use intent extra if provided
        val intentUserId = intent.getStringExtra("userId")
        if (!intentUserId.isNullOrBlank()) {
            userId = intentUserId
            startRealtimeListenerForUser(userId)
            return
        }

        // 2) attempt to resolve using currently authenticated Firebase user
        val fUser = auth.currentUser
        if (fUser == null) {
            // no signed-in user; cannot resolve automatically
            Toast.makeText(this, "User not signed in. Please login.", Toast.LENGTH_SHORT).show()
            Log.w(TAG, "No FirebaseAuth user. Intent userId was empty.")
            adapter.updateData(emptyList())
            return
        }

        // Resolve Firestore user document id using several fallbacks
        resolveFirestoreUserId(fUser.uid, fUser.email, fUser.phoneNumber)
    }

    private fun setupNavigation() {
        navHome.setOnClickListener {
            val intent = Intent(this, DashboardCustomer::class.java)
            // Keep passing the same userId if we've resolved it, otherwise rely on DashboardCustomer logic
            if (::userId.isInitialized && userId.isNotBlank()) {
                intent.putExtra("userId", userId)
            }
            startActivity(intent)
            finish()
        }

        navHistory.setOnClickListener {
            // already here - intentionally no-op
        }
    }

    /**
     * Resolve the Firestore users document ID by trying multiple strategies.
     * Calls startRealtimeListenerForUser once resolved.
     */
    private fun resolveFirestoreUserId(authUid: String, email: String?, phone: String?) {
        // Strategy order:
        // 1) users/{authUid} exists (document id equals auth uid)
        // 2) users where field "authUid" == authUid
        // 3) users where field "email" == email
        // 4) users where field "phone" == phone
        // If none found, inform user and abort gracefully.

        // 1) direct doc by authUid
        db.collection(FirestoreFields.USERS).document(authUid).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    userId = doc.id
                    Log.d(TAG, "Resolved userId by direct doc id = $userId")
                    startRealtimeListenerForUser(userId)
                } else {
                    // 2) search by authUid field
                    db.collection(FirestoreFields.USERS)
                        .whereEqualTo("authUid", authUid)
                        .get()
                        .addOnSuccessListener { snapByAuth ->
                            if (!snapByAuth.isEmpty) {
                                val doc = snapByAuth.documents[0]
                                userId = doc.id
                                Log.d(TAG, "Resolved userId by authUid field = $userId")
                                startRealtimeListenerForUser(userId)
                                return@addOnSuccessListener
                            }

                            // 3) search by email (if available)
                            if (!email.isNullOrBlank()) {
                                db.collection(FirestoreFields.USERS)
                                    .whereEqualTo("email", email)
                                    .get()
                                    .addOnSuccessListener { snapByEmail ->
                                        if (!snapByEmail.isEmpty) {
                                            val doc = snapByEmail.documents[0]
                                            userId = doc.id
                                            Log.d(TAG, "Resolved userId by email = $userId")
                                            startRealtimeListenerForUser(userId)
                                            return@addOnSuccessListener
                                        }

                                        // 4) search by phone (if available)
                                        if (!phone.isNullOrBlank()) {
                                            db.collection(FirestoreFields.USERS)
                                                .whereEqualTo("phone", phone)
                                                .get()
                                                .addOnSuccessListener { snapByPhone ->
                                                    if (!snapByPhone.isEmpty) {
                                                        val doc = snapByPhone.documents[0]
                                                        userId = doc.id
                                                        Log.d(TAG, "Resolved userId by phone = $userId")
                                                        startRealtimeListenerForUser(userId)
                                                    } else {
                                                        // Nothing found — inform and abort
                                                        Log.w(TAG, "No user doc found by authUid/email/phone.")
                                                        Toast.makeText(
                                                            this,
                                                            "User profile not found in database. Please login again or contact support.",
                                                            Toast.LENGTH_LONG
                                                        ).show()
                                                        adapter.updateData(emptyList())
                                                    }
                                                }
                                                .addOnFailureListener { e ->
                                                    Log.e(TAG, "Failed search by phone: ${e.message}")
                                                    Toast.makeText(this, "Error resolving user profile.", Toast.LENGTH_SHORT).show()
                                                    adapter.updateData(emptyList())
                                                }
                                        } else {
                                            Log.w(TAG, "No phone to search; user doc not found by authUid/email.")
                                            Toast.makeText(
                                                this,
                                                "User profile not found in database. Please login again or contact support.",
                                                Toast.LENGTH_LONG
                                            ).show()
                                            adapter.updateData(emptyList())
                                        }
                                    }
                                    .addOnFailureListener { e ->
                                        Log.e(TAG, "Failed search by email: ${e.message}")
                                        Toast.makeText(this, "Error resolving user profile.", Toast.LENGTH_SHORT).show()
                                        adapter.updateData(emptyList())
                                    }
                                return@addOnSuccessListener
                            } else {
                                // no email; try phone above or abort
                                if (!phone.isNullOrBlank()) {
                                    db.collection(FirestoreFields.USERS)
                                        .whereEqualTo("phone", phone)
                                        .get()
                                        .addOnSuccessListener { snapByPhone2 ->
                                            if (!snapByPhone2.isEmpty) {
                                                val doc = snapByPhone2.documents[0]
                                                userId = doc.id
                                                Log.d(TAG, "Resolved userId by phone = $userId")
                                                startRealtimeListenerForUser(userId)
                                            } else {
                                                Log.w(TAG, "No user doc found by authUid or phone.")
                                                Toast.makeText(
                                                    this,
                                                    "User profile not found in database. Please login again or contact support.",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                                adapter.updateData(emptyList())
                                            }
                                        }
                                        .addOnFailureListener { e ->
                                            Log.e(TAG, "Failed search by phone: ${e.message}")
                                            Toast.makeText(this, "Error resolving user profile.", Toast.LENGTH_SHORT).show()
                                            adapter.updateData(emptyList())
                                        }
                                } else {
                                    Log.w(TAG, "No email/phone available; cannot resolve user doc.")
                                    Toast.makeText(
                                        this,
                                        "User profile not found in database. Please login again or contact support.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    adapter.updateData(emptyList())
                                }
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Failed search by authUid field: ${e.message}")
                            Toast.makeText(this, "Error resolving user profile.", Toast.LENGTH_SHORT).show()
                            adapter.updateData(emptyList())
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to fetch users/$authUid: ${e.message}")
                Toast.makeText(this, "Error accessing user profile.", Toast.LENGTH_SHORT).show()
                adapter.updateData(emptyList())
            }
    }

    /**
     * Start the realtime listener for requests for the given Firestore user document id.
     */
    private fun startRealtimeListenerForUser(firestoreUserId: String) {
        listener?.remove()
        listener = db.collection(FirestoreFields.REQUESTS)
            .whereEqualTo("customerId", firestoreUserId)
            .addSnapshotListener { snaps, e ->
                if (e != null) {
                    Toast.makeText(this, "Failed to load history: ${e.message}", Toast.LENGTH_SHORT).show()
                    Log.e(TAG, "Firestore listener error: ${e.message}")
                    return@addSnapshotListener
                }

                if (snaps == null) {
                    adapter.updateData(emptyList())
                    return@addSnapshotListener
                }

                val newList = mutableListOf<HistoryItem>()
                for (doc in snaps.documents) {
                    try {
                        // Normalize and map safely using JobNormalizer
                        val sch = JobNormalizer.requestDocToSchedule(doc)

                        // Map rating fields if present
                        val rating = doc.getLong("rating")
                        val ratingComment = doc.getString("ratingComment")
                        val ratedAtMillis = doc.getTimestamp("ratedAt")?.toDate()?.time

                        // Build history item
                        newList.add(
                            HistoryItem(
                                id = sch.scheduleId,
                                customerName = sch.customerName,
                                address = sch.address,
                                date = sch.date,
                                time = sch.time,
                                unitsCount = sch.technicianIds.size,
                                technicians = sch.technicians,
                                normalizedStatus = sch.normalizedStatus,
                                origin = "request",
                                rating = rating,
                                ratingComment = ratingComment,
                                ratedAtMillis = ratedAtMillis
                            )
                        )
                    } catch (ex: Exception) {
                        // don't let one bad document stop the list; log and continue
                        Log.w(TAG, "Skipped document ${doc.id} while building history: ${ex.message}")
                    }
                }

                // Sort by date+time descending (stable)
                newList.sortByDescending { it.date + it.time }
                adapter.updateData(newList)
            }
    }

    override fun onStop() {
        super.onStop()
        listener?.remove()
        listener = null
    }

    override fun onDestroy() {
        super.onDestroy()
        listener?.remove()
        listener = null
    }
}
