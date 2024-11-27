package com.example.turistear

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.content.Intent
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import jp.wasabeef.glide.transformations.BlurTransformation

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Configurar el fondo con Glide
        val backgroundGif = findViewById<ImageView>(R.id.backgroundGif)
        Glide.with(this)
            .load(R.drawable.ar)
            .transform(CenterCrop(), BlurTransformation(5, 3)) // Nivel de desenfoque
            .into(backgroundGif)

        // Botones para seleccionar idioma
        val btnEnglish = findViewById<Button>(R.id.btnEnglish)
        val btnSpanish = findViewById<Button>(R.id.btnSpanish)

        // Configurar acciones para los botones
        btnEnglish.setOnClickListener {
            saveLanguagePreference("en")
            navigateToMainActivity()
        }

        btnSpanish.setOnClickListener {
            saveLanguagePreference("es")
            navigateToMainActivity()
        }
    }

    private fun saveLanguagePreference(language: String) {
        val sharedPreferences = getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putString("language", language)
        editor.apply()
    }

    private fun navigateToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out) // Transición suave
        finish()
    }
}
