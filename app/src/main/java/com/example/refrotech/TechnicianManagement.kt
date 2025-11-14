package com.example.refrotech

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore

class TechnicianManagement : AppCompatActivity() {

    private lateinit var recyclerTechnicians: RecyclerView
    private lateinit var btnAddTechnician: FrameLayout

    // BOTTOM NAVIGATION
    private lateinit var navDashboard: LinearLayout
    private lateinit var navTechnician: LinearLayout
    private lateinit var navRequests: LinearLayout

    private lateinit var technicianAdapter: TechnicianManageAdapter
    private val technicianList = mutableListOf<Technician>()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_technician_management)

        recyclerTechnicians = findViewById(R.id.recyclerTechnicians)
        btnAddTechnician = findViewById(R.id.btnAddTechnician)

        navDashboard = findViewById(R.id.navDashboard)
        navTechnician = findViewById(R.id.navTechnician)
        navRequests = findViewById(R.id.navRequests)

        recyclerTechnicians.layoutManager = LinearLayoutManager(this)

        technicianAdapter = TechnicianManageAdapter(
            technicians = technicianList,
            onEditClick = { technician -> showEditDialog(technician) },
            onDeleteClick = { technician -> confirmDeleteTechnician(technician) }
        )

        recyclerTechnicians.adapter = technicianAdapter

        // Load technicians from Firestore
        loadTechnicians()

        // Add technician
        btnAddTechnician.setOnClickListener { showAddTechnicianDialog() }

        // ===== BOTTOM NAVIGATION =====

        // Go to Leader Dashboard
        navDashboard.setOnClickListener {
            val intent = Intent(this, leader_dashboard::class.java)
            startActivity(intent)
            finish()
        }

        // Already in technician page
        navTechnician.setOnClickListener {
            Toast.makeText(this, "Already in Technician Management", Toast.LENGTH_SHORT).show()
        }

        // Go to Leader Confirmation Page (Requests)
        navRequests.setOnClickListener {
            val intent = Intent(this, LeaderConfirmationActivity::class.java)
            startActivity(intent)
        }
    }

    private fun loadTechnicians() {
        technicianList.clear()
        db.collection("users")
            .whereEqualTo("role", "technician")
            .get()
            .addOnSuccessListener { result ->
                for (doc in result) {
                    val technician = Technician(
                        id = doc.id,
                        name = doc.getString("name") ?: "Tanpa Nama",
                        username = doc.getString("username") ?: "-",
                        password = doc.getString("password") ?: "-"
                    )
                    technicianList.add(technician)
                }
                technicianAdapter.notifyDataSetChanged()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Gagal memuat data teknisi.", Toast.LENGTH_SHORT).show()
            }
    }

    // ========= ADD TECHNICIAN =========
    private fun showAddTechnicianDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_technician, null)

        val etName = dialogView.findViewById<EditText>(R.id.etTechnicianName)
        val etUsername = dialogView.findViewById<EditText>(R.id.etTechnicianUsername)
        val etPassword = dialogView.findViewById<EditText>(R.id.etTechnicianPassword)
        val toggleBtn = dialogView.findViewById<ImageView>(R.id.btnTogglePassword)

        var isVisible = false

        toggleBtn.setOnClickListener {
            isVisible = !isVisible
            if (isVisible) {
                etPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                toggleBtn.setImageResource(R.drawable.ic_eye_open)
            } else {
                etPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                toggleBtn.setImageResource(R.drawable.ic_eye_closed)
            }
            etPassword.setSelection(etPassword.text.length)
        }

        AlertDialog.Builder(this)
            .setTitle("Tambah Teknisi")
            .setView(dialogView)
            .setPositiveButton("Tambah") { _, _ ->
                val name = etName.text.toString().trim()
                val username = etUsername.text.toString().trim()
                val password = etPassword.text.toString().trim()

                if (name.isNotEmpty() && username.isNotEmpty() && password.isNotEmpty()) {
                    val data = hashMapOf(
                        "name" to name,
                        "username" to username,
                        "password" to password,
                        "role" to "technician"
                    )

                    db.collection("users")
                        .add(data)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Teknisi berhasil ditambahkan", Toast.LENGTH_SHORT).show()
                            loadTechnicians()
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "Gagal menambah teknisi", Toast.LENGTH_SHORT).show()
                        }
                } else {
                    Toast.makeText(this, "Isi semua kolom!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Batal", null)
            .create()
            .show()
    }

    // ========= EDIT TECHNICIAN =========
    private fun showEditDialog(technician: Technician) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_technician, null)

        val etName = dialogView.findViewById<EditText>(R.id.etTechnicianName)
        val etUsername = dialogView.findViewById<EditText>(R.id.etTechnicianUsername)
        val etPassword = dialogView.findViewById<EditText>(R.id.etTechnicianPassword)
        val toggleBtn = dialogView.findViewById<ImageView>(R.id.btnTogglePassword)

        etName.setText(technician.name)
        etUsername.setText(technician.username)
        etPassword.setText(technician.password)

        var isVisible = false

        toggleBtn.setOnClickListener {
            isVisible = !isVisible
            if (isVisible) {
                etPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                toggleBtn.setImageResource(R.drawable.ic_eye_open)
            } else {
                etPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                toggleBtn.setImageResource(R.drawable.ic_eye_closed)
            }
            etPassword.setSelection(etPassword.text.length)
        }

        AlertDialog.Builder(this)
            .setTitle("Edit Teknisi")
            .setView(dialogView)
            .setPositiveButton("Simpan") { _, _ ->
                val updatedName = etName.text.toString().trim()
                val updatedUsername = etUsername.text.toString().trim()
                val updatedPassword = etPassword.text.toString().trim()

                if (updatedName.isNotEmpty() && updatedUsername.isNotEmpty() && updatedPassword.isNotEmpty()) {
                    db.collection("users").document(technician.id)
                        .update(
                            mapOf(
                                "name" to updatedName,
                                "username" to updatedUsername,
                                "password" to updatedPassword
                            )
                        )
                        .addOnSuccessListener {
                            Toast.makeText(this, "Data teknisi diperbarui", Toast.LENGTH_SHORT).show()
                            loadTechnicians()
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "Gagal memperbarui data", Toast.LENGTH_SHORT).show()
                        }
                } else {
                    Toast.makeText(this, "Isi semua kolom!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Batal", null)
            .create()
            .show()
    }

    private fun confirmDeleteTechnician(technician: Technician) {
        AlertDialog.Builder(this)
            .setTitle("Hapus Teknisi")
            .setMessage("Apakah Anda yakin ingin menghapus ${technician.name}?")
            .setPositiveButton("Hapus") { _, _ ->
                db.collection("users").document(technician.id)
                    .delete()
                    .addOnSuccessListener {
                        Toast.makeText(this, "Teknisi dihapus", Toast.LENGTH_SHORT).show()
                        loadTechnicians()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Gagal menghapus teknisi", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Batal", null)
            .create()
            .show()
    }
}
