package com.example.refrotech

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.*

/**
 * CustomerHistory — robust version + date filter
 *
 * Behavior:
 * - Prefers userId from intent extras ("userId")
 * - If missing/blank, resolves Firestore user document ID by inspecting the currently authenticated Firebase user:
 *     1) try to find a user doc whose document ID equals auth.uid
 *     2) try to find user doc where field "authUid" == auth.uid (if you store it)
 *     3) try to find user doc where field "email" == auth.currentUser.email
 *     4) try to find user doc where field "phone" == auth.currentUser.phoneNumber
 * - Once userId resolved, listens on requests where requests.customerId == userId
 * - Adds a date filter (creation date) with options:
 *     All Dates, Today, Last 7 Days, Last 30 Days, Custom Range
 * - Sorting: newest -> oldest by createdAtMillis
 */
class CustomerHistory : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private lateinit var recyclerHistory: androidx.recyclerview.widget.RecyclerView
    private lateinit var adapter: HistoryAdapter
    private lateinit var userId: String // Firestore user document id used in requests.customerId

    // NAVIGATION
    private lateinit var navHome: LinearLayout
    private lateinit var navHistory: LinearLayout

    private var listener: ListenerRegistration? = null

    private val TAG = "CustomerHistory"

    // === DATE FILTER UI ===
    private lateinit var spinnerDateFilter: Spinner

    // All items from Firestore, filtered in-memory based on date filter
    private val allHistoryItems = mutableListOf<HistoryItem>()

    // Date filter types
    private enum class DateFilterType {
        ALL,
        TODAY,
        LAST_7_DAYS,
        LAST_30_DAYS,
        CUSTOM_RANGE
    }

    private var currentFilterType: DateFilterType = DateFilterType.ALL
    private var customFromMillis: Long? = null
    private var customToMillis: Long? = null

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
        spinnerDateFilter = findViewById(R.id.spinnerDateFilter)

        setupNavigation()
        setupDateFilterSpinner()

        // Attempt to resolve the Firestore userId to use for queries.
        val intentUserId = intent.getStringExtra("userId")
        if (!intentUserId.isNullOrBlank()) {
            userId = intentUserId
            startRealtimeListenerForUser(userId)
            return
        }

        val fUser = auth.currentUser
        if (fUser == null) {
            Toast.makeText(this, "User not signed in. Please login.", Toast.LENGTH_SHORT).show()
            Log.w(TAG, "No FirebaseAuth user. Intent userId was empty.")
            adapter.updateData(emptyList())
            return
        }

        resolveFirestoreUserId(fUser.uid, fUser.email, fUser.phoneNumber)
    }

    private fun setupNavigation() {
        navHome.setOnClickListener {
            val intent = Intent(this, DashboardCustomer::class.java)
            if (::userId.isInitialized && userId.isNotBlank()) {
                intent.putExtra("userId", userId)
            }
            startActivity(intent)
            finish()
        }

        navHistory.setOnClickListener {
            // already here - no-op
        }
    }

    /**
     * Date filter spinner: All Dates, Today, Last 7 Days, Last 30 Days, Custom Range
     */
    private fun setupDateFilterSpinner() {
        val options = listOf(
            "Semua tanggal",
            "Hari ini",
            "7 hari terakhir",
            "30 hari terakhir",
            "Rentang tanggal..."
        )

        val spinnerAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            options
        )
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerDateFilter.adapter = spinnerAdapter

        spinnerDateFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: android.view.View?,
                position: Int,
                id: Long
            ) {
                when (position) {
                    0 -> { // All dates
                        currentFilterType = DateFilterType.ALL
                        customFromMillis = null
                        customToMillis = null
                        applyDateFilter()
                    }
                    1 -> { // Today
                        currentFilterType = DateFilterType.TODAY
                        customFromMillis = null
                        customToMillis = null
                        applyDateFilter()
                    }
                    2 -> { // Last 7 days
                        currentFilterType = DateFilterType.LAST_7_DAYS
                        customFromMillis = null
                        customToMillis = null
                        applyDateFilter()
                    }
                    3 -> { // Last 30 days
                        currentFilterType = DateFilterType.LAST_30_DAYS
                        customFromMillis = null
                        customToMillis = null
                        applyDateFilter()
                    }
                    4 -> { // Custom range
                        currentFilterType = DateFilterType.CUSTOM_RANGE
                        showCustomDateRangePicker()
                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // no-op
            }
        }
    }

    /**
     * Show two DatePickers (from/to) to define custom range based on creation date.
     */
    private fun showCustomDateRangePicker() {
        val cal = Calendar.getInstance()

        // 1) FROM date picker
        val fromPicker = DatePickerDialog(
            this,
            { _, y, m, d ->
                val fromCal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, y)
                    set(Calendar.MONTH, m)
                    set(Calendar.DAY_OF_MONTH, d)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                customFromMillis = fromCal.timeInMillis

                // 2) TO date picker
                val toPicker = DatePickerDialog(
                    this,
                    { _, y2, m2, d2 ->
                        val toCal = Calendar.getInstance().apply {
                            set(Calendar.YEAR, y2)
                            set(Calendar.MONTH, m2)
                            set(Calendar.DAY_OF_MONTH, d2)
                            set(Calendar.HOUR_OF_DAY, 23)
                            set(Calendar.MINUTE, 59)
                            set(Calendar.SECOND, 59)
                            set(Calendar.MILLISECOND, 999)
                        }
                        customToMillis = toCal.timeInMillis

                        if (customToMillis != null && customFromMillis != null &&
                            customToMillis!! < customFromMillis!!
                        ) {
                            Toast.makeText(
                                this,
                                "Tanggal akhir tidak boleh sebelum tanggal awal.",
                                Toast.LENGTH_SHORT
                            ).show()
                            // Reset to all dates if invalid
                            currentFilterType = DateFilterType.ALL
                            spinnerDateFilter.setSelection(0)
                        } else {
                            applyDateFilter()
                        }
                    },
                    fromCal.get(Calendar.YEAR),
                    fromCal.get(Calendar.MONTH),
                    fromCal.get(Calendar.DAY_OF_MONTH)
                )

                toPicker.datePicker.minDate = fromCal.timeInMillis
                toPicker.show()
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        )

        fromPicker.show()
    }

    /**
     * Resolve the Firestore users document ID by trying multiple strategies.
     * Calls startRealtimeListenerForUser once resolved.
     */
    private fun resolveFirestoreUserId(authUid: String, email: String?, phone: String?) {
        db.collection(FirestoreFields.USERS).document(authUid).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    userId = doc.id
                    Log.d(TAG, "Resolved userId by direct doc id = $userId")
                    startRealtimeListenerForUser(userId)
                } else {
                    db.collection(FirestoreFields.USERS)
                        .whereEqualTo("authUid", authUid)
                        .get()
                        .addOnSuccessListener { snapByAuth ->
                            if (!snapByAuth.isEmpty) {
                                val docAuth = snapByAuth.documents[0]
                                userId = docAuth.id
                                Log.d(TAG, "Resolved userId by authUid field = $userId")
                                startRealtimeListenerForUser(userId)
                                return@addOnSuccessListener
                            }

                            if (!email.isNullOrBlank()) {
                                db.collection(FirestoreFields.USERS)
                                    .whereEqualTo("email", email)
                                    .get()
                                    .addOnSuccessListener { snapByEmail ->
                                        if (!snapByEmail.isEmpty) {
                                            val docEmail = snapByEmail.documents[0]
                                            userId = docEmail.id
                                            Log.d(TAG, "Resolved userId by email = $userId")
                                            startRealtimeListenerForUser(userId)
                                            return@addOnSuccessListener
                                        }

                                        if (!phone.isNullOrBlank()) {
                                            db.collection(FirestoreFields.USERS)
                                                .whereEqualTo("phone", phone)
                                                .get()
                                                .addOnSuccessListener { snapByPhone ->
                                                    if (!snapByPhone.isEmpty) {
                                                        val docPhone = snapByPhone.documents[0]
                                                        userId = docPhone.id
                                                        Log.d(TAG, "Resolved userId by phone = $userId")
                                                        startRealtimeListenerForUser(userId)
                                                    } else {
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
                                                    Toast.makeText(
                                                        this,
                                                        "Error resolving user profile.",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
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
                                        Toast.makeText(
                                            this,
                                            "Error resolving user profile.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        adapter.updateData(emptyList())
                                    }
                                return@addOnSuccessListener
                            } else {
                                if (!phone.isNullOrBlank()) {
                                    db.collection(FirestoreFields.USERS)
                                        .whereEqualTo("phone", phone)
                                        .get()
                                        .addOnSuccessListener { snapByPhone2 ->
                                            if (!snapByPhone2.isEmpty) {
                                                val docPhone2 = snapByPhone2.documents[0]
                                                userId = docPhone2.id
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
                                            Toast.makeText(
                                                this,
                                                "Error resolving user profile.",
                                                Toast.LENGTH_SHORT
                                            ).show()
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
                            Toast.makeText(
                                this,
                                "Error resolving user profile.",
                                Toast.LENGTH_SHORT
                            ).show()
                            adapter.updateData(emptyList())
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to fetch users/$authUid: ${e.message}")
                Toast.makeText(
                    this,
                    "Error accessing user profile.",
                    Toast.LENGTH_SHORT
                ).show()
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
                    Toast.makeText(
                        this,
                        "Failed to load history: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    Log.e(TAG, "Firestore listener error: ${e.message}")
                    return@addSnapshotListener
                }

                if (snaps == null) {
                    allHistoryItems.clear()
                    adapter.updateData(emptyList())
                    return@addSnapshotListener
                }

                val newList = mutableListOf<HistoryItem>()
                for (doc in snaps.documents) {
                    try {
                        val sch = JobNormalizer.requestDocToSchedule(doc)

                        // rating fields
                        val rating = doc.getLong("rating")
                        val ratingComment = doc.getString("ratingComment")
                        val ratedAtMillis = doc.getTimestamp("ratedAt")?.toDate()?.time

                        // creation timestamp for filtering
                        val createdAtMillis = doc.getLong("createdAtMillis")
                            ?: doc.getTimestamp("createdAt")?.toDate()?.time

                        newList.add(
                            HistoryItem(
                                id = sch.scheduleId,
                                customerName = sch.customerName,
                                address = sch.address,
                                date = sch.date,
                                time = sch.time,
                                unitsCount = sch.units.size,
                                technicians = sch.technicians,
                                normalizedStatus = sch.normalizedStatus,
                                origin = "request",
                                rating = rating,
                                ratingComment = ratingComment,
                                ratedAtMillis = ratedAtMillis,
                                createdAtMillis = createdAtMillis
                            )
                        )
                    } catch (ex: Exception) {
                        Log.w(
                            TAG,
                            "Skipped document ${doc.id} while building history: ${ex.message}"
                        )
                    }
                }

                allHistoryItems.clear()
                allHistoryItems.addAll(newList)
                applyDateFilter()
            }
    }

    /**
     * Apply the current date filter over allHistoryItems, then sort newest -> oldest by createdAtMillis.
     */
    private fun applyDateFilter() {
        if (allHistoryItems.isEmpty()) {
            adapter.updateData(emptyList())
            return
        }

        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()

        val filtered = when (currentFilterType) {
            DateFilterType.ALL -> {
                allHistoryItems
            }

            DateFilterType.TODAY -> {
                cal.timeInMillis = now
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                cal.set(Calendar.MILLISECOND, 999)
                val end = cal.timeInMillis

                allHistoryItems.filter { it.createdAtMillis?.let { ts -> ts in start..end } == true }
            }

            DateFilterType.LAST_7_DAYS -> {
                val start = now - 7L * 24L * 60L * 60L * 1000L
                allHistoryItems.filter { it.createdAtMillis?.let { ts -> ts in start..now } == true }
            }

            DateFilterType.LAST_30_DAYS -> {
                val start = now - 30L * 24L * 60L * 60L * 1000L
                allHistoryItems.filter { it.createdAtMillis?.let { ts -> ts in start..now } == true }
            }

            DateFilterType.CUSTOM_RANGE -> {
                val from = customFromMillis
                val to = customToMillis
                if (from == null || to == null) {
                    allHistoryItems
                } else {
                    allHistoryItems.filter { it.createdAtMillis?.let { ts -> ts in from..to } == true }
                }
            }
        }

        // newest -> oldest by createdAtMillis; fallback 0 if null
        val sorted = filtered.sortedByDescending { it.createdAtMillis ?: 0L }
        adapter.updateData(sorted)
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
