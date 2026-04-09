package com.example.myapplication.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.myapplication.data.*
import com.example.myapplication.ui.components.VerificationBadge
import com.example.myapplication.ui.theme.WiseUpColors
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseStoreScreen() {
    val scope = rememberCoroutineScope()

    // ---- Store picker state ----
    var stores by remember { mutableStateOf<List<Store>>(emptyList()) }
    var storeHqs by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var storeSearch by remember { mutableStateOf("") }
    var isLoadingStores by remember { mutableStateOf(true) }

    // ---- Selected store state ----
    var selectedStore by remember { mutableStateOf<Store?>(null) }

    // ---- Virtual store state ----
    var storePrices by remember { mutableStateOf<List<StorePrice>>(emptyList()) }
    var products by remember { mutableStateOf<Map<String, Product>>(emptyMap()) }
    var productSearch by remember { mutableStateOf("") }
    var isLoadingProducts by remember { mutableStateOf(false) }
    var hasSearchedProducts by remember { mutableStateOf(false) }

    // ---- Shopping list state ----
    var shoppingLists by remember { mutableStateOf<List<ShoppingList>>(emptyList()) }
    var showAddToListDialog by remember { mutableStateOf<StorePrice?>(null) }
    var selectedListId by remember { mutableStateOf<String?>(null) }
    var addToListMsg by remember { mutableStateOf<String?>(null) }

    // ---- Update price dialog state ----
    var showUpdatePriceDialog by remember { mutableStateOf<StorePrice?>(null) }
    var newPriceInput by remember { mutableStateOf("") }
    var isSubmittingPrice by remember { mutableStateOf(false) }
    var priceUpdateMsg by remember { mutableStateOf<String?>(null) }

    // ---- Barcode scanner state ----
    var showScanner by remember { mutableStateOf(false) }
    var scannerGtin by remember { mutableStateOf("") }

    // ---- Share list state ----
    var showShareListPicker by remember { mutableStateOf(false) }

    // Load stores on mount
    LaunchedEffect(Unit) {
        try {
            stores = SupabaseClient.client.from("stores").select().decodeList()
            val hqs: List<StoreHq> = SupabaseClient.client.from("store_hq").select().decodeList()
            storeHqs = hqs.associate { it.id to it.name }
        } catch (_: Exception) { }
        isLoadingStores = false
    }

    // Load shopping lists for add-to-list feature
    LaunchedEffect(Unit) {
        try {
            val userId = SupabaseClient.client.auth.currentUserOrNull()?.id ?: return@LaunchedEffect
            shoppingLists = SupabaseClient.client.from("shopping_lists")
                .select { filter { eq("user_id", userId) } }
                .decodeList()
        } catch (_: Exception) { }
    }

    // Search store products by GTIN or description
    fun searchStoreProducts(storeId: String) {
        val q = productSearch.trim()
        if (q.isBlank()) return
        isLoadingProducts = true
        hasSearchedProducts = true
        scope.launch {
            try {
                // Find matching products by GTIN or description
                val matchingProducts: List<Product> = SupabaseClient.client.from("products")
                    .select {
                        filter {
                            or {
                                ilike("gtin", "%$q%")
                                ilike("description", "%$q%")
                            }
                        }
                    }
                    .decodeList()
                val gtinMap = matchingProducts.associateBy { it.gtin }

                // Get store prices for matching GTINs in this store
                val results = mutableListOf<StorePrice>()
                if (gtinMap.isNotEmpty()) {
                    gtinMap.keys.toList().chunked(50).forEach { batch ->
                        val prices: List<StorePrice> = SupabaseClient.client.from("store_prices")
                            .select {
                                filter {
                                    eq("store_id", storeId)
                                    isIn("product_gtin", batch)
                                }
                            }
                            .decodeList()
                        results.addAll(prices)
                    }
                }
                storePrices = results
                products = gtinMap
            } catch (_: Exception) { }
            isLoadingProducts = false
        }
    }

    // ---- ADD TO LIST DIALOG ----
    showAddToListDialog?.let { sp ->
        val prodName = products[sp.productGtin]?.description ?: sp.productGtin
        AlertDialog(
            onDismissRequest = { showAddToListDialog = null; selectedListId = null },
            title = { Text("Add to Shopping List") },
            text = {
                Column {
                    Text("$prodName", fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(12.dp))
                    if (shoppingLists.isEmpty()) {
                        Text("No shopping lists found. Create one first.", color = WiseUpColors.TextMuted, fontSize = 13.sp)
                    } else {
                        Text("Select a list:", fontSize = 13.sp, color = WiseUpColors.TextSecondary)
                        Spacer(Modifier.height(8.dp))
                        shoppingLists.forEach { list ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedListId == list.id,
                                    onClick = { selectedListId = list.id },
                                    colors = RadioButtonDefaults.colors(selectedColor = WiseUpColors.Blue500)
                                )
                                Text(list.name, modifier = Modifier.padding(start = 4.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val listId = selectedListId ?: return@Button
                        scope.launch {
                            try {
                                SupabaseClient.client.from("shopping_list_items").insert(
                                    ShoppingListItemCreate(
                                        shoppingListId = listId,
                                        productGtin = sp.productGtin,
                                        quantity = 1
                                    )
                                )
                                addToListMsg = "${prodName} added to list!"
                            } catch (_: Exception) {
                                addToListMsg = "Failed to add item"
                            }
                            showAddToListDialog = null
                            selectedListId = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WiseUpColors.Blue500),
                    enabled = selectedListId != null
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddToListDialog = null; selectedListId = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // ---- UPDATE PRICE DIALOG ----
    showUpdatePriceDialog?.let { sp ->
        val prodName = products[sp.productGtin]?.description ?: sp.productGtin
        AlertDialog(
            onDismissRequest = {
                showUpdatePriceDialog = null; newPriceInput = ""
            },
            title = { Text("Update Price") },
            text = {
                Column {
                    Text(prodName, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))

                    if (sp.verified) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Verified Price: ", fontSize = 13.sp, color = WiseUpColors.TextSecondary)
                            Text(CurrencyProvider.formatPrice(sp.price), fontWeight = FontWeight.Bold, color = WiseUpColors.Green600)
                        }
                    }
                    sp.unverifiedPrice?.let { uv ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Community Price: ", fontSize = 13.sp, color = WiseUpColors.TextSecondary)
                            Text(CurrencyProvider.formatPrice(uv), fontWeight = FontWeight.Bold, color = WiseUpColors.Red500)
                        }
                    }
                    if (!sp.verified && sp.unverifiedPrice == null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Current Price: ", fontSize = 13.sp, color = WiseUpColors.TextSecondary)
                            Text(CurrencyProvider.formatPrice(sp.price), fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newPriceInput,
                        onValueChange = { newPriceInput = it },
                        label = { Text("Enter New Price") },
                        leadingIcon = { Icon(Icons.Default.AttachMoney, null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val priceVal = newPriceInput.toDoubleOrNull() ?: return@Button
                        isSubmittingPrice = true
                        scope.launch {
                            try {
                                val userId = SupabaseClient.client.auth.currentUserOrNull()?.id ?: return@launch
                                SupabaseClient.client.from("store_prices").upsert(
                                    StorePriceUpsert(
                                        storeId = sp.storeId,
                                        productGtin = sp.productGtin,
                                        price = sp.price,
                                        inStock = sp.inStock,
                                        verified = false,
                                        updatedBy = userId,
                                        source = "shopper",
                                        unverifiedPrice = priceVal
                                    )
                                ) {
                                    onConflict = "store_id,product_gtin"
                                }
                                priceUpdateMsg = "Price submitted! Awaiting verification."
                                // Reload search results
                                selectedStore?.let { searchStoreProducts(it.id) }
                            } catch (_: Exception) {
                                priceUpdateMsg = "Failed to submit price"
                            }
                            isSubmittingPrice = false
                            showUpdatePriceDialog = null
                            newPriceInput = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WiseUpColors.Blue500),
                    enabled = newPriceInput.toDoubleOrNull() != null && !isSubmittingPrice
                ) {
                    if (isSubmittingPrice) {
                        CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Submit")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showUpdatePriceDialog = null; newPriceInput = "" }) {
                    Text("Cancel")
                }
            }
        )
    }

    // ---- MAIN CONTENT ----
    if (selectedStore == null) {
        // ======== STORE PICKER ========
        Column(modifier = Modifier.fillMaxSize()) {
            // Search bar
            OutlinedTextField(
                value = storeSearch,
                onValueChange = { storeSearch = it },
                label = { Text("Search stores...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (storeSearch.isNotBlank()) {
                        IconButton(onClick = { storeSearch = "" }) {
                            Icon(Icons.Default.Close, null)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            if (isLoadingStores) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = WiseUpColors.Blue500)
                }
            } else {
                val filtered = stores.filter { store ->
                    val hqName = storeHqs[store.hqId] ?: ""
                    val query = storeSearch.trim().lowercase()
                    query.isBlank() ||
                            hqName.lowercase().contains(query) ||
                            store.location.lowercase().contains(query) ||
                            (store.city?.lowercase()?.contains(query) == true)
                }

                if (filtered.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.StoreMallDirectory, null, Modifier.size(64.dp), tint = WiseUpColors.TextMuted)
                            Spacer(Modifier.height(8.dp))
                            Text("No stores found", color = WiseUpColors.TextSecondary)
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filtered) { store ->
                            val hqName = storeHqs[store.hqId] ?: "Unknown"
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Store,
                                        null,
                                        tint = WiseUpColors.Orange500,
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(hqName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        Text(store.location, fontSize = 13.sp, color = WiseUpColors.TextSecondary)
                                        store.city?.let {
                                            Text(it, fontSize = 12.sp, color = WiseUpColors.TextMuted)
                                        }
                                    }
                                    Button(
                                        onClick = {
                                            selectedStore = store
                                            hasSearchedProducts = false
                                            productSearch = ""
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = WiseUpColors.Blue500),
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                    ) {
                                        Icon(Icons.Default.ShoppingCart, null, Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Shop")
                                    }
                                }
                            }
                        }
                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
        }
    } else {
        // ======== VIRTUAL STORE ========
        val store = selectedStore!!
        val hqName = storeHqs[store.hqId] ?: "Unknown"

        Column(modifier = Modifier.fillMaxSize()) {
            // Store header + back
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = WiseUpColors.Blue500)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        selectedStore = null
                        storePrices = emptyList()
                        products = emptyMap()
                        productSearch = ""
                        hasSearchedProducts = false
                        priceUpdateMsg = null
                        addToListMsg = null
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(hqName, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                        Text(store.location, fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
                    }
                    // Map button — opens Google Maps
                    if (store.latitude != null && store.longitude != null || store.address != null) {
                        val context = LocalContext.current
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = {
                                val uri = if (store.latitude != null && store.longitude != null) {
                                    Uri.parse("geo:${store.latitude},${store.longitude}?q=${store.latitude},${store.longitude}(${Uri.encode(hqName + " - " + store.location)})")
                                } else {
                                    Uri.parse("geo:0,0?q=${Uri.encode(store.address)}")
                                }
                                val mapIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                                    setPackage("com.google.android.apps.maps")
                                }
                                if (mapIntent.resolveActivity(context.packageManager) != null) {
                                    context.startActivity(mapIntent)
                                } else {
                                    val browserUri = if (store.latitude != null && store.longitude != null) {
                                        Uri.parse("https://www.google.com/maps/search/?api=1&query=${store.latitude},${store.longitude}")
                                    } else {
                                        Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode(store.address)}")
                                    }
                                    context.startActivity(Intent(Intent.ACTION_VIEW, browserUri))
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White)
                        ) {
                            Text("Map", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Messages
            addToListMsg?.let { msg ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F8E8))
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = WiseUpColors.Green600, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(msg, color = WiseUpColors.Green600, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = { addToListMsg = null }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, null, Modifier.size(14.dp), tint = WiseUpColors.TextMuted)
                        }
                    }
                }
            }
            priceUpdateMsg?.let { msg ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1))
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, null, tint = Color(0xFFFFA000), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(msg, color = Color(0xFF6D4C00), fontSize = 13.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = { priceUpdateMsg = null }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, null, Modifier.size(14.dp), tint = WiseUpColors.TextMuted)
                        }
                    }
                }
            }

            // Search bar + Scan button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = productSearch,
                    onValueChange = { productSearch = it; scannerGtin = "" },
                    placeholder = { Text("Search GTIN or product name...", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(20.dp)) },
                    trailingIcon = {
                        if (productSearch.isNotBlank()) {
                            IconButton(onClick = { selectedStore?.let { searchStoreProducts(it.id) } }) {
                                Icon(Icons.Default.Search, "Search", tint = WiseUpColors.Blue500)
                            }
                        }
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { selectedStore?.let { searchStoreProducts(it.id) } })
                )
                FilledTonalButton(
                    onClick = {
                        showScanner = !showScanner
                        if (!showScanner) { scannerGtin = "" }
                    },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (showScanner) WiseUpColors.Blue500 else WiseUpColors.Blue100
                    ),
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(
                        Icons.Default.QrCodeScanner,
                        contentDescription = "Scan barcode",
                        tint = if (showScanner) Color.White else WiseUpColors.Blue500
                    )
                }
                // Share List button
                FilledTonalButton(
                    onClick = { showShareListPicker = true },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = WiseUpColors.Blue100
                    ),
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(Icons.Default.Share, null, Modifier.size(16.dp), tint = WiseUpColors.Blue500)
                    Spacer(Modifier.width(4.dp))
                    Text("Share", fontSize = 11.sp, color = WiseUpColors.Blue500)
                }
            }

            // Share list picker dialog
            if (showShareListPicker) {
                val shareContext = LocalContext.current
                AlertDialog(
                    onDismissRequest = { showShareListPicker = false },
                    title = { Text("Share Shopping List", fontWeight = FontWeight.Bold) },
                    text = {
                        if (shoppingLists.isEmpty()) {
                            Text("You have no shopping lists yet.")
                        } else {
                            Column {
                                Text("Select a list to share:", fontSize = 13.sp, color = WiseUpColors.TextSecondary)
                                Spacer(Modifier.height(8.dp))
                                shoppingLists.forEach { list ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(containerColor = WiseUpColors.Blue100),
                                        onClick = {
                                            showShareListPicker = false
                                            scope.launch {
                                                try {
                                                    val items: List<ShoppingListItem> = SupabaseClient.client
                                                        .from("shopping_list_items")
                                                        .select { filter { eq("shopping_list_id", list.id) } }
                                                        .decodeList()
                                                    val gtins = items.map { it.productGtin }.distinct()
                                                    val prods: List<Product> = if (gtins.isNotEmpty()) {
                                                        SupabaseClient.client.from("products")
                                                            .select { filter { isIn("gtin", gtins) } }
                                                            .decodeList()
                                                    } else emptyList()
                                                    val prodMap = prods.associateBy { it.gtin }
                                                    val listText = buildString {
                                                        appendLine("🛒 ${list.name}")
                                                        if (list.budget != null) appendLine("Budget: ${CurrencyProvider.formatPrice(list.budget)}")
                                                        appendLine("────────────────")
                                                        items.forEachIndexed { idx, item ->
                                                            val name = prodMap[item.productGtin]?.description ?: item.productGtin
                                                            appendLine("${idx + 1}. $name  x${item.quantity}")
                                                        }
                                                        appendLine("────────────────")
                                                        appendLine("Shared from WiseUp Shop")
                                                    }
                                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                        type = "text/plain"
                                                        putExtra(Intent.EXTRA_TEXT, listText)
                                                    }
                                                    shareContext.startActivity(Intent.createChooser(shareIntent, "Share list via"))
                                                } catch (_: Exception) { }
                                            }
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.ShoppingCart, null, Modifier.size(20.dp), tint = WiseUpColors.Blue500)
                                            Spacer(Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(list.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                                if (list.budget != null) {
                                                    Text("Budget: ${CurrencyProvider.formatPrice(list.budget)}", fontSize = 11.sp, color = WiseUpColors.TextSecondary)
                                                }
                                            }
                                            Icon(Icons.Default.Share, null, Modifier.size(18.dp), tint = WiseUpColors.Blue500)
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { showShareListPicker = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // Inline barcode scanner
            if (showScanner) {
                val context = LocalContext.current
                val lifecycleOwner = LocalLifecycleOwner.current
                var hasCameraPermission by remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                                PackageManager.PERMISSION_GRANTED
                    )
                }
                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted -> hasCameraPermission = granted }

                LaunchedEffect(Unit) {
                    if (!hasCameraPermission) {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                }

                if (hasCameraPermission) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .height(200.dp),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            AndroidView(
                                factory = { ctx ->
                                    val previewView = PreviewView(ctx)
                                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                    cameraProviderFuture.addListener({
                                        val cameraProvider = cameraProviderFuture.get()
                                        val preview = Preview.Builder().build().also {
                                            it.setSurfaceProvider(previewView.surfaceProvider)
                                        }
                                        val imageAnalysis = ImageAnalysis.Builder()
                                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                            .build()
                                        val scanner = BarcodeScanning.getClient()
                                        val executor = Executors.newSingleThreadExecutor()

                                        imageAnalysis.setAnalyzer(executor) { imageProxy ->
                                            @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
                                            val mediaImage = imageProxy.image
                                            if (mediaImage != null) {
                                                val inputImage = InputImage.fromMediaImage(
                                                    mediaImage, imageProxy.imageInfo.rotationDegrees
                                                )
                                                scanner.process(inputImage)
                                                    .addOnSuccessListener { barcodes ->
                                                        for (barcode in barcodes) {
                                                            if (barcode.format == Barcode.FORMAT_EAN_13 ||
                                                                barcode.format == Barcode.FORMAT_EAN_8 ||
                                                                barcode.format == Barcode.FORMAT_UPC_A ||
                                                                barcode.format == Barcode.FORMAT_UPC_E
                                                            ) {
                                                                barcode.rawValue?.let { value ->
                                                                    if (scannerGtin.isBlank()) {
                                                                        scannerGtin = value
                                                                        productSearch = value
                                                                        showScanner = false
                                                                        selectedStore?.let { s -> searchStoreProducts(s.id) }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                    .addOnCompleteListener { imageProxy.close() }
                                            } else {
                                                imageProxy.close()
                                            }
                                        }

                                        try {
                                            cameraProvider.unbindAll()
                                            cameraProvider.bindToLifecycle(
                                                lifecycleOwner,
                                                CameraSelector.DEFAULT_BACK_CAMERA,
                                                preview, imageAnalysis
                                            )
                                        } catch (e: Exception) {
                                            Log.e("BrowseStoreScan", "Camera bind failed", e)
                                        }
                                    }, ContextCompat.getMainExecutor(ctx))
                                    previewView
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                            // Scan overlay
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(180.dp, 80.dp)
                                    .border(2.dp, WiseUpColors.Blue500, RoundedCornerShape(8.dp))
                            )
                            Text(
                                "Point at barcode for quick lookup",
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 8.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black.copy(alpha = 0.6f))
                                    .padding(horizontal = 10.dp, vertical = 3.dp),
                                color = Color.White,
                                fontSize = 12.sp
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                } else {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CameraAlt, null, tint = Color(0xFFFF9800))
                            Spacer(Modifier.width(8.dp))
                            Text("Camera permission required for scanning.", fontSize = 13.sp)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            if (isLoadingProducts) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = WiseUpColors.Blue500)
                }
            } else if (storePrices.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            if (hasSearchedProducts) Icons.Default.SearchOff else Icons.Default.Search,
                            null, Modifier.size(64.dp), tint = WiseUpColors.TextMuted
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (hasSearchedProducts) "No products match \"$productSearch\""
                            else "Search for products by GTIN or name",
                            color = WiseUpColors.TextSecondary
                        )
                        if (!hasSearchedProducts) {
                            Text("Or scan a barcode to find items", color = WiseUpColors.TextMuted, fontSize = 13.sp)
                        }
                    }
                }
            } else {
                val filtered = storePrices

                // Find cheapest price for highlighting
                val cheapestPrice = filtered.minOfOrNull { it.price }

                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered) { sp ->
                        val prodName = products[sp.productGtin]?.description ?: sp.productGtin
                        val isCheapest = sp.price == cheapestPrice && filtered.size > 1
                        val priceDiffers = sp.verified && sp.unverifiedPrice != null && sp.unverifiedPrice != sp.price

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isCheapest) Color(0xFFE8F8E8) else Color.White
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                // Product name + stock status
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(prodName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        Text("GTIN: ${sp.productGtin}", fontSize = 11.sp, color = WiseUpColors.TextMuted)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            if (sp.inStock) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                            null,
                                            modifier = Modifier.size(14.dp),
                                            tint = if (sp.inStock) WiseUpColors.Green600 else WiseUpColors.Red500
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            if (sp.inStock) "In Stock" else "Out of Stock",
                                            fontSize = 11.sp,
                                            color = if (sp.inStock) WiseUpColors.Green600 else WiseUpColors.Red500
                                        )
                                    }
                                }

                                Spacer(Modifier.height(8.dp))

                                // Price display
                                if (sp.verified) {
                                    // Verified price row
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        VerificationBadge(verified = true)
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            CurrencyProvider.formatPrice(sp.price),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp,
                                            color = WiseUpColors.Green600
                                        )
                                    }
                                    // Community price if different
                                    sp.unverifiedPrice?.let { uv ->
                                        Spacer(Modifier.height(4.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            VerificationBadge(verified = false)
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                CurrencyProvider.formatPrice(uv),
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 14.sp,
                                                color = WiseUpColors.Red500
                                            )
                                        }
                                    }
                                } else {
                                    // Only unverified/community price
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        VerificationBadge(verified = false)
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            CurrencyProvider.formatPrice(sp.unverifiedPrice ?: sp.price),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp,
                                            color = WiseUpColors.Red500
                                        )
                                    }
                                }

                                // Price conflict warning
                                if (priceDiffers) {
                                    Spacer(Modifier.height(6.dp))
                                    Card(
                                        shape = RoundedCornerShape(8.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Warning, null, Modifier.size(14.dp), tint = Color(0xFFFF9800))
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                "Price difference detected \u2014 you may be overpaying",
                                                fontSize = 11.sp,
                                                color = Color(0xFF6D4C00)
                                            )
                                        }
                                    }
                                }

                                Spacer(Modifier.height(10.dp))

                                // Action buttons
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { showAddToListDialog = sp },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = WiseUpColors.Blue500),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Icon(Icons.Default.AddShoppingCart, null, Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Add to List", fontSize = 12.sp)
                                    }
                                    OutlinedButton(
                                        onClick = { showUpdatePriceDialog = sp; newPriceInput = "" },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, null, Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            if (sp.verified) "Report Price" else "Update Price",
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}
