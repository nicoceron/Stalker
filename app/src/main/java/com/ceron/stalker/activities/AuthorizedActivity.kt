package com.ceron.stalker.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.ceron.stalker.R
import com.ceron.stalker.models.UserProfile
import com.ceron.stalker.utils.Alerts
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.database.database

open class AuthorizedActivity : AppCompatActivity() {
    private var auth: FirebaseAuth = Firebase.auth
    var currentUser = auth.currentUser
    protected lateinit var user: UserProfile
    protected val database = Firebase.database
    protected val refData = database.getReference("users/${currentUser?.uid}")

    protected var alerts = Alerts(this)

    override fun onResume() {
        super.onResume()
        if (auth.currentUser == null) {
            logout()
        }
        // Load user data from the database
        refData.get().addOnSuccessListener { data ->
            user = data.getValue(UserProfile::class.java)!!
        }
    }

    protected fun logout() {
        auth.signOut()
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }
}