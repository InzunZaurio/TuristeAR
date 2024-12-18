package com.example.turistear

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.*
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetBehavior
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

    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>
    private lateinit var assistantImage: ImageView
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
    private var isOverlayActive = false
    private var currentBottomSheetView: View? = null
    private lateinit var bottomNavigationView: BottomNavigationView
    private var favoritePoints: MutableList<PointOfInterest> = mutableListOf()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        favoritePoints = loadFavoritesFromSharedPreferences()
        // Configuración de OSMDroid
        Configuration.getInstance().load(this, applicationContext.getSharedPreferences("osmdroid", MODE_PRIVATE))

        setContentView(R.layout.activity_main)

        // Inicializar locationCallback para evitar errores
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    updateLocationOnMap(location)
                }
            }
        }

        val sharedPreferences = getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        val language = sharedPreferences.getString("language", "es") ?: "es"

        // Cargar el archivo JSON correspondiente
        val jsonFileName = if (language == "en") "puntos_interes_en.json" else "puntos_interes.json"
        pointsOfInterest = loadPointsOfInterest(jsonFileName)

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
                R.id.nav_language_es -> {
                    changeLanguage("es")
                }
                R.id.nav_language_en -> {
                    changeLanguage("en")
                }
                R.id.mostrar_tutorial ->{
                    showTutorialBottomSheet()
                }
                R.id.nav_privacy_policy -> {
                    showPrivacyPolicy()
                    true
                }
                else -> false
            }
            drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START)
            true
        }

        val tvPrivacyPolicy = findViewById<TextView>(R.id.tvPrivacyPolicy)
        tvPrivacyPolicy.setOnClickListener {
            showPrivacyPolicy()
        }

        // Inicializar el MapView
        mapView = findViewById(R.id.map)
        mapView.setMultiTouchControls(true)

        // Inicializar el cliente de ubicación
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Solicitar permisos de ubicación
        requestLocationPermission()

        // Inicializar BottomNavigationView
        bottomNavigationView = findViewById(R.id.bottom_navigation)

        bottomNavigationView.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_home -> {
                    Toast.makeText(this, "Estás en el mapa principal", Toast.LENGTH_SHORT).show()
                    true
                }

                R.id.action_language -> {
                    Toast.makeText(this, "Esta función será agregada próximamente.", Toast.LENGTH_SHORT).show()
                    true
                }

                R.id.action_favorites -> {
                    showFavoritesBottomSheet() // Mostrar POIs favoritos
                    true
                }
                else -> false
            }
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

    private fun showPrivacyPolicy() {
        val intent = Intent(this, PrivacyPolicyActivity::class.java)
        startActivity(intent)
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
                    if (!isRequestingLocationUpdates){
                        mapView.controller.setCenter(GeoPoint(location.latitude, location.longitude))
                        mapView.controller.setZoom(15.0)
                        isRequestingLocationUpdates = true
                    }
                }
            }
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, mainLooper)

        }
    }



    // Método para actualizar la ubicación del usuario en el mapa
    private fun updateLocationOnMap(location: Location) {
        val userLocation = GeoPoint(location.latitude, location.longitude)

        if (userMarker == null) {
            // Crear el marcador del usuario si no existe
            userMarker = Marker(mapView)
            userMarker?.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            userMarker?.title = "Tú estás aquí, viajero."
            mapView.overlays.add(userMarker)

            // Solo centramos el mapa la primera vez
            mapView.controller.setCenter(userLocation)
            mapView.controller.setZoom(15.0)
            addPointsOfInterestMarkers()
            startUserMarkerAnimation()
        }

        // Actualizar la posición del marcador sin centrar el mapa
        userMarker?.position = userLocation

        // Verificar si está cerca del destino de la ruta actual
        if (currentRoute != null) {
            val destination = currentRoute?.points?.lastOrNull()
            if (destination != null && userLocation.distanceToAsDouble(destination) <= 100.0) {
                Toast.makeText(this, "Has llegado a tu destino, viajero.", Toast.LENGTH_SHORT).show()
                removeRouteFromMap()
            }
        }

        // Redibujar el mapa para reflejar el movimiento del marcador
        mapView.invalidate()
    }

    // Método para agregar los marcadores de los puntos de interés al mapa
    private fun addPointsOfInterestMarkers() {
        val handler = Handler(Looper.getMainLooper())

        for (point in pointsOfInterest) {
            val pointLocation = GeoPoint(point.latitud, point.longitud)
            val marker = Marker(mapView)
            marker.position = pointLocation
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            marker.title = point.nombre
            marker.snippet = point.descripcion // Añadir una breve descripción

            // Animación del marcador (opcional)
            val frames = listOf(
                R.drawable.frame_0,
                R.drawable.frame_1,
                R.drawable.frame_2,
                R.drawable.frame_3,
                R.drawable.frame_4,
            )
            var currentFrame = 0

            // Runnable para alternar las imágenes
            val runnable = object : Runnable {
                override fun run() {
                    marker.icon = ContextCompat.getDrawable(this@MainActivity, frames[currentFrame])
                    mapView.invalidate() // Refrescar el mapa
                    currentFrame = (currentFrame + 1) % frames.size // Ciclar entre los frames
                    handler.postDelayed(this, 100) // Cambiar frame cada 100ms
                }
            }

            // Iniciar animación del marcador
            handler.post(runnable)

            marker.setOnMarkerClickListener { _, _ ->
                showMuseumDetails(point)
                true
            }
            mapView.overlays.add(marker)
        }
    }


    // Método para cargar puntos de interés desde el archivo JSON
    private fun loadPointsOfInterest(jsonFileName: String): List<PointOfInterest> {
        val inputStream = assets.open(jsonFileName)
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
        val bottomSheetDialog = BottomSheetDialog(this, R.style.CustomBottomSheetDialog)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_museo, null)
        currentBottomSheetView = view
        updateUITexts(view)
        bottomSheetDialog.setContentView(view)

        // Configurar la información del museo en el Bottom Sheet
        val nombreTextView = view.findViewById<TextView>(R.id.museoNombre)
        val descripcionTextView = view.findViewById<TextView>(R.id.museoDescripcion)
        val horarioTextView = view.findViewById<TextView>(R.id.horarioMuseo)
        val accessTextView = view.findViewById<TextView>(R.id.accesibilidadMuseo)
        nombreTextView.text = museo.nombre
        descripcionTextView.text = museo.descripcion
        horarioTextView.text = museo.horarios
        accessTextView.text = museo.accesibilidad

        // Configurar la estrella de favoritos
        val btnFavorite = view.findViewById<ImageButton>(R.id.btnFavorite)
        updateFavoriteIcon(btnFavorite, museo)

        // Configurar el botón para la ruta
        val btnRuta = view.findViewById<Button>(R.id.btnRuta)
        btnRuta.setOnClickListener {
            // Lógica para mostrar la ruta al museo
            showRouteToMuseum(museo)
            bottomSheetDialog.dismiss()
        }

        val btnRouteMaps = view.findViewById<Button>(R.id.btnRouteMaps)
        btnRouteMaps.setOnClickListener {
            openGoogleMaps(museo.latitud, museo.longitud)
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

        val btnNoAR = view.findViewById<Button>(R.id.btnNoAR)
        btnNoAR.setOnClickListener {
            showAssistantOverlay(museo)
            bottomSheetDialog.dismiss()
        }

        btnFavorite.setOnClickListener {
            if (favoritePoints.contains(museo)) {
                favoritePoints.remove(museo)
                saveFavoritesToSharedPreferences(favoritePoints)
                Toast.makeText(this, "${museo.nombre} eliminado de favoritos", Toast.LENGTH_SHORT).show()
            } else {
                favoritePoints.add(museo)
                saveFavoritesToSharedPreferences(favoritePoints)
                Toast.makeText(this, "${museo.nombre} añadido a favoritos", Toast.LENGTH_SHORT).show()
            }
            updateFavoriteIcon(btnFavorite, museo)
        }


        bottomSheetDialog.setOnDismissListener {
            currentBottomSheetView = null // Liberar referencia al cerrar el BottomSheet
        }

        // Mostrar el Bottom Sheet
        bottomSheetDialog.show()
    }

    private fun updateFavoriteIcon(button: ImageButton, museo: PointOfInterest) {
        if (favoritePoints.contains(museo)) {
            button.setImageResource(R.drawable.baseline_favorite)
        } else {
            button.setImageResource(R.drawable.baseline_favorite_border)
        }
    }

    private fun showFavoritesBottomSheet() {
        val bottomSheetView = layoutInflater.inflate(R.layout.bottom_sheet_favorites, null)
        val bottomSheetDialog = BottomSheetDialog(this, R.style.CustomBottomSheetDialog)
        bottomSheetDialog.setContentView(bottomSheetView)

        val favoritesList = bottomSheetView.findViewById<RecyclerView>(R.id.recyclerViewFavorites)
        favoritesList.layoutManager = LinearLayoutManager(this)
        favoritesList.adapter = FavoritesAdapter(favoritePoints.toList().toMutableList()) { point ->
            mapView.controller.animateTo(GeoPoint(point.latitud, point.longitud))
            bottomSheetDialog.dismiss()
        }

        bottomSheetDialog.show()
    }

    fun saveFavoritesToSharedPreferences(favorites: List<PointOfInterest>) {
        val sharedPreferences = getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        val gson = Gson()
        val jsonFavorites = gson.toJson(favorites)
        editor.putString("favorites", jsonFavorites)
        editor.apply()
    }

    fun loadFavoritesFromSharedPreferences(): MutableList<PointOfInterest> {
        val sharedPreferences = getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        val gson = Gson()
        val jsonFavorites = sharedPreferences.getString("favorites", null)
        val type = object : TypeToken<MutableList<PointOfInterest>>() {}.type
        return if (jsonFavorites != null) gson.fromJson(jsonFavorites, type) else mutableListOf()
    }




    private fun openGoogleMaps(lat: Double, lon: Double) {
        try {
            val uri = Uri.parse("geo:$lat,$lon?q=$lat,$lon")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.setPackage("com.google.android.apps.maps") // Asegurarnos de usar Google Maps
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Google Maps no está instalado viajero.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showRouteToMuseum(museo: PointOfInterest) {
        if (currentRoute != null) {
            Toast.makeText(this, "Ya hay una ruta activa. Cancélala para empezar otra...", Toast.LENGTH_SHORT).show()
            return
        }

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
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

    private fun showTutorialBottomSheet() {
        // Inflar la vista del BottomSheet
        val bottomSheetView = layoutInflater.inflate(R.layout.bottom_sheet_tutorial, null)

        // Crear el BottomSheetDialog
        val bottomSheetDialog = BottomSheetDialog(this)
        bottomSheetDialog.setContentView(bottomSheetView)

        // Mostrar el BottomSheet
        bottomSheetDialog.show()
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
                    showAlertDialog("No estás cerca de ningún punto de interés, viajero.")
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

    private var currentLanguage = "es" // Idioma por defecto
    private fun changeLanguage(language: String) {
        val sharedPreferences = getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        val currentLanguage = sharedPreferences.getString("language", "es") // Idioma actual
        if (currentLanguage == language) {
            val message = if (language == "es") "Ya estás en Español" else "You are already in English"
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            return
        }

        // Guardar el nuevo idioma en las preferencias
        sharedPreferences.edit().putString("language", language).apply()


        // Cargar el archivo JSON correspondiente
        val jsonFileName = if (language == "es") "puntos_interes.json" else "puntos_interes_en.json"
        pointsOfInterest = loadPointsOfInterest(jsonFileName)
        updatePointsOfInterestMarkers()

        // Actualizar textos en el BottomSheet actual si está abierto
        currentBottomSheetView?.let { updateUITexts(it) }

        // Reiniciar la animación del marcador del usuario si existe
        if (userMarker != null) {
            stopUserMarkerAnimation() // Detenemos cualquier animación en curso
            startUserMarkerAnimation() // Reiniciamos la animación
        }

        val confirmationMessage = if (language == "es") "Idioma cambiado a Español" else "Language changed to English"
        Toast.makeText(this, confirmationMessage, Toast.LENGTH_SHORT).show()
    }

    private fun updateUITexts(view: View) {
        // Actualizar textos del BottomSheet usando el View correspondiente
        view.findViewById<Button>(R.id.btnRuta)?.text = getString(R.string.quick_route)
        view.findViewById<Button>(R.id.btnRouteMaps)?.text = getString(R.string.route_in_maps)
        view.findViewById<Button>(R.id.btnAR)?.text = getString(R.string.start_ra)
        view.findViewById<Button>(R.id.btnNoAR)?.text = getString(R.string.non_ra_info)
        view.findViewById<Button>(R.id.btnNext)?.text =getString(R.string.next)
    }




    // Variable para manejar el ciclo de animación del marcador del usuario
    private val userMarkerFrames = listOf(
        R.drawable.user_1,
        R.drawable.user_2,
        R.drawable.user_3,
        R.drawable.user_4,
        R.drawable.user_5,
        R.drawable.user_6,
        R.drawable.user_7,
        R.drawable.user_8,
        R.drawable.user_9,
        R.drawable.user_10
    )
    private var userMarkerCurrentFrame = 0
    private val userMarkerAnimationHandler = Handler(Looper.getMainLooper())

    // Método para inicializar la animación del marcador del usuario
    private fun startUserMarkerAnimation() {
        userMarker?.let { marker ->
            val runnable = object : Runnable {
                override fun run() {
                    // Cambiar la imagen del marcador
                    marker.icon = ContextCompat.getDrawable(this@MainActivity, userMarkerFrames[userMarkerCurrentFrame])
                    mapView.invalidate() // Refrescar el mapa

                    // Pasar al siguiente frame
                    userMarkerCurrentFrame = (userMarkerCurrentFrame + 1) % userMarkerFrames.size

                    // Volver a ejecutar el Runnable después de 100ms
                    userMarkerAnimationHandler.postDelayed(this, 100)
                }
            }

            // Iniciar la animación
            userMarkerAnimationHandler.post(runnable)
        }
    }

    // Método para detener la animación del marcador del usuario
    private fun stopUserMarkerAnimation() {
        userMarkerAnimationHandler.removeCallbacksAndMessages(null)
    }




    private fun updatePointsOfInterestMarkers() {
        // Eliminar todos los marcadores previos de los puntos de interés
        mapView.overlays.removeAll(mapView.overlays.filter {
            it is Marker && it != userMarker
        })

        // Añadir los nuevos marcadores
        val handler = Handler(Looper.getMainLooper())
        for (point in pointsOfInterest) {
            val pointLocation = GeoPoint(point.latitud, point.longitud)
            val marker = Marker(mapView).apply {
                position = pointLocation
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = point.nombre
                snippet = point.descripcion

                // Animación personalizada para el marcador
                val frames = listOf(
                    R.drawable.frame_0,
                    R.drawable.frame_1,
                    R.drawable.frame_2,
                    R.drawable.frame_3,
                    R.drawable.frame_4,
                )
                var currentFrame = 0

                val runnable = object : Runnable {
                    override fun run() {
                        icon = ContextCompat.getDrawable(this@MainActivity, frames[currentFrame])
                        mapView.invalidate()
                        currentFrame = (currentFrame + 1) % frames.size
                        handler.postDelayed(this, 100)
                    }
                }
                handler.post(runnable)

                // Manejar eventos de clic en el marcador
                setOnMarkerClickListener { _, _ ->
                    showMuseumDetails(point)
                    true
                }
            }

            // Agregar el marcador al mapa
            mapView.overlays.add(marker)
        }

        // Refrescar el mapa
        mapView.invalidate()
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
        builder.setMessage("TuristeAR fue desarrollado como proyecto de Trabajo Terminal para el Instituto Politécnico Nacional en la Escuela Superior de Cómputo. Todos los derechos son los declarados dentro del reglamento interno del IPN.")
        builder.setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
        builder.create().show()
    }

    private fun showAssistantOverlay(museo: PointOfInterest) {
        if (isOverlayActive){
            val builder = AlertDialog.Builder(this)
            builder.setTitle("Información activa")
            builder.setMessage("Ya se está mostrando información. Por favor, finaliza la actual antes de continuar.")
            builder.setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            builder.show()
            return
        }


        // Inflar la vista del overlay
        val overlayView = layoutInflater.inflate(R.layout.assistant_overlay, null)
        val overlayDialogText = overlayView.findViewById<TextView>(R.id.overlayDialogText)

        // Configurar el WindowManager
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        layoutParams.gravity = Gravity.BOTTOM

        // Configurar diálogos del museo
        val dialogs = museo.dialogos
        var currentIndex = 0
        overlayDialogText.text = dialogs[currentIndex]
        isOverlayActive = true

        // Añadir la vista al WindowManager
        windowManager.addView(overlayView, layoutParams)

        // Manejar toques en el overlay para avanzar los diálogos
        overlayView.setOnClickListener {
            currentIndex++
            if (currentIndex < dialogs.size) {
                overlayDialogText.text = dialogs[currentIndex]
            } else {
                // Eliminar el overlay cuando se acaben los diálogos
                windowManager.removeView(overlayView)
                isOverlayActive = false
            }
        }
    }


    // Respuesta a la solicitud de permisos
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permiso concedido
                startLocationUpdates()
            } else {
                // Permiso denegado; puedes manejar este caso si es necesario
                Toast.makeText(this, "Permiso de ubicación denegado. Algunas funciones estarán limitadas.", Toast.LENGTH_SHORT).show()
                val defaultLocation = GeoPoint(19.432608, -99.133209) // CDMX como ejemplo
                mapView.controller.setCenter(defaultLocation)
                mapView.controller.setZoom(10.0)
            }
        }
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
        if (this::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

}
