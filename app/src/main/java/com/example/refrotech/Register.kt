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
import com.google.firebase.firestore.FirebaseFirestore

class Register : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var usernameInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var confirmPasswordInput: EditText
    private lateinit var registerButton: FrameLayout
    private lateinit var loginButton: FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        db = FirebaseFirestore.getInstance()

        usernameInput = findViewById(R.id.username_input)
        passwordInput = findViewById(R.id.password_input)
        confirmPasswordInput = findViewById(R.id.password_confirmation_input)
        registerButton = findViewById(R.id.register_button)
        loginButton = findViewById(R.id.cust_login_page_button)

        // 🔹 Apply ripple effects to buttons
        applyRippleEffect(registerButton)
        applyRippleEffect(loginButton)

        // 🔹 Go back to Customer Login
        loginButton.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        // 🔹 Register new customer (unique username)
        registerButton.setOnClickListener {
            val username = usernameInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()
            val confirmPassword = confirmPasswordInput.text.toString().trim()

            if (username.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(this, "Please fill all fields.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password != confirmPassword) {
                Toast.makeText(this, "Passwords do not match.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 🔸 Check if username already exists
            db.collection("users")
                .whereEqualTo("username", username)
                .get()
                .addOnSuccessListener { result ->
                    if (!result.isEmpty) {
                        Toast.makeText(this, "Username already exists. Please choose another.", Toast.LENGTH_SHORT).show()
                    } else {
                        // Save new unique user
                        val user = hashMapOf(
                            "username" to username,
                            "password" to password,
                            "role" to "customer"
                        )

                        db.collection("users")
                            .add(user)
                            .addOnSuccessListener {
                                Toast.makeText(this, "Registration successful!", Toast.LENGTH_SHORT).show()
                                startActivity(Intent(this, MainActivity::class.java))
                                finish()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, "Failed to register: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error checking username: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }
    }

    // 🔸 Ripple effect function (rounded blue ripple)
    private fun applyRippleEffect(button: FrameLayout) {
        val rippleColor = Color.parseColor("#EAFFD7") // RefroTech blue
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
