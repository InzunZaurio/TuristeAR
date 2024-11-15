package com.example.turistear

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.example.turistear.R
import com.google.android.gms.location.*
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.*
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.IOException
import java.io.InputStreamReader

class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var mapView: MapView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val REQUEST_LOCATION_PERMISSION = 1001
    private lateinit var nearbyButton: FloatingActionButton
    private lateinit var pointsOfInterest: List<PointOfInterest>
    private lateinit var centerMapButton: FloatingActionButton
    private lateinit var cancelRouteButton: FloatingActionButton
    private var currentRoute: Polyline? = null
    private var userMarker: Marker? = null
    private lateinit var locationCallback: LocationCallback
    private var isRequestingLocationUpdates = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Configuración de OSMDroid
        Configuration.getInstance().load(this, applicationContext.getSharedPreferences("osmdroid", MODE_PRIVATE))

        setContentView(R.layout.activity_main)

        pointsOfInterest = loadPointsOfInterest()
        nearbyButton = findViewById(R.id.btnNearbyPoints)
        nearbyButton.setOnClickListener {
            showNearbyPoints()
        }

        centerMapButton = findViewById(R.id.btnCenterMap)
        centerMapButton.setOnClickListener {
            centerMapOnUserLocation()
        }

        cancelRouteButton = findViewById(R.id.btnCancelRoute)
        cancelRouteButton.setOnClickListener {
            removeRouteFromMap()
        }

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_menu) // Agregar icono de menú
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Configurar DrawerLayout
        drawerLayout = findViewById(R.id.drawer_layout)
        val navigationView: NavigationView = findViewById(R.id.navigation_view)

        // Listener para abrir el drawer al hacer clic en el ícono de menú
        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_reset_location -> {
                    resetLocation()
                }
                R.id.nav_about_us -> {
                    showAboutUs()
                }
                R.id.nav_show_tutorial -> {
                    showAssistantTutorial()
                }
            }
            drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START)
            true
        }

        // Inicializar el MapView
        mapView = findViewById(R.id.map)
        mapView.setMultiTouchControls(true)

        // Inicializar el cliente de ubicación
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Solicitar permisos de ubicación
        requestLocationPermission()

        if (isFirstLaunch()) {
            showAssistantTutorial()
            setFirstLaunchComplete()
        }
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
            // Si el permiso ya fue concedido, inicia las actualizaciones de ubicación
            startLocationUpdates()
        }
    }

    // Método para iniciar las actualizaciones de ubicación
    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            2000 // Tiempo entre actualizaciones en milisegundos
        ).build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    updateLocationOnMap(location)
                }
            }
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, mainLooper)
            isRequestingLocationUpdates = true
        }
    }

    // Método para actualizar la ubicación del usuario en el mapa
    private fun updateLocationOnMap(location: Location) {
        val userLocation = GeoPoint(location.latitude, location.longitude)
        //userMarker?.position = userLocation
        //mapView.controller.animateTo(userLocation)

        if (userMarker == null) {
            // Crear el marcador del usuario si no existe
            userMarker = Marker(mapView)
            userMarker?.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            userMarker?.title = "Tú estás aquí"
            mapView.overlays.add(userMarker)

            // Solo centramos el mapa la primera vez
            mapView.controller.setCenter(userLocation)
            mapView.controller.setZoom(15.0)
            addPointsOfInterestMarkers()
        }

        currentRoute?.let {
            val remainingRoute = updateRouteBasedOnUserLocation(userLocation)
            if (remainingRoute.isNotEmpty()) {
                currentRoute?.setPoints(remainingRoute)
            } else {
                // Si el usuario ha alcanzado el destino, elimina la ruta
                removeRouteFromMap()
                Toast.makeText(this, "¡Has llegado a tu destino!", Toast.LENGTH_SHORT).show()
            }
        }

        // Actualizar la posición del marcador sin centrar el mapa
        userMarker?.position = userLocation

        // Redibujar el mapa para reflejar el movimiento del marcador
        mapView.invalidate()
    }

    private fun updateRouteBasedOnUserLocation(userLocation: GeoPoint): List<GeoPoint> {
        val remainingPoints = currentRoute?.points ?: return emptyList()

        // Verifica los puntos de la ruta y elimina los que ya se han recorrido
        val newRoute = mutableListOf<GeoPoint>()
        for (point in remainingPoints) {
            val distance = userLocation.distanceToAsDouble(point)
            if (distance > 10) { // Umbral para determinar si el usuario está lo suficientemente cerca del punto
                newRoute.add(point)
            }
        }
        return newRoute
    }

    // Método para agregar los marcadores de los puntos de interés al mapa
    private fun addPointsOfInterestMarkers() {
        for (point in pointsOfInterest) {
            val pointLocation = GeoPoint(point.latitud, point.longitud)
            val marker = Marker(mapView)
            marker.position = pointLocation
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            marker.title = point.nombre
            marker.snippet = point.descripcion // Añadir una breve descripción
            marker.setOnMarkerClickListener { _, _ ->
                showMuseumDetails(point)
                true
            }
            mapView.overlays.add(marker)
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

    private fun showMuseumDetails(museo: PointOfInterest) {
        // Crear el BottomSheetDialog
        val bottomSheetDialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_museo, null)
        bottomSheetDialog.setContentView(view)

        // Configurar la información del museo en el Bottom Sheet
        val nombreTextView = view.findViewById<TextView>(R.id.museoNombre)
        val descripcionTextView = view.findViewById<TextView>(R.id.museoDescripcion)
        nombreTextView.text = museo.nombre
        descripcionTextView.text = museo.descripcion

        // Configurar el botón para la ruta
        val btnRuta = view.findViewById<Button>(R.id.btnRuta)
        btnRuta.setOnClickListener {
            // Lógica para mostrar la ruta al museo
            showRouteToMuseum(museo)
            bottomSheetDialog.dismiss()
        }

        // Configurar el botón para activar la RA
        val btnAR = view.findViewById<Button>(R.id.btnAR)
        btnAR.setOnClickListener {
            val intent = Intent(this, CameraActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            intent.putExtra("museoNombre", museo.nombre)
            startActivity(intent)
            bottomSheetDialog.dismiss()
        }

        // Mostrar el Bottom Sheet
        bottomSheetDialog.show()
    }

    private fun showRouteToMuseum(museo: PointOfInterest) {
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

        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                val userLat = location.latitude
                val userLon = location.longitude
                val museumLat = museo.latitud
                val museumLon = museo.longitud
                getRouteFromOSRM(userLat, userLon, museumLat, museumLon)
            }
        }
    }

    private fun showNearbyPoints() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                val userLocation = GeoPoint(location.latitude, location.longitude)
                val nearbyPoints = pointsOfInterest.filter { point ->
                    val pointLocation = GeoPoint(point.latitud, point.longitud)
                    userLocation.distanceToAsDouble(pointLocation) <= 1000 // Radio de 1 km
                }

                // Mostrar mensaje con los puntos cercanos
                if (nearbyPoints.isNotEmpty()) {
                    val nearbyNames = nearbyPoints.joinToString(separator = ", ") { it.nombre }
                    showAlertDialog("Estás cerca de estos puntos: $nearbyNames")
                } else {
                    showAlertDialog("No estás cerca de ningún punto de interés.")
                }
            }
        }
    }

    private fun showAlertDialog(message: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Puntos cercanos")
        builder.setMessage(message)
        builder.setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
        builder.create().show()
    }

    private fun centerMapOnUserLocation() {
        if (userMarker != null) {
            mapView.controller.animateTo(userMarker?.position) // Centrar y animar hacia la posición
            mapView.controller.setZoom(15.0) // Opcional: ajustar el zoom
        } else {
            Toast.makeText(this, "Ubicación del usuario no disponible", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getRouteFromOSRM(startLat: Double, startLon: Double, destLat: Double, destLon: Double) {
        val url = "https://router.project-osrm.org/route/v1/driving/$startLon,$startLat;$destLon,$destLat?overview=full&geometries=geojson"
        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Error al obtener la ruta", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val jsonResponse = response.body?.string()
                    if (jsonResponse != null) {
                        drawRouteOnMap(jsonResponse)
                    }
                }
            }
        })
    }

    private fun drawRouteOnMap(jsonResponse: String) {
        val geoJson = JSONObject(jsonResponse).getJSONArray("routes")
            .getJSONObject(0).getJSONObject("geometry").getJSONArray("coordinates")

        val geoPoints = ArrayList<GeoPoint>()
        for (i in 0 until geoJson.length()) {
            val point = geoJson.getJSONArray(i)
            val lon = point.getDouble(0)
            val lat = point.getDouble(1)
            geoPoints.add(GeoPoint(lat, lon))
        }

        runOnUiThread {
            // Crear y mostrar la ruta en el mapa
            currentRoute = Polyline().apply { setPoints(geoPoints) }
            mapView.overlayManager.add(currentRoute)
            mapView.invalidate()

            // Hacer visible el botón de cancelar ruta
            cancelRouteButton.visibility = View.VISIBLE
        }
    }

    private fun removeRouteFromMap() {
        currentRoute?.let {
            mapView.overlayManager.remove(it) // Eliminar la ruta del mapa
            currentRoute = null
            mapView.invalidate() // Refrescar el mapa
        }

        // Ocultar el botón de cancelar ruta
        cancelRouteButton.visibility = View.GONE
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Abrir el DrawerLayout cuando se hace clic en el icono de menú
        return when (item.itemId) {
            android.R.id.home -> {
                drawerLayout.openDrawer(androidx.core.view.GravityCompat.START)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun resetLocation() {
        Toast.makeText(this, "Ubicación reiniciada", Toast.LENGTH_SHORT).show()
    }

    private fun showAboutUs() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Sobre Nosotros")
        builder.setMessage("Esta aplicación fue desarrollada para ...")
        builder.setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
        builder.create().show()
    }

    // Respuesta a la solicitud de permisos
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permiso concedido
                startLocationUpdates()
            } else {
                // Permiso denegado; puedes manejar este caso si es necesario
                Toast.makeText(this, "Permiso de ubicación denegado", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun isFirstLaunch(): Boolean {
        val sharedPref = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        return sharedPref.getBoolean("isFirstLaunch", true)
    }

    private fun setFirstLaunchComplete() {
        val sharedPref = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        with(sharedPref.edit()) {
            putBoolean("isFirstLaunch", false)
            apply()
        }
    }

    private fun showAssistantTutorial() {
        val bottomSheetDialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_assistant, null)
        bottomSheetDialog.setContentView(view)

        val dialogTextView = view.findViewById<TextView>(R.id.dialogText)
        val assistantImageView = view.findViewById<ImageView>(R.id.assistantImage)

        val tutorialMessages = listOf(
            "¡Hola! Bienvenido a TuristeAR.",
            "Te ayudaré a encontrar los mejores museos de la CDMX.",
            "Toca la pantalla para continuar..."
        )
        var currentMessageIndex = 0
        dialogTextView.text = tutorialMessages[currentMessageIndex]

        view.setOnClickListener {
            currentMessageIndex++
            if (currentMessageIndex < tutorialMessages.size) {
                dialogTextView.text = tutorialMessages[currentMessageIndex]
            } else {
                bottomSheetDialog.dismiss()
            }
        }

        bottomSheetDialog.show()
    }


    // Métodos del ciclo de vida para MapView y actualizaciones de ubicación
    override fun onResume() {
        super.onResume()
        mapView.onResume()
        if (isRequestingLocationUpdates) {
            startLocationUpdates()
        }
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

}
