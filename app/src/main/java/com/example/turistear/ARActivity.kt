package com.example.turistear

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.ar.core.*
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.ux.ArFragment
import com.google.ar.sceneform.ux.TransformableNode

class ARActivity : AppCompatActivity() {
    private lateinit var arFragment: ArFragment
    private val CAMERA_PERMISSION_REQUEST_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ar)

        // Verificar el permiso de la cámara antes de continuar
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST_CODE)
        } else {
            initializeAR()
        }
    }

    private fun initializeAR() {
        arFragment = supportFragmentManager.findFragmentById(R.id.arFragment) as ArFragment

        // Configurar el listener para detectar toques en la pantalla y añadir el PNG
        arFragment.setOnTapArPlaneListener { hitResult, _, _ ->
            Log.d("ARActivity", "Surface touched")
            val anchor = hitResult.createAnchor()
            val anchorNode = AnchorNode(anchor)
            anchorNode.setParent(arFragment.arSceneView.scene)

            // Llamar al método para crear y mostrar el renderable
            createImageRenderable(anchorNode)
        }
    }

    private fun createImageRenderable(anchorNode: AnchorNode) {
        Log.d("ARActivity", "Creating renderable")
        com.google.ar.sceneform.rendering.ViewRenderable.builder()
            .setView(this, R.layout.assistant_view)
            .build()
            .thenAccept { renderable ->
                Log.d("ARActivity", "Renderable created")
                val node = TransformableNode(arFragment.transformationSystem)
                node.renderable = renderable
                node.setParent(anchorNode)
                node.select()
            }
            .exceptionally { throwable ->
                Log.e("ARActivity", "Error creating renderable: ${throwable.message}")
                Toast.makeText(this, "Error al cargar el PNG: ${throwable.message}", Toast.LENGTH_SHORT).show()
                null
            }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initializeAR()
            } else {
                Toast.makeText(this, "Permiso de cámara denegado", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}
