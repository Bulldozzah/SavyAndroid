package com.example.myapplication.ui.screens

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
fun BarcodeScannerScreen(onNavigateToProduct: ((String) -> Unit)? = null) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    // Camera permission
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

    // State
    var scannedGtin by remember { mutableStateOf("") }
    var manualInput by remember { mutableStateOf("") }
    var isSearchingByName by remember { mutableStateOf(false) }
    var foundProduct by remember { mutableStateOf<Product?>(null) }
    var searchResults by remember { mutableStateOf<List<Product>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var productConfirmed by remember { mutableStateOf(false) }

    var stores by remember { mutableStateOf<List<Store>>(emptyList()) }
    var storeHqs by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var selectedStoreId by remember { mutableStateOf<String?>(null) }
    var storeExpanded by remember { mutableStateOf(false) }

    var priceInput by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var successMsg by remember { mutableStateOf<String?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    var existingPrice by remember { mutableStateOf<StorePrice?>(null) }
    var isScannerActive by remember { mutableStateOf(true) }

    // Load stores
    LaunchedEffect(Unit) {
        try {
            stores = SupabaseClient.client.from("stores").select().decodeList()
            val hqs: List<StoreHq> = SupabaseClient.client.from("store_hq").select().decodeList()
            storeHqs = hqs.associate { it.id to it.name }
        } catch (_: Exception) { }
    }

    // Look up product when GTIN changes
    LaunchedEffect(scannedGtin) {
        if (scannedGtin.isNotBlank()) {
            isSearching = true
            try {
                val products: List<Product> = SupabaseClient.client.from("products")
                    .select { filter { eq("gtin", scannedGtin) } }
                    .decodeList()
                foundProduct = products.firstOrNull()
            } catch (_: Exception) {
                foundProduct = null
            }
            isSearching = false
        }
    }

    // Look up existing price when store + product selected
    LaunchedEffect(selectedStoreId, scannedGtin) {
        existingPrice = null
        val storeId = selectedStoreId ?: return@LaunchedEffect
        if (scannedGtin.isBlank()) return@LaunchedEffect
        try {
            val prices: List<StorePrice> = SupabaseClient.client.from("store_prices")
                .select {
                    filter {
                        eq("store_id", storeId)
                        eq("product_gtin", scannedGtin)
                    }
                }
                .decodeList()
            existingPrice = prices.firstOrNull()
        } catch (_: Exception) { }
    }

    fun submitPrice() {
        val storeId = selectedStoreId ?: return
        if (scannedGtin.isBlank() || priceInput.isBlank()) return
        val priceVal = priceInput.toDoubleOrNull() ?: return
        isSubmitting = true
        successMsg = null; errorMsg = null
        scope.launch {
            try {
                val userId = SupabaseClient.client.auth.currentUserOrNull()?.id ?: return@launch
                SupabaseClient.client.from("store_prices").upsert(
                    StorePriceUpsert(
                        storeId = storeId,
                        productGtin = scannedGtin,
                        price = priceVal,
                        inStock = true,
                        verified = false,
                        updatedBy = userId,
                        source = "shopper"
                    )
                ) {
                    onConflict = "store_id,product_gtin"
                }
                successMsg = "Price submitted successfully!"
                priceInput = ""
                // Refresh existing price
                val prices: List<StorePrice> = SupabaseClient.client.from("store_prices")
                    .select {
                        filter {
                            eq("store_id", storeId)
                            eq("product_gtin", scannedGtin)
                        }
                    }
                    .decodeList()
                existingPrice = prices.firstOrNull()
            } catch (e: Exception) {
                errorMsg = e.message ?: "Failed to submit price"
            } finally {
                isSubmitting = false
            }
        }
    }

    fun searchByName(query: String) {
        if (query.isBlank()) return
        isSearching = true
        searchResults = emptyList()
        scope.launch {
            try {
                searchResults = SupabaseClient.client.from("products")
                    .select { filter { ilike("description", "%$query%") } }
                    .decodeList()
            } catch (_: Exception) { }
            isSearching = false
        }
    }

    fun resetScan() {
        scannedGtin = ""
        manualInput = ""
        foundProduct = null
        searchResults = emptyList()
        productConfirmed = false
        existingPrice = null
        priceInput = ""
        successMsg = null
        errorMsg = null
        isScannerActive = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Mode toggle: Scan vs Manual
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = !isSearchingByName,
                onClick = { isSearchingByName = false; resetScan() },
                label = { Text("Scan / GTIN") },
                leadingIcon = {
                    Icon(Icons.Default.CameraAlt, null, Modifier.size(16.dp))
                }
            )
            FilterChip(
                selected = isSearchingByName,
                onClick = { isSearchingByName = true; resetScan() },
                label = { Text("Search by Name") },
                leadingIcon = {
                    Icon(Icons.Default.Search, null, Modifier.size(16.dp))
                }
            )
        }

        Spacer(Modifier.height(12.dp))

        if (!isSearchingByName) {
            // === SCAN / GTIN MODE ===

            // Camera Preview
            if (hasCameraPermission && isScannerActive && scannedGtin.isBlank()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp),
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
                                                mediaImage,
                                                imageProxy.imageInfo.rotationDegrees
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
                                                                if (scannedGtin.isBlank()) {
                                                                    scannedGtin = value
                                                                    isScannerActive = false
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                                .addOnCompleteListener {
                                                    imageProxy.close()
                                                }
                                        } else {
                                            imageProxy.close()
                                        }
                                    }

                                    try {
                                        cameraProvider.unbindAll()
                                        cameraProvider.bindToLifecycle(
                                            lifecycleOwner,
                                            CameraSelector.DEFAULT_BACK_CAMERA,
                                            preview,
                                            imageAnalysis
                                        )
                                    } catch (e: Exception) {
                                        Log.e("BarcodeScan", "Camera binding failed", e)
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
                                .size(200.dp, 100.dp)
                                .border(2.dp, WiseUpColors.Green600, RoundedCornerShape(8.dp))
                        )

                        Text(
                            "Point camera at barcode",
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 12.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.6f))
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            color = Color.White,
                            fontSize = 13.sp
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            } else if (!hasCameraPermission) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CameraAlt, null, tint = Color(0xFFFF9800))
                        Spacer(Modifier.width(12.dp))
                        Text("Camera permission required for scanning. You can still enter GTIN manually below.", fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // Manual GTIN input
            OutlinedTextField(
                value = if (scannedGtin.isNotBlank()) scannedGtin else manualInput,
                onValueChange = {
                    if (scannedGtin.isBlank()) {
                        manualInput = it
                    }
                },
                label = { Text("Barcode / GTIN") },
                leadingIcon = { Icon(Icons.Default.QrCodeScanner, null) },
                trailingIcon = {
                    if (scannedGtin.isNotBlank()) {
                        IconButton(onClick = { resetScan() }) {
                            Icon(Icons.Default.Close, null)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                readOnly = scannedGtin.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            if (scannedGtin.isBlank() && manualInput.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { scannedGtin = manualInput; isScannerActive = false },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = WiseUpColors.Green600),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Search, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Look Up Product")
                }
            }

        } else {
            // === SEARCH BY NAME MODE ===
            OutlinedTextField(
                value = manualInput,
                onValueChange = { manualInput = it },
                label = { Text("Product Name") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { searchByName(manualInput) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = WiseUpColors.Green600),
                shape = RoundedCornerShape(12.dp),
                enabled = manualInput.isNotBlank() && !isSearching
            ) {
                if (isSearching) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Search, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Search Products")
                }
            }

            // Search results
            if (searchResults.isNotEmpty() && !productConfirmed) {
                Spacer(Modifier.height(12.dp))
                Text("Results (${searchResults.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                searchResults.take(10).forEach { product ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(product.description, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                Text("GTIN: ${product.gtin}", fontSize = 11.sp, color = WiseUpColors.TextMuted)
                            }
                            Button(
                                onClick = {
                                    scannedGtin = product.gtin
                                    foundProduct = product
                                    productConfirmed = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = WiseUpColors.Green600),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Select", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // === DETECTED PRODUCT CARD ===
        if (scannedGtin.isNotBlank()) {
            Spacer(Modifier.height(16.dp))

            if (isSearching) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = WiseUpColors.Green600, modifier = Modifier.size(32.dp))
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Detected Product", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(Modifier.height(8.dp))

                        if (foundProduct != null) {
                            Text(foundProduct!!.description, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text("GTIN: ${foundProduct!!.gtin}", fontSize = 12.sp, color = WiseUpColors.TextMuted)

                            if (!productConfirmed) {
                                Spacer(Modifier.height(8.dp))
                                Text("Is this the correct item?", fontSize = 13.sp, color = WiseUpColors.TextSecondary)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { productConfirmed = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = WiseUpColors.Green600),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Yes")
                                    }
                                    OutlinedButton(
                                        onClick = { resetScan() },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.Close, null, Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("No, scan again")
                                    }
                                }
                            }
                        } else {
                            Text("Product not found in database", color = WiseUpColors.Red500, fontSize = 14.sp)
                            Text("GTIN: $scannedGtin", fontSize = 12.sp, color = WiseUpColors.TextMuted)
                            Spacer(Modifier.height(4.dp))
                            Text("You can still submit a price for this barcode.", fontSize = 12.sp, color = WiseUpColors.TextSecondary)
                            productConfirmed = true
                        }
                    }
                }
            }
        }

        // === STORE SELECTOR + PRICE INPUT (only after product confirmed) ===
        if (productConfirmed && scannedGtin.isNotBlank()) {
            Spacer(Modifier.height(16.dp))

            // Existing price info
            existingPrice?.let { ep ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (ep.verified) Color(0xFFE8F8E8) else Color(0xFFFFF3E0)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Current Price", fontSize = 12.sp, color = WiseUpColors.TextMuted)
                            Text("%.2f".format(ep.price), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text(
                                "Source: ${ep.source ?: "unknown"}",
                                fontSize = 11.sp,
                                color = WiseUpColors.TextMuted
                            )
                        }
                        VerificationBadge(verified = ep.verified)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // Store selector
            Text("Select Store", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            ExposedDropdownMenuBox(
                expanded = storeExpanded,
                onExpandedChange = { storeExpanded = !storeExpanded }
            ) {
                OutlinedTextField(
                    value = selectedStoreId?.let { id ->
                        val store = stores.find { it.id == id }
                        store?.let { "${storeHqs[it.hqId] ?: "Unknown"} - ${it.location}" } ?: ""
                    } ?: "",
                    onValueChange = {},
                    readOnly = true,
                    placeholder = { Text("Choose the store you're in") },
                    leadingIcon = { Icon(Icons.Default.Store, null) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = storeExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    shape = RoundedCornerShape(12.dp)
                )
                ExposedDropdownMenu(
                    expanded = storeExpanded,
                    onDismissRequest = { storeExpanded = false }
                ) {
                    stores.forEach { store ->
                        DropdownMenuItem(
                            text = { Text("${storeHqs[store.hqId] ?: "Unknown"} - ${store.location}") },
                            onClick = {
                                selectedStoreId = store.id
                                storeExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Price input
            OutlinedTextField(
                value = priceInput,
                onValueChange = { priceInput = it },
                label = { Text("Price") },
                leadingIcon = { Icon(Icons.Default.AttachMoney, null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(Modifier.height(16.dp))

            // Submit button
            Button(
                onClick = { submitPrice() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WiseUpColors.Green600),
                shape = RoundedCornerShape(12.dp),
                enabled = selectedStoreId != null && priceInput.isNotBlank() && !isSubmitting
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Submit Price", fontSize = 16.sp)
                }
            }

            // Messages
            successMsg?.let {
                Spacer(Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F8E8))
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = WiseUpColors.Green600)
                        Spacer(Modifier.width(8.dp))
                        Text(it, color = WiseUpColors.Green600, fontWeight = FontWeight.Medium)
                    }
                }
            }

            errorMsg?.let {
                Spacer(Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFDE8E7))
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Error, null, tint = WiseUpColors.Red500)
                        Spacer(Modifier.width(8.dp))
                        Text(it, color = WiseUpColors.Red500, fontSize = 13.sp)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Scan another
            OutlinedButton(
                onClick = { resetScan() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.CameraAlt, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Scan Another Item")
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}
