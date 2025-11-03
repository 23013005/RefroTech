package com.example.refrotech

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class activity_pegawai_login : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var loginButton: FrameLayout
    private lateinit var custLoginButton: FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.pegawai_login)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        emailInput = findViewById(R.id.username_input)
        passwordInput = findViewById(R.id.password_input)
        loginButton = findViewById(R.id.login_button)
        custLoginButton = findViewById(R.id.cust_login_page_button)

        applyRippleEffect(loginButton)
        applyRippleEffect(custLoginButton)

        custLoginButton.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        loginButton.setOnClickListener { loginEmployee() }
    }

    private fun loginEmployee() {
        val email = emailInput.text.toString().trim()
        val password = passwordInput.text.toString().trim()

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Email dan password wajib diisi", Toast.LENGTH_SHORT).show()
            return
        }

        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                val userId = auth.currentUser!!.uid

                db.collection("users").document(userId).get()
                    .addOnSuccessListener { document ->
                        if (document.exists()) {
                            val role = document.getString("role")

                            when (role) {
                                "leader" -> {
                                    Toast.makeText(this, "Login berhasil sebagai Leader", Toast.LENGTH_SHORT).show()
                                    startActivity(Intent(this, leader_dashboard::class.java))
                                    finish()
                                }
                                "technician" -> {
                                    Toast.makeText(this, "Login berhasil sebagai Teknisi", Toast.LENGTH_SHORT).show()
                                    // Technician dashboard belum dibuat
                                }
                                else -> {
                                    Toast.makeText(this, "Akses ditolak. Gunakan login pelanggan.", Toast.LENGTH_LONG).show()
                                    auth.signOut()
                                }
                            }
                        } else {
                            Toast.makeText(this, "Data pengguna tidak ditemukan.", Toast.LENGTH_LONG).show()
                            auth.signOut()
                        }
                    }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Login gagal: ${it.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun applyRippleEffect(button: FrameLayout) {
        val rippleColor = Color.parseColor("#EAFFD7")
        val colorStateList = android.content.res.ColorStateList.valueOf(rippleColor)

        val mask = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 100f
            setColor(Color.WHITE)
        }

        val ripple = RippleDrawable(colorStateList, null, mask)
        button.foreground = ripple
    }
}
