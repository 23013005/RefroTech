package com.example.refrotech

import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class Register : AppCompatActivity() {

    private lateinit var fullname: EditText
    private lateinit var email: EditText
    private lateinit var password: EditText
    private lateinit var confirmPassword: EditText
    private lateinit var registerButton: FrameLayout
    private lateinit var loginRedirect: FrameLayout
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var loadingDialog: AlertDialog

    // ------------------------------------------------------------------
    // LOADING KEREN (DIALOG + LOTTIE)
    // ------------------------------------------------------------------
    private fun showLoading() {
        val view = layoutInflater.inflate(R.layout.dialog_loading, null)

        loadingDialog = AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(false)
            .create()

        loadingDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        loadingDialog.window?.setWindowAnimations(R.style.DialogFadeAnimation)


        loadingDialog.show()
    }

    private fun hideLoading() {
        if (this::loadingDialog.isInitialized) loadingDialog.dismiss()
    }

    // ------------------------------------------------------------------
    // ON CREATE
    // ------------------------------------------------------------------
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        fullname = findViewById(R.id.fullname_input)
        email = findViewById(R.id.email_input)
        password = findViewById(R.id.password_input)
        confirmPassword = findViewById(R.id.password_confirmation_input)
        registerButton = findViewById(R.id.register_button)
        loginRedirect = findViewById(R.id.cust_login_page_button)

        // ---------------------------------------------------------
        // Animasi tombol
        // ---------------------------------------------------------
        val animPress = AnimationUtils.loadAnimation(this, R.anim.button_press)
        val animRelease = AnimationUtils.loadAnimation(this, R.anim.button_release)

        fun applyButtonAnim(frame: FrameLayout) {
            frame.setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> v.startAnimation(animPress)
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.startAnimation(animRelease)
                }
                false
            }
        }

        applyButtonAnim(registerButton)
        applyButtonAnim(loginRedirect)

        // Open login page
        loginRedirect.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        // Register clicked
        registerButton.setOnClickListener { registerUser() }
    }

    // ------------------------------------------------------------------
    // REGISTER USER
    // ------------------------------------------------------------------
    private fun registerUser() {

        val name = fullname.text.toString().trim()
        val emailText = email.text.toString().trim()
        val pass = password.text.toString().trim()
        val confirmPass = confirmPassword.text.toString().trim()

        // VALIDASI
        if (name.isEmpty() || emailText.isEmpty() || pass.isEmpty() || confirmPass.isEmpty()) {
            Toast.makeText(this, "Semua kolom wajib diisi", Toast.LENGTH_SHORT).show()
            return
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(emailText).matches()) {
            Toast.makeText(this, "Email tidak valid", Toast.LENGTH_SHORT).show()
            return
        }

        if (pass.length < 6) {
            Toast.makeText(this, "Kata sandi minimal 6 karakter", Toast.LENGTH_SHORT).show()
            return
        }

        if (pass != confirmPass) {
            Toast.makeText(this, "Konfirmasi kata sandi tidak cocok", Toast.LENGTH_SHORT).show()
            return
        }

        // DISABLE BUTTON + TAMPILKAN LOADING
        registerButton.isEnabled = false
        registerButton.alpha = 0.5f
        showLoading()

        // BUAT USER PADA FIREBASE AUTH
        auth.createUserWithEmailAndPassword(emailText, pass)
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    hideLoading()
                    registerButton.isEnabled = true
                    registerButton.alpha = 1f

                    Toast.makeText(
                        this,
                        "Registrasi gagal: ${task.exception?.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    return@addOnCompleteListener
                }

                // Kirim email verifikasi
                auth.currentUser?.sendEmailVerification()

                // Simpan data user ke Firestore
                val userId = auth.currentUser!!.uid

                val userData = hashMapOf(
                    "name" to name,
                    "email" to emailText,
                    "role" to "customer"
                )

                firestore.collection("users").document(userId)
                    .set(userData)
                    .addOnSuccessListener {
                        hideLoading()
                        registerButton.isEnabled = true
                        registerButton.alpha = 1f

                        Toast.makeText(
                            this,
                            "Akun berhasil dibuat! Verifikasi email sebelum login.",
                            Toast.LENGTH_LONG
                        ).show()

                        auth.signOut()

                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    }
                    .addOnFailureListener {
                        hideLoading()
                        registerButton.isEnabled = true
                        registerButton.alpha = 1f

                        Toast.makeText(
                            this,
                            "Gagal menyimpan data: ${it.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            }
    }
}
