package com.example.plsworkver3

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.random.Random

class RegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var nameInput: EditText
    private lateinit var surnameInput: EditText
    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var registerButton: Button
    private lateinit var loginLink: TextView
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        nameInput = findViewById(R.id.inputName)
        surnameInput = findViewById(R.id.inputSurname)
        emailInput = findViewById(R.id.inputEmail)
        passwordInput = findViewById(R.id.inputPassword)
        registerButton = findViewById(R.id.buttonRegister)
        loginLink = findViewById(R.id.linkLogin)
        progressBar = findViewById(R.id.progressBar)

        registerButton.setOnClickListener { attemptRegister() }
        loginLink.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun attemptRegister() {
        val name = nameInput.text.toString().trim()
        val surname = surnameInput.text.toString().trim()
        val email = emailInput.text.toString().trim()
        val password = passwordInput.text.toString().trim()

        if (name.isEmpty() || surname.isEmpty() || email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_empty_fields), Toast.LENGTH_SHORT).show()
            return
        }

        if (password.length < 6) {
            Toast.makeText(this, getString(R.string.error_password_short), Toast.LENGTH_SHORT).show()
            return
        }

        setLoading(true)
        android.util.Log.d("RegisterActivity", "Starting registration for: $email")

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                android.util.Log.d("RegisterActivity", "Auth createUser completed. Success: ${task.isSuccessful}")
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user == null) {
                        android.util.Log.e("RegisterActivity", "User is null after successful creation")
                        setLoading(false)
                        Toast.makeText(this, getString(R.string.error_register_generic), Toast.LENGTH_SHORT).show()
                        return@addOnCompleteListener
                    }

                    android.util.Log.d("RegisterActivity", "User created with UID: ${user.uid}")

                    // Generate 6-digit verification code
                    val code = (100000..999999).random().toString()
                    android.util.Log.d("RegisterActivity", "Generated verification code: $code")

                    // Store verification code temporarily in Auth user metadata or SharedPreferences
                    // We'll create the Firestore document only after email verification
                    val prefs = getSharedPreferences("verification_prefs", MODE_PRIVATE)
                    prefs.edit().apply {
                        putString("pending_verification_code", code)
                        putString("pending_name", name)
                        putString("pending_surname", surname)
                        putString("pending_email", email)
                        putString("pending_uid", user.uid)
                        apply()
                    }
                    
                    android.util.Log.d("RegisterActivity", "Verification data stored. Redirecting to verify screen.")
                    redirectToVerify(email, code, name)
                } else {
                    android.util.Log.e("RegisterActivity", "Auth creation failed: ${task.exception?.message}")
                    setLoading(false)
                    val errorMsg = task.exception?.localizedMessage ?: getString(R.string.error_register_generic)
                    Toast.makeText(
                        this,
                        errorMsg,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            .addOnFailureListener { e ->
                android.util.Log.e("RegisterActivity", "Auth createUser exception: ${e.message}", e)
                setLoading(false)
                Toast.makeText(
                    this,
                    "Registration failed: ${e.localizedMessage ?: getString(R.string.error_register_generic)}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun redirectToVerify(email: String, code: String, name: String) {
        setLoading(false)
        
                    // Fire-and-forget call to backend that sends email via SendGrid
                    // Don't wait for this - it's async and non-blocking
                    try {
                        EmailVerificationSender.sendVerificationEmail(this, email, code, name)
                        android.util.Log.d("RegisterActivity", "Email send request initiated (non-blocking)")
                    } catch (e: Exception) {
                        android.util.Log.e("RegisterActivity", "Error initiating email send: ${e.message}", e)
                        // Don't block on email send errors
                    }

        Toast.makeText(this, getString(R.string.register_success), Toast.LENGTH_SHORT).show()

        // Always redirect to verify screen after successful registration
        val intent = Intent(this, VerifyEmailActivity::class.java)
        intent.putExtra("email", email)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        android.util.Log.d("RegisterActivity", "Starting VerifyEmailActivity")
        startActivity(intent)
        finish()
    }

    private fun setLoading(loading: Boolean) {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        registerButton.isEnabled = !loading
        loginLink.isEnabled = !loading
    }
}


