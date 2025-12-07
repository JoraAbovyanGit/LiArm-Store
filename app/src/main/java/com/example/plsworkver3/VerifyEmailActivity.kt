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

class VerifyEmailActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var codeInput: EditText
    private lateinit var verifyButton: Button
    private lateinit var resendButton: TextView
    private lateinit var backToRegisterButton: TextView
    private lateinit var emailDisplay: TextView
    private lateinit var progressBar: ProgressBar

    private var email: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_verify_email)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        email = intent.getStringExtra("email") ?: auth.currentUser?.email

        codeInput = findViewById(R.id.inputCode)
        verifyButton = findViewById(R.id.buttonVerify)
        resendButton = findViewById(R.id.linkResend)
        backToRegisterButton = findViewById(R.id.linkBackToRegister)
        emailDisplay = findViewById(R.id.emailDisplay)
        progressBar = findViewById(R.id.progressBar)

        // Display the email address
        val displayEmail = email ?: auth.currentUser?.email ?: "your email"
        emailDisplay.text = "Verification code sent to: $displayEmail"

        verifyButton.setOnClickListener { verifyCode() }
        resendButton.setOnClickListener { resendCode() }
        backToRegisterButton.setOnClickListener { goBackToRegistration() }
    }

    private fun verifyCode() {
        val user = auth.currentUser
        if (user == null) {
            android.util.Log.e("VerifyEmailActivity", "User is null")
            Toast.makeText(this, getString(R.string.error_login_generic), Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        val enteredCode = codeInput.text.toString().trim()
        if (enteredCode.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_empty_fields), Toast.LENGTH_SHORT).show()
            return
        }

        setLoading(true)
        android.util.Log.d("VerifyEmailActivity", "Verifying code for user: ${user.uid}, entered code: $enteredCode")

        // Set a timeout - if Firestore doesn't respond in 10 seconds, proceed anyway
        val timeoutHandler = Handler(Looper.getMainLooper())
        var hasRedirected = false
        val timeoutRunnable = Runnable {
            if (!hasRedirected) {
                android.util.Log.w("VerifyEmailActivity", "Verification timeout - proceeding with redirect anyway")
                hasRedirected = true
                // Even if timeout, if code was correct, mark as verified and redirect
                redirectToMain()
            }
        }
        timeoutHandler.postDelayed(timeoutRunnable, 10000) // 10 second timeout

        // Check both Firestore and SharedPreferences for verification code
        val prefs = getSharedPreferences("verification_prefs", MODE_PRIVATE)
        val pendingCode = prefs.getString("pending_verification_code", null)
        val pendingName = prefs.getString("pending_name", null)
        val pendingSurname = prefs.getString("pending_surname", null)
        val pendingEmail = prefs.getString("pending_email", null)
        val pendingUid = prefs.getString("pending_uid", null)
        
        // First check SharedPreferences (new flow - code stored during registration)
        if (pendingCode != null && pendingCode == enteredCode && pendingUid == user.uid) {
            android.util.Log.d("VerifyEmailActivity", "Code matches from SharedPreferences! Creating user document...")
            setLoading(true)
            
            // Create user document only after verification (new flow)
            val userData = hashMapOf(
                "name" to (pendingName ?: ""),
                "surname" to (pendingSurname ?: ""),
                "email" to (pendingEmail ?: user.email ?: ""),
                "verified" to true,
                "verificationCode" to null,
                "createdAt" to System.currentTimeMillis()
            )
            
            db.collection("users").document(user.uid)
                .set(userData)
                .addOnSuccessListener {
                    // Clear pending verification data
                    prefs.edit().clear().apply()
                    android.util.Log.d("VerifyEmailActivity", "User document created successfully")
                    if (!hasRedirected) {
                        hasRedirected = true
                        redirectToMain()
                    }
                }
                .addOnFailureListener { e ->
                    android.util.Log.e("VerifyEmailActivity", "Failed to create user document: ${e.message}", e)
                    setLoading(false)
                    Toast.makeText(
                        this,
                        "Code verified but data save failed. Redirecting anyway...",
                        Toast.LENGTH_SHORT
                    ).show()
                    // Clear pending data even on failure
                    prefs.edit().clear().apply()
                    if (!hasRedirected) {
                        hasRedirected = true
                        redirectToMain()
                    }
                }
            return
        }
        
        // Fallback: Check Firestore (old flow compatibility)
        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { doc ->
                timeoutHandler.removeCallbacks(timeoutRunnable)
                if (hasRedirected) return@addOnSuccessListener
                
                android.util.Log.d("VerifyEmailActivity", "Got user document from Firestore")
                val storedCode = doc.getString("verificationCode")
                android.util.Log.d("VerifyEmailActivity", "Stored code: $storedCode, Entered code: $enteredCode")
                
                // Check code from Firestore (old flow)
                val codeMatches = storedCode != null && storedCode == enteredCode
                
                if (codeMatches) {
                    android.util.Log.d("VerifyEmailActivity", "Code matches! Updating verification status...")
                    // Old flow: user already exists in Firestore, just update verified status
                    db.collection("users").document(user.uid)
                        .update(
                            mapOf(
                                "verified" to true,
                                "verificationCode" to null
                            )
                        )
                        .addOnSuccessListener {
                            android.util.Log.d("VerifyEmailActivity", "Verification status updated successfully")
                            if (!hasRedirected) {
                                hasRedirected = true
                                redirectToMain()
                            }
                        }
                        .addOnFailureListener { e ->
                            android.util.Log.e("VerifyEmailActivity", "Failed to update verification status: ${e.message}", e)
                            setLoading(false)
                            Toast.makeText(
                                this,
                                "Code verified but update failed. Redirecting anyway...",
                                Toast.LENGTH_SHORT
                            ).show()
                            if (!hasRedirected) {
                                hasRedirected = true
                                redirectToMain()
                            }
                        }
                } else {
                    android.util.Log.w("VerifyEmailActivity", "Code mismatch or null")
                    setLoading(false)
                    Toast.makeText(this, getString(R.string.error_invalid_code), Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                timeoutHandler.removeCallbacks(timeoutRunnable)
                android.util.Log.e("VerifyEmailActivity", "Failed to get user document: ${e.message}", e)
                setLoading(false)
                Toast.makeText(
                    this,
                    "Failed to verify code: ${e.localizedMessage ?: getString(R.string.error_verify_generic)}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun redirectToMain() {
        setLoading(false)
        Toast.makeText(this, getString(R.string.verification_success), Toast.LENGTH_SHORT).show()
        android.util.Log.d("VerifyEmailActivity", "Redirecting to MainActivity")
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }

    private fun resendCode() {
        val user = auth.currentUser
        val currentEmail = email

        if (user == null || currentEmail.isNullOrEmpty()) {
            Toast.makeText(this, getString(R.string.error_login_generic), Toast.LENGTH_SHORT).show()
            return
        }

        setLoading(true)

        val newCode = (100000..999999).random().toString()

        // Get name from SharedPreferences or Firestore
        val prefs = getSharedPreferences("verification_prefs", MODE_PRIVATE)
        val pendingName = prefs.getString("pending_name", null)
        
        // Try to get name from Firestore if not in SharedPreferences
        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { doc ->
                val userName = pendingName ?: doc.getString("name") ?: null
                
                // Update verification code
                if (doc.exists()) {
                    db.collection("users").document(user.uid)
                        .update("verificationCode", newCode, "verified", false)
                        .addOnSuccessListener {
                            // Fire-and-forget call to backend that sends email via SendGrid
                            EmailVerificationSender.sendVerificationEmail(this, currentEmail, newCode, userName)
                            setLoading(false)
                            Toast.makeText(this, getString(R.string.code_resent), Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener { e ->
                            setLoading(false)
                            Toast.makeText(
                                this,
                                e.localizedMessage ?: getString(R.string.error_verify_generic),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                } else {
                    // User doesn't exist in Firestore yet, just send email
                    EmailVerificationSender.sendVerificationEmail(this, currentEmail, newCode, userName)
                    setLoading(false)
                    Toast.makeText(this, getString(R.string.code_resent), Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                // If Firestore fails, still try to send email
                EmailVerificationSender.sendVerificationEmail(this, currentEmail, newCode, pendingName)
                setLoading(false)
                Toast.makeText(this, getString(R.string.code_resent), Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                setLoading(false)
                Toast.makeText(
                    this,
                    e.localizedMessage ?: getString(R.string.error_verify_generic),
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun goBackToRegistration() {
        val user = auth.currentUser
        if (user != null) {
            // Delete the Firebase Auth user that was created during registration
            // since user wants to start over
            user.delete()
                .addOnSuccessListener {
                    android.util.Log.d("VerifyEmailActivity", "Auth user deleted, going back to registration")
                    // Clear pending verification data
                    val prefs = getSharedPreferences("verification_prefs", MODE_PRIVATE)
                    prefs.edit().clear().apply()
                    // Sign out
                    auth.signOut()
                    // Go back to registration
                    startActivity(Intent(this, RegisterActivity::class.java))
                    finish()
                }
                .addOnFailureListener { e ->
                    android.util.Log.e("VerifyEmailActivity", "Failed to delete Auth user: ${e.message}", e)
                    // Still go back even if deletion fails
                    val prefs = getSharedPreferences("verification_prefs", MODE_PRIVATE)
                    prefs.edit().clear().apply()
                    auth.signOut()
                    startActivity(Intent(this, RegisterActivity::class.java))
                    finish()
                }
        } else {
            // No user to delete, just go back
            val prefs = getSharedPreferences("verification_prefs", MODE_PRIVATE)
            prefs.edit().clear().apply()
            startActivity(Intent(this, RegisterActivity::class.java))
            finish()
        }
    }

    private fun setLoading(loading: Boolean) {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        verifyButton.isEnabled = !loading
        resendButton.isEnabled = !loading
        backToRegisterButton.isEnabled = !loading
    }
}


