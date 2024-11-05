package com.example.turistear

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.InputStreamReader

class MainActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val REQUEST_LOCATION_PERMISSION = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Configuración de OSMDroid
        Configuration.getInstance().load(this, applicationContext.getSharedPreferences("osmdroid", MODE_PRIVATE))

        // Inicializar el MapView
        mapView = findViewById(R.id.map)
        mapView.setMultiTouchControls(true)

        // Inicializar el cliente de ubicación
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Solicitar permisos de ubicación
        requestLocationPermission()
    }

    // Función para solicitar permisos de ubicación
    private fun requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                REQUEST_LOCATION_PERMISSION
            )
        } else {
            // Si el permiso ya fue concedido, inicializa la ubicación del usuario
            initializeUserLocation()
        }
    }

    // Método para cargar puntos de interés desde el archivo JSON
    private fun loadPointsOfInterest(): List<PointOfInterest> {
        val inputStream = assets.open("puntos_interes.json")
        val reader = InputStreamReader(inputStream)
        val pointType = object : TypeToken<List<PointOfInterest>>() {}.type
        return Gson().fromJson(reader, pointType)
    }

    // Método para verificar si el usuario está cerca de algún punto de interés
    private fun checkNearbyPoints(userLocation: GeoPoint, points: List<PointOfInterest>) {
        for (point in points) {
            val pointLocation = GeoPoint(point.latitud, point.longitud)
            val distance = userLocation.distanceToAsDouble(pointLocation) // distancia en metros

            if (distance <= 1000) { // Verifica si está dentro de un radio de 1 km
                enableInteraction(point)
            }
        }
    }

    // Función que se llama cuando el usuario está cerca de un punto de interés
    private fun enableInteraction(point: PointOfInterest) {
        // Aquí puedes mostrar un mensaje, diálogo o activar la realidad aumentada
        Toast.makeText(this, "Estás cerca de ${point.nombre}", Toast.LENGTH_SHORT).show()
    }

    // Método para inicializar la ubicación del usuario y verificar puntos cercanos
    private fun initializeUserLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        // Obtener la última ubicación del usuario
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                val userLocation = GeoPoint(location.latitude, location.longitude)

                // Centrar el mapa en la ubicación del usuario y establecer el nivel de zoom
                mapView.controller.setZoom(15.0)
                mapView.controller.setCenter(userLocation)

                // Añadir un marcador en la ubicación del usuario
                val userMarker = Marker(mapView)
                userMarker.position = userLocation
                userMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                userMarker.title = "Tú estás aquí"
                mapView.overlays.add(userMarker)

                // Cargar puntos de interés y agregarlos como marcadores en el mapa
                val points = loadPointsOfInterest()
                for (point in points) {
                    val pointLocation = GeoPoint(point.latitud, point.longitud)
                    val marker = Marker(mapView)
                    marker.position = pointLocation
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    marker.title = point.nombre
                    marker.snippet = point.descripcion // Añadir una breve descripción
                    mapView.overlays.add(marker)
                }

                // Verificar cercanía de puntos
                checkNearbyPoints(userLocation, points)
            }
        }
    }


    // Respuesta a la solicitud de permisos
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permiso concedido
                initializeUserLocation()
            } else {
                // Permiso denegado; puedes manejar este caso si es necesario
            }
        }
    }

    // Métodos del ciclo de vida para MapView
    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }
}
