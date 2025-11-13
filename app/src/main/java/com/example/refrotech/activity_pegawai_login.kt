package com.example.refrotech

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class activity_pegawai_login : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var usernameInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var loginButton: FrameLayout
    private lateinit var custLoginButton: FrameLayout
    private var isPasswordVisible = false   // 👈 added

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.pegawai_login)

        db = FirebaseFirestore.getInstance()

        usernameInput = findViewById(R.id.username_input)
        passwordInput = findViewById(R.id.password_input)
        loginButton = findViewById(R.id.login_button)
        custLoginButton = findViewById(R.id.cust_login_page_button)

        val togglePassword = findViewById<ImageView>(R.id.btnTogglePassword) // 👈 added

        applyRippleEffect(loginButton)
        applyRippleEffect(custLoginButton)

        // ===== Password Visibility Toggle =====
        togglePassword.setOnClickListener {
            isPasswordVisible = !isPasswordVisible

            if (isPasswordVisible) {
                // SHOW PASSWORD
                passwordInput.transformationMethod =
                    android.text.method.HideReturnsTransformationMethod.getInstance()
                togglePassword.setImageResource(R.drawable.ic_eye_open)
            } else {
                // HIDE PASSWORD
                passwordInput.transformationMethod =
                    android.text.method.PasswordTransformationMethod.getInstance()
                togglePassword.setImageResource(R.drawable.ic_eye_closed)
            }

            passwordInput.setSelection(passwordInput.text.length) // keep cursor at end
        }


        custLoginButton.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        loginButton.setOnClickListener { loginEmployee() }
    }

    private fun loginEmployee() {
        val username = usernameInput.text.toString().trim()
        val password = passwordInput.text.toString().trim()

        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Username dan password wajib diisi", Toast.LENGTH_SHORT).show()
            return
        }

        db.collection("users")
            .whereEqualTo("username", username)
            .whereEqualTo("password", password)
            .get()
            .addOnSuccessListener { result ->
                if (!result.isEmpty) {
                    val doc = result.documents[0]
                    val role = doc.getString("role") ?: ""

                    when (role) {
                        "leader" -> {
                            Toast.makeText(this, "Login berhasil sebagai Leader", Toast.LENGTH_SHORT).show()
                            val intent = Intent(this, leader_dashboard::class.java)
                            startActivity(intent)
                            finish()
                        }

                        "technician" -> {
                            Toast.makeText(this, "Login berhasil sebagai Teknisi", Toast.LENGTH_SHORT).show()
                            Toast.makeText(this, "Halaman dashboard teknisi belum tersedia", Toast.LENGTH_LONG).show()
                        }

                        else -> {
                            Toast.makeText(this, "Akses ditolak. Gunakan login pelanggan.", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    Toast.makeText(this, "Username atau password salah.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Gagal login: ${it.message}", Toast.LENGTH_LONG).show()
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
