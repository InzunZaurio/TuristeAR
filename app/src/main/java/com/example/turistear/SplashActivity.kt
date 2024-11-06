package com.example.turistear

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.content.Intent
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.example.turistear.R
import jp.wasabeef.glide.transformations.BlurTransformation

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val startButton: Button = findViewById(R.id.startButton)
        val backgroundGif = findViewById<ImageView>(R.id.backgroundGif)
        Glide.with(this)
            .load(R.drawable.ar) // Reemplaza `tu_gif` con el nombre del archivo GIF en drawable
            .transform(CenterCrop(), BlurTransformation(5, 3)) // Nivel de desenfoque
            .into(backgroundGif)

        startButton.setOnClickListener{
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }
    }
}