package com.example.refrotech

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class TechnicianManagement : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private lateinit var recyclerTechnicians: androidx.recyclerview.widget.RecyclerView
    private lateinit var btnAddTechnician: FrameLayout
    private lateinit var adapter: TechnicianManageAdapter

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_technician_management)

        recyclerTechnicians = findViewById(R.id.recyclerTechnicians)
        btnAddTechnician = findViewById(R.id.btnAddTechnician)

        adapter = TechnicianManageAdapter(
            mutableListOf(),
            onEdit = { id -> showEditDialog(id) },
            onDelete = { id -> confirmDeleteTechnician(id) },
            onSetAvailability = { id, name -> showSetUnavailabilityDialog(id, name) }
        )

        recyclerTechnicians.layoutManager = LinearLayoutManager(this)
        recyclerTechnicians.adapter = adapter

        btnAddTechnician.setOnClickListener { showAddTechnicianDialog() }

        loadTechnicians()
        setupBottomNav()
    }

    private fun loadTechnicians() {
        db.collection(FirestoreFields.USERS)
            .whereEqualTo("role", "technician")
            .get()
            .addOnSuccessListener { snap ->
                val docs = snap.documents.map { d ->
                    mapOf(
                        "id" to d.id,
                        "name" to (d.getString("name") ?: ""),
                        "username" to (d.getString("username") ?: "")
                    )
                }
                adapter.updateData(docs)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Gagal memuat teknisi: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showAddTechnicianDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_technician, null)
        val etName = view.findViewById<EditText>(R.id.etTechnicianName)
        val etUsername = view.findViewById<EditText>(R.id.etTechnicianUsername)
        val etPassword = view.findViewById<EditText>(R.id.etTechnicianPassword)
        val toggleBtn = view.findViewById<ImageView>(R.id.btnTogglePassword)

        var isVisible = false
        toggleBtn?.setOnClickListener {
            isVisible = !isVisible
            if (isVisible) {
                etPassword.inputType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                toggleBtn.setImageResource(R.drawable.ic_eye_open)
            } else {
                etPassword.inputType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_VARIATION_PASSWORD
                toggleBtn.setImageResource(R.drawable.ic_eye_closed)
            }
            etPassword.setSelection(etPassword.text.length)
        }

        AlertDialog.Builder(this)
            .setTitle("Tambah Teknisi")
            .setView(view)
            .setPositiveButton("Tambah") { dialog, _ ->
                val name = etName.text.toString().trim()
                val username = etUsername.text.toString().trim()
                val password = etPassword.text.toString().trim()

                if (name.isEmpty() || username.isEmpty() || password.isEmpty()) {
                    Toast.makeText(this, "Lengkapi data", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val data = hashMapOf<String, Any>(
                    "name" to name,
                    "username" to username,
                    "password" to password,
                    "role" to "technician"
                )

                db.collection(FirestoreFields.USERS)
                    .add(data)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Teknisi ditambahkan", Toast.LENGTH_SHORT).show()
                        loadTechnicians()
                        dialog.dismiss()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Gagal: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showEditDialog(techId: String) {
        db.collection(FirestoreFields.USERS).document(techId).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    Toast.makeText(this, "Data teknisi tidak ditemukan", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_technician, null)
                val etName = view.findViewById<EditText>(R.id.etTechnicianName)
                val etUsername = view.findViewById<EditText>(R.id.etTechnicianUsername)
                val etPassword = view.findViewById<EditText>(R.id.etTechnicianPassword)
                val toggleBtn = view.findViewById<ImageView>(R.id.btnTogglePassword)

                etName.setText(doc.getString("name") ?: "")
                etUsername.setText(doc.getString("username") ?: "")
                etPassword.setText(doc.getString("password") ?: "")

                var isVisible = false
                toggleBtn?.setOnClickListener {
                    isVisible = !isVisible
                    if (isVisible) {
                        etPassword.inputType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                        toggleBtn.setImageResource(R.drawable.ic_eye_open)
                    } else {
                        etPassword.inputType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_VARIATION_PASSWORD
                        toggleBtn.setImageResource(R.drawable.ic_eye_closed)
                    }
                    etPassword.setSelection(etPassword.text.length)
                }

                AlertDialog.Builder(this)
                    .setTitle("Edit Teknisi")
                    .setView(view)
                    .setPositiveButton("Simpan") { dialog, _ ->
                        val newName = etName.text.toString().trim()
                        val newUsername = etUsername.text.toString().trim()
                        val newPassword = etPassword.text.toString().trim()

                        if (newName.isEmpty() || newUsername.isEmpty() || newPassword.isEmpty()) {
                            Toast.makeText(this, "Lengkapi data", Toast.LENGTH_SHORT).show()
                            return@setPositiveButton
                        }

                        val updates = hashMapOf<String, Any>(
                            "name" to newName,
                            "username" to newUsername,
                            "password" to newPassword
                        )

                        db.collection(FirestoreFields.USERS).document(techId)
                            .update(updates)
                            .addOnSuccessListener {
                                Toast.makeText(this, "Perubahan disimpan", Toast.LENGTH_SHORT).show()
                                loadTechnicians()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, "Gagal menyimpan: ${e.message}", Toast.LENGTH_SHORT).show()
                            }

                        dialog.dismiss()
                    }
                    .setNegativeButton("Batal", null)
                    .show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Gagal memuat teknisi: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun confirmDeleteTechnician(techId: String) {
        AlertDialog.Builder(this)
            .setTitle("Hapus Teknisi")
            .setMessage("Hapus teknisi ini?")
            .setPositiveButton("Ya") { _, _ ->
                db.collection(FirestoreFields.USERS).document(techId)
                    .delete()
                    .addOnSuccessListener {
                        Toast.makeText(this, "Teknisi dihapus", Toast.LENGTH_SHORT).show()
                        loadTechnicians()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Gagal: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    /**
     * Show dialog to set unavailability for a technician.
     *
     * New behaviour (Option A):
     * - We store ONLY unavailablePeriods: a single-element list with a map { "start": yyyy-MM-dd, "end": yyyy-MM-dd? }
     * - If "until further notice" is selected, end will be saved as null in the map.
     * - This replaces the old unavailableFrom/unavailableTo fields.
     */
    private fun showSetUnavailabilityDialog(techId: String, techName: String) {

        db.collection(FirestoreFields.USERS).document(techId).get()
            .addOnSuccessListener { doc ->

                // Read existing unavailablePeriods first (if any). We'll pre-fill with first element.
                var currentStart: String? = null
                var currentEnd: String? = null
                val periods = doc.get("unavailablePeriods") as? List<*>
                if (!periods.isNullOrEmpty()) {
                    val first = periods[0] as? Map<*, *>
                    currentStart = first?.get("start")?.toString()
                    // treat explicit null as null; Firestore might render missing "end" as null or missing
                    currentEnd = if (first?.containsKey("end") == true) {
                        first["end"]?.toString()
                    } else {
                        null
                    }
                }

                val view = LayoutInflater.from(this).inflate(R.layout.dialog_set_unavailability, null)
                val btnStart = view.findViewById<Button>(R.id.btnStartDate)
                val btnEnd = view.findViewById<Button>(R.id.btnEndDate)
                val cbUntilFurther = view.findViewById<CheckBox>(R.id.cbUntilFurther)
                val tvChosen = view.findViewById<TextView>(R.id.tvChosenRange)

                var startStr = currentStart
                var endStr = currentEnd

                // Pre-fill UI
                if (startStr != null) {
                    if (endStr == null) tvChosen.text = "Start: $startStr\nEnd: (until further notice)"
                    else tvChosen.text = "Start: $startStr\nEnd: $endStr"
                } else tvChosen.text = "Start: -\nEnd: -"

                if (startStr != null && endStr == null) cbUntilFurther.isChecked = true

                btnStart.setOnClickListener {
                    val cal = Calendar.getInstance()
                    DatePickerDialog(this, { _, y, m, d ->
                        val c = Calendar.getInstance()
                        c.set(y, m, d)
                        startStr = dateFormat.format(c.time)
                        tvChosen.text = "Start: $startStr\nEnd: ${endStr ?: "-"}"
                    }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                }

                btnEnd.setOnClickListener {
                    val cal = Calendar.getInstance()
                    DatePickerDialog(this, { _, y, m, d ->
                        val c = Calendar.getInstance()
                        c.set(y, m, d)
                        endStr = dateFormat.format(c.time)
                        tvChosen.text = "Start: ${startStr ?: "-"}\nEnd: $endStr"
                        cbUntilFurther.isChecked = false
                    }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                }

                cbUntilFurther.setOnCheckedChangeListener { _, checked ->
                    if (checked) {
                        endStr = null
                        tvChosen.text = "Start: ${startStr ?: "-"}\nEnd: (until further notice)"
                    } else {
                        tvChosen.text = "Start: ${startStr ?: "-"}\nEnd: ${endStr ?: "-"}"
                    }
                }

                AlertDialog.Builder(this)
                    .setTitle("Set Unavailability for $techName")
                    .setView(view)
                    .setPositiveButton("Save") { dialog, _ ->

                        val s = startStr ?: run {
                            Toast.makeText(this, "Please choose start date", Toast.LENGTH_SHORT).show()
                            return@setPositiveButton
                        }

                        // endStr may be null if until further
                        val e: String? = if (cbUntilFurther.isChecked) null else endStr

                        // Build single-element periods list (replace existing)
                        val entry = if (e == null) {
                            mapOf<String, Any?>("start" to s, "end" to null)
                        } else {
                            mapOf<String, Any?>("start" to s, "end" to e)
                        }

                        // Replace the unavailablePeriods field with a single-element list
                        db.collection(FirestoreFields.USERS).document(techId)
                            .update(mapOf("unavailablePeriods" to listOf(entry)))
                            .addOnSuccessListener {
                                Toast.makeText(this, "Unavailability saved", Toast.LENGTH_SHORT).show()
                                loadTechnicians()
                            }
                            .addOnFailureListener { eEx ->
                                Toast.makeText(this, "Failed to save: ${eEx.message}", Toast.LENGTH_SHORT).show()
                            }

                        dialog.dismiss()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            .addOnFailureListener { ex ->
                Toast.makeText(this, "Failed to load technician data: ${ex.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupBottomNav() {
        findViewById<LinearLayout>(R.id.navDashboard).setOnClickListener {
            startActivity(Intent(this, LeaderDashboard::class.java))
            overridePendingTransition(0, 0)
        }

        findViewById<LinearLayout>(R.id.navTechnician).setOnClickListener {
            // already here; do nothing or you can provide feedback
        }

        findViewById<LinearLayout>(R.id.navRequests).setOnClickListener {
            startActivity(Intent(this, LeaderConfirmationActivity::class.java))
            overridePendingTransition(0, 0)
        }
    }
}
