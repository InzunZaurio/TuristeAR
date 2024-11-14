package com.example.turistear

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
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
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.InputStreamReader

class CameraActivity : AppCompatActivity() {

    private lateinit var arFragment: ArFragment
    private lateinit var bottomSheetDialog: BottomSheetDialog
    private val CAMERA_PERMISSION_REQUEST_CODE = 1001
    private val TAG = "CameraActivity"

    private var currentDialogIndex = 0
    private lateinit var dialogs: List<String>
    private var assistantPlaced = false
    private var museumName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        arFragment = supportFragmentManager.findFragmentById(R.id.arFragment) as ArFragment
        museumName = intent.getStringExtra("museoNombre")

        val btnBackToMap = findViewById<Button>(R.id.btnBackToMap)
        val btnShowDialog = findViewById<Button>(R.id.btnShowDialog)

        btnBackToMap.setOnClickListener {
            finish() // Regresa al mapa
        }

        btnShowDialog.setOnClickListener {
            showBottomSheetDialog() // Mostrar los diálogos nuevamente
        }

        // Verificar permisos de cámara
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST_CODE)
        } else {
            initializeAR()
        }

        // Inicializar el BottomSheet
        setupBottomSheet()

        // Cargar los diálogos desde el archivo JSON
        loadDialogsFromJSON()
    }

    private fun initializeAR() {
        arFragment.arSceneView.scene.addOnUpdateListener { frameTime ->
            // Detectar la primera superficie y colocar el asistente una sola vez
            if (!assistantPlaced) {
                val hitResult = arFragment.arSceneView.arFrame?.hitTestCenter()
                if (hitResult != null) {
                    placeAssistant(hitResult)
                    assistantPlaced = true
                    assistantPlaced = true
                }
            }
        }
    }

    private fun setupBottomSheet() {
        bottomSheetDialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_dialog, null)
        bottomSheetDialog.setContentView(view)

        val dialogTextView = view.findViewById<TextView>(R.id.dialogText)
        val btnNext = view.findViewById<Button>(R.id.btnNext)

        btnNext.setOnClickListener {
            currentDialogIndex++
            if (currentDialogIndex < dialogs.size) {
                dialogTextView.text = dialogs[currentDialogIndex]
            } else {
                bottomSheetDialog.dismiss()
            }
        }
    }

    private fun placeAssistant(hitResult: HitResult) {
        ViewRenderable.builder()
            .setView(this, R.layout.assistant_view)
            .build()
            .thenAccept { renderable ->
                val anchor = hitResult.createAnchor()
                val anchorNode = AnchorNode(anchor)
                anchorNode.setParent(arFragment.arSceneView.scene)

                // Crear un nodo que contendrá el renderable
                val node = com.google.ar.sceneform.Node()
                node.setParent(anchorNode)
                node.renderable = renderable

                // Mostrar el BottomSheet con los diálogos
                showBottomSheetDialog()

            }
            .exceptionally { throwable ->
                Log.e(TAG, "Error al crear el renderable", throwable)
                null
            }
    }

    private fun showBottomSheetDialog() {
        if (::dialogs.isInitialized && dialogs.isNotEmpty()) {
            currentDialogIndex = 0 // Reiniciar el índice de diálogos
            val dialogTextView = bottomSheetDialog.findViewById<TextView>(R.id.dialogText)
            dialogTextView?.text = dialogs[currentDialogIndex]
            bottomSheetDialog.show()
        }
    }

    private fun loadDialogsFromJSON() {
        val inputStream = assets.open("puntos_interes.json")
        val reader = InputStreamReader(inputStream)
        val pointType = object : TypeToken<List<PointOfInterest>>() {}.type
        val pointsOfInterest: List<PointOfInterest> = Gson().fromJson(reader, pointType)

        // Buscar el museo por nombre
        val museo = pointsOfInterest.find { it.nombre == museumName }

        // Obtener los diálogos del museo o usar un valor por defecto
        dialogs = museo?.dialogos ?: listOf("¡Bienvenido!", "Esto es un museo...", "Gracias por tu visita")
    }


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

