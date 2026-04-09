package com.example.myapplication.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Geocoder
import java.util.Locale
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.myapplication.data.*
import com.example.myapplication.ui.theme.WiseUpColors
import com.google.android.gms.location.LocationServices
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreAdminScreen(store: Store, hqName: String, onStoreUpdated: (Store) -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var email by remember(store.id) { mutableStateOf(store.email ?: "") }
    var contact by remember(store.id) { mutableStateOf(store.contact ?: "") }
    var whatsapp by remember(store.id) { mutableStateOf(store.whatsapp ?: "") }
    var location by remember(store.id) { mutableStateOf(store.location) }
    var address by remember(store.id) { mutableStateOf(store.address ?: "") }
    var latitude by remember(store.id) { mutableStateOf(store.latitude?.toString() ?: "") }
    var longitude by remember(store.id) { mutableStateOf(store.longitude?.toString() ?: "") }
    var isSaving by remember { mutableStateOf(false) }
    var saveMessage by remember { mutableStateOf<String?>(null) }
    var isLocating by remember { mutableStateOf(false) }
    var isGeocodingAddress by remember { mutableStateOf(false) }

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasLocationPermission = granted }

    @SuppressLint("MissingPermission")
    fun useCurrentLocation() {
        if (!hasLocationPermission) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }
        isLocating = true
        val fusedClient = LocationServices.getFusedLocationProviderClient(context)
        fusedClient.lastLocation.addOnSuccessListener { loc ->
            if (loc != null) {
                latitude = loc.latitude.toString()
                longitude = loc.longitude.toString()
                // Reverse geocode to fill address
                try {
                    val geocoder = Geocoder(context, Locale.getDefault())
                    @Suppress("DEPRECATION")
                    val results = geocoder.getFromLocation(loc.latitude, loc.longitude, 1)
                    if (!results.isNullOrEmpty()) {
                        val a = results[0]
                        val parts = listOfNotNull(
                            a.featureName, a.thoroughfare, a.locality, a.countryName
                        ).distinct()
                        address = parts.joinToString(", ")
                    }
                } catch (_: Exception) { }
                saveMessage = "Location & address captured!"
            } else {
                saveMessage = "Unable to get location. Enable GPS and try again."
            }
            isLocating = false
        }.addOnFailureListener {
            saveMessage = "Location error: ${it.message}"
            isLocating = false
        }
    }

    fun saveStoreProfile() {
        isSaving = true
        saveMessage = null
        scope.launch {
            try {
                val update = StoreUpdate(
                    email = email.ifBlank { null },
                    contact = contact.ifBlank { null },
                    whatsapp = whatsapp.ifBlank { null },
                    location = location.ifBlank { null },
                    address = address.ifBlank { null },
                    latitude = latitude.toDoubleOrNull(),
                    longitude = longitude.toDoubleOrNull()
                )
                SupabaseClient.client.from("stores").update(update) {
                    filter { eq("id", store.id) }
                }
                // Reload the store
                val updated: List<Store> = SupabaseClient.client.from("stores")
                    .select { filter { eq("id", store.id) } }
                    .decodeList()
                updated.firstOrNull()?.let { onStoreUpdated(it) }
                saveMessage = "Store profile saved!"
            } catch (e: Exception) {
                saveMessage = "Error: ${e.message}"
            }
            isSaving = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Store info header
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = WiseUpColors.Blue100)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Store, null, tint = WiseUpColors.Blue500, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(hqName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(store.location, fontSize = 12.sp, color = WiseUpColors.TextSecondary)
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("Store Profile", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.height(12.dp))

        // Location / Branch Name
        OutlinedTextField(
            value = location,
            onValueChange = { location = it },
            label = { Text("Branch Name / Location") },
            leadingIcon = { Icon(Icons.Default.LocationCity, null) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )
        Spacer(Modifier.height(10.dp))

        // Address
        OutlinedTextField(
            value = address,
            onValueChange = { address = it },
            label = { Text("Store Address") },
            leadingIcon = { Icon(Icons.Default.Place, null) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = false,
            maxLines = 2
        )
        Spacer(Modifier.height(6.dp))

        // Use this location button — reverse geocode lat/lng into address
        OutlinedButton(
            onClick = {
                val lat = latitude.toDoubleOrNull()
                val lng = longitude.toDoubleOrNull()
                if (lat != null && lng != null) {
                    isGeocodingAddress = true
                    scope.launch {
                        try {
                            val geocoder = Geocoder(context, Locale.getDefault())
                            @Suppress("DEPRECATION")
                            val results = geocoder.getFromLocation(lat, lng, 1)
                            if (!results.isNullOrEmpty()) {
                                val a = results[0]
                                val parts = listOfNotNull(
                                    a.featureName, a.thoroughfare, a.locality, a.countryName
                                ).distinct()
                                address = parts.joinToString(", ")
                                saveMessage = "Address filled from coordinates!"
                            } else {
                                saveMessage = "Could not resolve address for these coordinates."
                            }
                        } catch (e: Exception) {
                            saveMessage = "Geocoding error: ${e.message}"
                        }
                        isGeocodingAddress = false
                    }
                } else {
                    saveMessage = "Set latitude and longitude first."
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            enabled = !isGeocodingAddress
        ) {
            if (isGeocodingAddress) {
                CircularProgressIndicator(Modifier.size(16.dp), color = WiseUpColors.Blue500, strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.LocationOn, null, Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text("Use this location for address")
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        Text("Contact Information", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(10.dp))

        // Email
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Store Email") },
            leadingIcon = { Icon(Icons.Default.Email, null) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )
        Spacer(Modifier.height(10.dp))

        // Contact number
        OutlinedTextField(
            value = contact,
            onValueChange = { contact = it },
            label = { Text("Contact Number") },
            leadingIcon = { Icon(Icons.Default.Phone, null) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
        )
        Spacer(Modifier.height(10.dp))

        // WhatsApp
        OutlinedTextField(
            value = whatsapp,
            onValueChange = { whatsapp = it },
            label = { Text("WhatsApp Number") },
            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Chat, null) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
        )

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        Text("GPS Coordinates", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = latitude,
                onValueChange = { latitude = it },
                label = { Text("Latitude") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            OutlinedTextField(
                value = longitude,
                onValueChange = { longitude = it },
                label = { Text("Longitude") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
        }

        Spacer(Modifier.height(10.dp))

        // Use current location button
        OutlinedButton(
            onClick = { useCurrentLocation() },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            enabled = !isLocating
        ) {
            if (isLocating) {
                CircularProgressIndicator(Modifier.size(16.dp), color = WiseUpColors.Blue500, strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.MyLocation, null, Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text("Use Current Location")
        }

        Spacer(Modifier.height(20.dp))

        // Save button
        Button(
            onClick = { saveStoreProfile() },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = WiseUpColors.Blue500),
            enabled = !isSaving
        ) {
            if (isSaving) {
                CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Save, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Save Store Profile")
            }
        }

        // Save message
        saveMessage?.let { msg ->
            Spacer(Modifier.height(10.dp))
            Text(
                msg,
                fontSize = 13.sp,
                color = if (msg.startsWith("Error")) WiseUpColors.Red500 else WiseUpColors.Blue500,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(Modifier.height(32.dp))
    }
}
