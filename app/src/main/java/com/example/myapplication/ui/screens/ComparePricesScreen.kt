package com.example.myapplication.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.myapplication.data.*
import com.example.myapplication.ui.theme.WiseUpColors
import com.google.android.gms.location.LocationServices
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch
import kotlin.math.*

private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
    return r * 2 * asin(sqrt(a))
}

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComparePricesScreen() {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var lists by remember { mutableStateOf<List<ShoppingList>>(emptyList()) }
    var stores by remember { mutableStateOf<List<Store>>(emptyList()) }
    var storeHqs by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var selectedListId by remember { mutableStateOf<String?>(null) }
    var selectedStoreIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var results by remember { mutableStateOf<List<ComparisonResult>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isComparing by remember { mutableStateOf(false) }
    var isAutoChecking by remember { mutableStateOf(false) }
    var showStoreSelector by remember { mutableStateOf(false) }
    var listExpanded by remember { mutableStateOf(false) }
    var autoCheckError by remember { mutableStateOf<String?>(null) }

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasLocationPermission = granted }

    LaunchedEffect(Unit) {
        try {
            val userId = SupabaseClient.client.auth.currentUserOrNull()?.id ?: return@LaunchedEffect
            lists = SupabaseClient.client.from("shopping_lists")
                .select { filter { eq("user_id", userId) } }
                .decodeList()
            stores = SupabaseClient.client.from("stores").select().decodeList()
            val hqs: List<StoreHq> = SupabaseClient.client.from("store_hq").select().decodeList()
            storeHqs = hqs.associate { it.id to it.name }
        } catch (_: Exception) { }
        isLoading = false
    }

    suspend fun buildResult(
        store: Store,
        listItems: List<ShoppingListItem>,
        distanceKm: Double? = null
    ): ComparisonResult? {
        return try {
            val prices: List<StorePrice> = SupabaseClient.client.from("store_prices")
                .select { filter { eq("store_id", store.id) } }
                .decodeList()
            val priceMap = prices.associateBy { it.productGtin }
            var totalInStock = 0.0; var totalAll = 0.0
            var inStockCount = 0; var missingCount = 0
            val resultPriceMap = mutableMapOf<String, StorePrice?>()
            listItems.forEach { item ->
                val sp = priceMap[item.productGtin]
                resultPriceMap[item.productGtin] = sp
                if (sp != null) {
                    val cost = sp.price * item.quantity
                    totalAll += cost
                    if (sp.inStock) { totalInStock += cost; inStockCount++ } else missingCount++
                } else missingCount++
            }
            ComparisonResult(
                store = store,
                hqName = storeHqs[store.hqId] ?: "Unknown",
                totalInStock = totalInStock,
                totalAll = totalAll,
                itemsInStock = inStockCount,
                itemsMissing = missingCount,
                priceMap = resultPriceMap,
                distanceKm = distanceKm
            )
        } catch (_: Exception) { null }
    }

    fun compare() {
        val listId = selectedListId ?: return
        if (selectedStoreIds.isEmpty()) return
        isComparing = true
        autoCheckError = null
        scope.launch {
            try {
                val listItems: List<ShoppingListItem> = SupabaseClient.client.from("shopping_list_items")
                    .select { filter { eq("shopping_list_id", listId) } }
                    .decodeList()
                val compResults = mutableListOf<ComparisonResult>()
                selectedStoreIds.forEach { storeId ->
                    val store = stores.find { it.id == storeId } ?: return@forEach
                    buildResult(store, listItems)?.let { compResults.add(it) }
                }
                results = compResults.sortedBy { it.totalInStock }
            } catch (_: Exception) { }
            isComparing = false
        }
    }

    fun autoCheck() {
        val listId = selectedListId ?: return
        if (!hasLocationPermission) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }
        isAutoChecking = true
        autoCheckError = null
        results = emptyList()
        val fusedClient = LocationServices.getFusedLocationProviderClient(context)
        fusedClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location == null) {
                autoCheckError = "Unable to get your location. Please enable GPS and try again."
                isAutoChecking = false
                return@addOnSuccessListener
            }
            val userLat = location.latitude
            val userLon = location.longitude
            scope.launch {
                try {
                    val listItems: List<ShoppingListItem> = SupabaseClient.client
                        .from("shopping_list_items")
                        .select { filter { eq("shopping_list_id", listId) } }
                        .decodeList()
                    val gtins = listItems.map { it.productGtin }.distinct()
                    // Find stores that have at least one item from the list
                    val candidateStores = stores.filter { store ->
                        store.latitude != null && store.longitude != null
                    }.map { store ->
                        val dist = haversineKm(userLat, userLon, store.latitude!!, store.longitude!!)
                        store to dist
                    }.sortedBy { it.second }.take(5)

                    val compResults = mutableListOf<ComparisonResult>()
                    candidateStores.forEach { (store, dist) ->
                        buildResult(store, listItems, distanceKm = dist)?.let { compResults.add(it) }
                    }
                    results = compResults.sortedBy { it.distanceKm }
                    selectedStoreIds = compResults.map { it.store.id }.toSet()
                } catch (_: Exception) {
                    autoCheckError = "Failed to run auto-check"
                }
                isAutoChecking = false
            }
        }.addOnFailureListener {
            autoCheckError = "Location error: ${it.message}"
            isAutoChecking = false
        }
    }

    // Store selector dialog
    if (showStoreSelector) {
        AlertDialog(
            onDismissRequest = { showStoreSelector = false },
            title = { Text("Select Stores (up to 5)") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(stores) { store ->
                        val hqName = storeHqs[store.hqId] ?: "Unknown"
                        val isSelected = selectedStoreIds.contains(store.id)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { checked ->
                                    selectedStoreIds = if (checked) {
                                        if (selectedStoreIds.size < 5) selectedStoreIds + store.id
                                        else selectedStoreIds
                                    } else {
                                        selectedStoreIds - store.id
                                    }
                                }
                            )
                            Text("$hqName - ${store.location}", modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showStoreSelector = false },
                    colors = ButtonDefaults.buttonColors(containerColor = WiseUpColors.Blue500)
                ) { Text("Done (${selectedStoreIds.size})") }
            },
            dismissButton = {
                TextButton(onClick = { showStoreSelector = false }) { Text("Cancel") }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Compare Prices", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = WiseUpColors.Blue500)
            }
        } else {
            // List selector
            ExposedDropdownMenuBox(
                expanded = listExpanded,
                onExpandedChange = { listExpanded = !listExpanded }
            ) {
                OutlinedTextField(
                    value = lists.find { it.id == selectedListId }?.name ?: "Select a shopping list",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = listExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    shape = RoundedCornerShape(12.dp)
                )
                ExposedDropdownMenu(
                    expanded = listExpanded,
                    onDismissRequest = { listExpanded = false }
                ) {
                    lists.forEach { list ->
                        DropdownMenuItem(
                            text = { Text(list.name) },
                            onClick = { selectedListId = list.id; listExpanded = false }
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Store selector + Auto-Check side by side
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { showStoreSelector = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Store, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (selectedStoreIds.isEmpty()) "Select stores"
                        else "${selectedStoreIds.size} store(s)",
                        fontSize = 13.sp
                    )
                }
                Button(
                    onClick = { autoCheck() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WiseUpColors.Orange500),
                    enabled = selectedListId != null && !isAutoChecking && !isComparing
                ) {
                    if (isAutoChecking) {
                        CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.MyLocation, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Auto-Check", fontSize = 13.sp)
                    }
                }
            }

            autoCheckError?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = WiseUpColors.Red500, fontSize = 12.sp)
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = { compare() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = WiseUpColors.Blue500),
                shape = RoundedCornerShape(12.dp),
                enabled = selectedListId != null && selectedStoreIds.isNotEmpty() && !isComparing && !isAutoChecking
            ) {
                if (isComparing) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.AutoMirrored.Filled.CompareArrows, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Compare")
                }
            }

            Spacer(Modifier.height(16.dp))

            // Results
            if (results.isNotEmpty()) {
                Text("Results", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(8.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(results.mapIndexed { index, result -> index to result }) { (index, result) ->
                        val isCheapest = index == 0
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isCheapest) WiseUpColors.Green100 else Color.White
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                "${result.hqName} - ${result.store.location}",
                                                fontWeight = FontWeight.Bold, fontSize = 15.sp
                                            )
                                            if (isCheapest) {
                                                Spacer(Modifier.width(8.dp))
                                                AssistChip(
                                                    onClick = {},
                                                    label = { Text("Cheapest", fontSize = 11.sp) },
                                                    colors = AssistChipDefaults.assistChipColors(
                                                        containerColor = WiseUpColors.Blue500,
                                                        labelColor = Color.White
                                                    ),
                                                    modifier = Modifier.height(24.dp)
                                                )
                                            }
                                        }
                                        result.distanceKm?.let { dist ->
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Default.LocationOn, null,
                                                    modifier = Modifier.size(14.dp),
                                                    tint = WiseUpColors.Orange500
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text(
                                                    if (dist < 1.0) "${"%.0f".format(dist * 1000)} m away"
                                                    else "${"%.1f".format(dist)} km away",
                                                    fontSize = 12.sp,
                                                    color = WiseUpColors.TextSecondary
                                                )
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column {
                                        Text("In-Stock Total", fontSize = 12.sp, color = WiseUpColors.TextMuted)
                                        Text(CurrencyProvider.formatPrice(result.totalInStock), fontWeight = FontWeight.Bold, color = WiseUpColors.Blue500)
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Items In Stock", fontSize = 12.sp, color = WiseUpColors.TextMuted)
                                        Text("${result.itemsInStock}", fontWeight = FontWeight.Bold)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("Missing", fontSize = 12.sp, color = WiseUpColors.TextMuted)
                                        Text("${result.itemsMissing}", fontWeight = FontWeight.Bold, color = WiseUpColors.Red500)
                                    }
                                }

                                // Map navigation button
                                val store = result.store
                                if (store.latitude != null && store.longitude != null || !store.address.isNullOrBlank()) {
                                    Spacer(Modifier.height(8.dp))
                                    OutlinedButton(
                                        onClick = {
                                            val uri = if (store.latitude != null && store.longitude != null) {
                                                Uri.parse("google.navigation:q=${store.latitude},${store.longitude}")
                                            } else {
                                                Uri.parse("google.navigation:q=${Uri.encode(store.address)}")
                                            }
                                            val mapIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                                                setPackage("com.google.android.apps.maps")
                                            }
                                            if (mapIntent.resolveActivity(context.packageManager) != null) {
                                                context.startActivity(mapIntent)
                                            } else {
                                                // Fallback to browser
                                                val webUri = if (store.latitude != null && store.longitude != null) {
                                                    Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${store.latitude},${store.longitude}")
                                                } else {
                                                    Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${Uri.encode(store.address)}")
                                                }
                                                context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.Map, null, Modifier.size(16.dp), tint = WiseUpColors.Blue500)
                                        Spacer(Modifier.width(6.dp))
                                        Text("Navigate to Store", fontSize = 13.sp, color = WiseUpColors.Blue500)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
