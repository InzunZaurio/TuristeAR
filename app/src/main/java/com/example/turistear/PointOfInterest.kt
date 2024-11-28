package com.example.turistear

data class PointOfInterest(
    val nombre: String,
    val latitud: Double,
    val longitud: Double,
    val descripcion: String,
    val dialogos: List<String>,
    val horarios: String,
    val accesibilidad: String
)