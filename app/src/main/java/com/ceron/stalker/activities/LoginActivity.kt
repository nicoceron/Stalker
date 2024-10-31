package com.ceron.stalker.activities

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.ceron.stalker.R
import com.ceron.stalker.databinding.ActivityLoginBinding
import com.ceron.stalker.utils.Alerts
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private var alerts = Alerts(this)

    private var auth: FirebaseAuth = Firebase.auth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.loginButton.setOnClickListener {
            login()
        }
        binding.signupButton.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
        binding.forgotButton.setOnClickListener {
            recoverPassword()
        }
    }

    private fun validateFields(): Boolean {
        // Validate email
        if (binding.loginEmail.editText?.text.toString()
                .isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(binding.loginEmail.editText?.text.toString())
                .matches()
        ) {
            binding.loginEmail.error = getString(R.string.mail_error_label)
            return false
        } else binding.loginEmail.isErrorEnabled = false
        // Validate password
        if (binding.loginPass.editText?.text.toString().isEmpty()) {
            binding.loginPass.error = getString(R.string.error_pass_label);
            return false
        } else binding.loginPass.isErrorEnabled = false;
        return true
    }

    private fun login() {
        if (validateFields()) {
            disableFields()
            auth.signInWithEmailAndPassword(
                binding.loginEmail.editText?.text.toString(),
                binding.loginPass.editText?.text.toString()
            ).addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Sign in success, update UI with the signed-in user's information
                    startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                } else {
                    // If sign in fails, display a message to the user.
                    task.exception?.localizedMessage?.let {
                        alerts.indefiniteSnackbar(
                            binding.root, it
                        )
                    }
                }
                enableFields()
            }
        }
    }

    private fun recoverPassword() {
        // Validate email
        if (binding.loginEmail.editText?.text.toString()
                .isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(binding.loginEmail.editText?.text.toString())
                .matches()
        ) {
            binding.loginEmail.error = getString(R.string.mail_error_label)
            return
        } else binding.loginEmail.isErrorEnabled = false
        auth.sendPasswordResetEmail(
            binding.loginEmail.editText?.text.toString()
        ).addOnCompleteListener(this) { task ->
            var msg: String = ""
            if (task.isSuccessful) {
                msg = "Email sent, please check your inbox."
            } else {
                task.exception?.localizedMessage?.let {
                    msg = it
                }
            }
            alerts.indefiniteSnackbar(
                binding.root, msg
            )
        }
    }

    private fun disableFields() {
        binding.loginEmail.isEnabled = false
        binding.loginPass.isEnabled = false
        binding.loginButton.isEnabled = false
        binding.signupButton.isEnabled = false
        binding.forgotButton.isEnabled = false
    }

    private fun enableFields() {
        binding.loginEmail.isEnabled = true
        binding.loginPass.isEnabled = true
        binding.loginButton.isEnabled = true
        binding.signupButton.isEnabled = true
        binding.forgotButton.isEnabled = true
    }

}