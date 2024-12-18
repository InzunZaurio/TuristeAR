package com.example.turistear

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class PrivacyPolicyActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_privacy_policy)

        val btnAccept: Button = findViewById(R.id.btnAcceptPrivacy)
        btnAccept.setOnClickListener {
            // Guardar aceptación en SharedPreferences
            val sharedPreferences = getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
            sharedPreferences.edit().putBoolean("AcceptedPrivacyPolicy", true).apply()

            // Redirigir a MainActivity
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}
