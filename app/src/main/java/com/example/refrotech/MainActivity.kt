package com.example.refrotech

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var loginButton: FrameLayout
    private lateinit var registerButton: FrameLayout
    private lateinit var empLoginButton: FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        emailInput = findViewById(R.id.username_input) // used as email
        passwordInput = findViewById(R.id.password_input)
        loginButton = findViewById(R.id.login_button)
        registerButton = findViewById(R.id.register_button)
        empLoginButton = findViewById(R.id.emp_login_page_button)

        applyRippleEffect(loginButton)
        applyRippleEffect(registerButton)
        applyRippleEffect(empLoginButton)

        registerButton.setOnClickListener {
            startActivity(Intent(this, Register::class.java))
        }

        empLoginButton.setOnClickListener {
            startActivity(Intent(this, activity_pegawai_login::class.java))
            finish()
        }

        loginButton.setOnClickListener { loginUser() }
        loginButton.setOnLongClickListener {
            showForgotPasswordDialog()
            true
        }
    }

    private fun loginUser() {
        val email = emailInput.text.toString().trim()
        val password = passwordInput.text.toString().trim()

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Email dan kata sandi wajib diisi", Toast.LENGTH_SHORT).show()
            return
        }

        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                val user = auth.currentUser
                if (user != null && user.isEmailVerified) {
                    Toast.makeText(this, "Login berhasil!", Toast.LENGTH_SHORT).show()

                    // ✅ Navigate to Customer Dashboard
                    val intent = Intent(this, DashboardCustomer::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()

                } else {
                    Toast.makeText(this, "Verifikasi email sebelum login!", Toast.LENGTH_LONG).show()
                    auth.signOut()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Login gagal: ${it.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun showForgotPasswordDialog() {
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(50, 20, 50, 0)

        val emailField = EditText(this)
        emailField.hint = "Masukkan email Anda"
        emailField.setPadding(30, 20, 30, 20)
        layout.addView(emailField)

        AlertDialog.Builder(this)
            .setTitle("Lupa Kata Sandi")
            .setMessage("Masukkan email yang terdaftar sebagai pelanggan.")
            .setView(layout)
            .setPositiveButton("Kirim") { _, _ ->
                val email = emailField.text.toString().trim()
                if (email.isEmpty()) {
                    Toast.makeText(this, "Email tidak boleh kosong", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                // ✅ Only allow customer accounts to reset password
                db.collection("users")
                    .whereEqualTo("email", email)
                    .whereEqualTo("role", "customer")
                    .get()
                    .addOnSuccessListener { result ->
                        if (!result.isEmpty) {
                            auth.sendPasswordResetEmail(email)
                                .addOnSuccessListener {
                                    Toast.makeText(
                                        this,
                                        "Silakan cek email Anda untuk reset password.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                                .addOnFailureListener {
                                    Toast.makeText(
                                        this,
                                        "Gagal mengirim email: ${it.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                        } else {
                            Toast.makeText(
                                this,
                                "Email tidak terdaftar sebagai pelanggan.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
            }
            .setNegativeButton("Batal", null)
            .show()
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
