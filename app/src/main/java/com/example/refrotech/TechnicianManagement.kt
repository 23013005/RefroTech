package com.example.refrotech

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore

class TechnicianManagement : AppCompatActivity() {

    private lateinit var recyclerTechnicians: RecyclerView
    private lateinit var btnAddTechnician: FrameLayout
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
        loadTechnicians()

        btnAddTechnician.setOnClickListener { showAddTechnicianDialog() }

        navDashboard.setOnClickListener { finish() }
        navTechnician.setOnClickListener {
            Toast.makeText(this, "Already in Technician Management", Toast.LENGTH_SHORT).show()
        }
        navRequests.setOnClickListener {
            Toast.makeText(this, "Request confirmation page not ready yet", Toast.LENGTH_SHORT).show()
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

    private fun showAddTechnicianDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_technician, null)
        val etName = dialogView.findViewById<EditText>(R.id.etTechnicianName)
        val etUsername = dialogView.findViewById<EditText>(R.id.etTechnicianEmail)
        etUsername.hint = "Username Teknisi"

        val etPassword = EditText(this).apply {
            hint = "Kata Sandi"
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(dialogView)
            addView(etPassword)
            setPadding(50, 20, 50, 10)
        }

        AlertDialog.Builder(this)
            .setTitle("Tambah Teknisi")
            .setView(container)
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

    private fun showEditDialog(technician: Technician) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_technician, null)
        val etName = dialogView.findViewById<EditText>(R.id.etTechnicianName)
        val etUsername = dialogView.findViewById<EditText>(R.id.etTechnicianEmail)
        etUsername.hint = "Username Teknisi"
        val etPassword = EditText(this).apply {
            hint = "Kata Sandi"
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            setText(technician.password)
        }

        etName.setText(technician.name)
        etUsername.setText(technician.username)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(dialogView)
            addView(etPassword)
            setPadding(50, 20, 50, 10)
        }

        AlertDialog.Builder(this)
            .setTitle("Edit Teknisi")
            .setView(container)
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
