package com.example.turistear

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.HitResult
import com.google.ar.core.Session
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.rendering.ViewRenderable
import com.google.ar.sceneform.ux.ArFragment
import java.util.concurrent.CompletableFuture

class CameraActivity : AppCompatActivity() {

    private lateinit var arFragment: ArFragment
    private val TAG = "CameraActivity"
    private var session: Session? = null
    private val CAMERA_PERMISSION_REQUEST_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        arFragment = supportFragmentManager.findFragmentById(R.id.arFragment) as ArFragment

        // Verificar permisos de cámara
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST_CODE)
        } else {
            initializeAR()
        }

        // Configurar para que el asistente aparezca automáticamente al iniciar la sesión
        arFragment.arSceneView.scene.addOnUpdateListener {
            if (arFragment.arSceneView.arFrame?.getUpdatedTrackables(com.google.ar.core.Plane::class.java)?.isNotEmpty() == true) {
                // Solo agregar una vez el asistente
                if (!::assistantPlaced.isInitialized) {
                    placeAssistant()
                }
            }
        }
    }

    private lateinit var assistantPlaced: AnchorNode

    private fun initializeAR() {
        if (!isARCoreSupportedAndUpToDate()) {
            Toast.makeText(this, "ARCore no está disponible", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun isARCoreSupportedAndUpToDate(): Boolean {
        val availability = com.google.ar.core.ArCoreApk.getInstance().checkAvailability(this)
        return availability.isSupported
    }

    private fun placeAssistant() {
        ViewRenderable.builder()
            .setView(this, R.layout.assistant_view)
            .build()
            .thenAccept { renderable ->
                // Crear un Anchor en la primera superficie detectada
                val hitResult = arFragment.arSceneView.arFrame?.hitTestCenter()
                val anchor = hitResult?.createAnchor()
                if (anchor != null) {
                    assistantPlaced = AnchorNode(anchor)
                    assistantPlaced.setParent(arFragment.arSceneView.scene)

                    // Crear un nodo que contendrá el renderable
                    val node = com.google.ar.sceneform.Node()
                    node.setParent(assistantPlaced)
                    node.renderable = renderable
                    node.localPosition = com.google.ar.sceneform.math.Vector3(0f, 0f, 0f) // Ajustar la altura si es necesario

                    // Aquí puedes añadir lógica para que el asistente comience a hablar automáticamente
                    startAssistantDialog()
                }
            }
            .exceptionally { throwable ->
                Log.e(TAG, "Error al crear el renderable", throwable)
                null
            }
    }

    private fun startAssistantDialog() {
        Toast.makeText(this, "¡Hola! Bienvenido al museo", Toast.LENGTH_LONG).show()
    }

    // Método para obtener la posición central de la pantalla
    private fun com.google.ar.core.Frame.hitTestCenter(): HitResult? {
        val centerX = arFragment.arSceneView.width / 2f
        val centerY = arFragment.arSceneView.height / 2f
        val hits = hitTest(centerX, centerY)
        return hits.firstOrNull()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            initializeAR()
        }
    }
}
