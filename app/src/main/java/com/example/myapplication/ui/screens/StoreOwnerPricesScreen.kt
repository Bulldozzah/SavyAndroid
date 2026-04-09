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
fun StoreOwnerPricesScreen(storeId: String, storeName: String) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<Product>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    // Selected product state
    var selectedProduct by remember { mutableStateOf<Product?>(null) }
    var existingPrice by remember { mutableStateOf<StorePrice?>(null) }
    var newPriceInput by remember { mutableStateOf("") }
    var inStock by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var saveMessage by remember { mutableStateOf<String?>(null) }

    // Barcode scanner state
    var showScanner by remember { mutableStateOf(false) }
    var scannedGtin by remember { mutableStateOf<String?>(null) }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (granted) showScanner = true
    }

    // Load product + existing price when a product is selected
    fun loadProductPrice(product: Product) {
        selectedProduct = product
        newPriceInput = ""
        inStock = true
        saveMessage = null
        scope.launch {
            try {
                val prices: List<StorePrice> = SupabaseClient.client.from("store_prices")
                    .select {
                        filter {
                            eq("store_id", storeId)
                            eq("product_gtin", product.gtin)
                        }
                    }
                    .decodeList()
                existingPrice = prices.firstOrNull()
                existingPrice?.let {
                    newPriceInput = "%.2f".format(it.price)
                    inStock = it.inStock
                }
            } catch (_: Exception) {
                existingPrice = null
            }
        }
    }

    // Search products
    fun searchProducts() {
        if (searchQuery.isBlank()) return
        isSearching = true
        scope.launch {
            try {
                val q = searchQuery.trim()
                searchResults = if (q.all { it.isDigit() }) {
                    SupabaseClient.client.from("products")
                        .select { filter { eq("gtin", q) } }
                        .decodeList()
                } else {
                    SupabaseClient.client.from("products")
                        .select { filter { ilike("description", "%$q%") } }
                        .decodeList<Product>().take(20)
                }
            } catch (_: Exception) {
                searchResults = emptyList()
            }
            isSearching = false
        }
    }

    // Save price update (verified, store_owner source)
    fun savePrice() {
        val product = selectedProduct ?: return
        val price = newPriceInput.toDoubleOrNull()
        if (price == null || price <= 0 || price > 999999.99) {
            saveMessage = "Enter a valid price (0.01 - 999,999.99)"
            return
        }
        isSaving = true
        saveMessage = null
        scope.launch {
            try {
                val userId = SupabaseClient.client.auth.currentUserOrNull()?.id ?: return@launch
                val upsert = StorePriceUpsert(
                    storeId = storeId,
                    productGtin = product.gtin,
                    price = price,
                    inStock = inStock,
                    verified = true,
                    verifiedBy = userId,
                    updatedBy = userId,
                    source = "store_owner",
                    unverifiedPrice = null
                )
                SupabaseClient.client.from("store_prices").upsert(upsert) {
                    onConflict = "store_id,product_gtin"
                }
                saveMessage = "Price updated successfully!"
                // Reload
                val prices: List<StorePrice> = SupabaseClient.client.from("store_prices")
                    .select {
                        filter {
                            eq("store_id", storeId)
                            eq("product_gtin", product.gtin)
                        }
                    }
                    .decodeList()
                existingPrice = prices.firstOrNull()
            } catch (e: Exception) {
                saveMessage = "Error: ${e.message}"
            }
            isSaving = false
        }
    }

    // Handle barcode scan result
    LaunchedEffect(scannedGtin) {
        scannedGtin?.let { gtin ->
            searchQuery = gtin
            showScanner = false
            isSearching = true
            try {
                val products: List<Product> = SupabaseClient.client.from("products")
                    .select { filter { eq("gtin", gtin) } }
                    .decodeList()
                searchResults = products
                if (products.size == 1) {
                    loadProductPrice(products.first())
                }
            } catch (_: Exception) {
                searchResults = emptyList()
            }
            isSearching = false
            scannedGtin = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Store info card
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
                Text(storeName, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
        }

        Spacer(Modifier.height(16.dp))

        // Search bar + scan button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search GTIN or product name") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions.Default
            )
            IconButton(
                onClick = {
                    if (hasCameraPermission) showScanner = !showScanner
                    else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            ) {
                Icon(
                    Icons.Default.QrCodeScanner,
                    contentDescription = "Scan barcode",
                    tint = WiseUpColors.Blue500,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = { searchProducts() },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = WiseUpColors.Blue500),
            enabled = searchQuery.isNotBlank() && !isSearching
        ) {
            if (isSearching) {
                CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Search, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Search")
            }
        }

        // Barcode scanner
        if (showScanner && hasCameraPermission) {
            Spacer(Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth().height(250.dp),
                shape = RoundedCornerShape(12.dp)
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
                                val analyzer = ImageAnalysis.Builder()
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()
                                val scanner = BarcodeScanning.getClient()
                                analyzer.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                                    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
                                    val mediaImage = imageProxy.image
                                    if (mediaImage != null) {
                                        val image = InputImage.fromMediaImage(
                                            mediaImage, imageProxy.imageInfo.rotationDegrees
                                        )
                                        scanner.process(image)
                                            .addOnSuccessListener { barcodes ->
                                                for (barcode in barcodes) {
                                                    if (barcode.valueType == Barcode.TYPE_PRODUCT ||
                                                        barcode.format == Barcode.FORMAT_EAN_13 ||
                                                        barcode.format == Barcode.FORMAT_EAN_8 ||
                                                        barcode.format == Barcode.FORMAT_UPC_A ||
                                                        barcode.format == Barcode.FORMAT_UPC_E
                                                    ) {
                                                        barcode.rawValue?.let { value ->
                                                            scannedGtin = value
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
                                        lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA,
                                        preview, analyzer
                                    )
                                } catch (e: Exception) {
                                    Log.e("StoreOwnerPrices", "Camera bind failed", e)
                                }
                            }, ContextCompat.getMainExecutor(ctx))
                            previewView
                        },
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
                    )
                    // Scan overlay
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(200.dp, 100.dp)
                            .border(2.dp, WiseUpColors.Blue500, RoundedCornerShape(8.dp))
                    )
                    Text(
                        "Point at barcode",
                        modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // Search results
        if (searchResults.isNotEmpty() && selectedProduct == null) {
            Spacer(Modifier.height(12.dp))
            Text("Search Results", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
            searchResults.forEach { product ->
                Card(
                    onClick = { loadProductPrice(product) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    shape = RoundedCornerShape(10.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Inventory, null, tint = WiseUpColors.Blue500, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(product.description, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            Text("GTIN: ${product.gtin}", fontSize = 11.sp, color = WiseUpColors.TextMuted)
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = WiseUpColors.TextMuted)
                    }
                }
            }
        }

        // Selected product — price editor
        selectedProduct?.let { product ->
            Spacer(Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Update Price", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        IconButton(onClick = {
                            selectedProduct = null
                            existingPrice = null
                            saveMessage = null
                        }) {
                            Icon(Icons.Default.Close, null)
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Text(product.description, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text("GTIN: ${product.gtin}", fontSize = 12.sp, color = WiseUpColors.TextMuted)

                    Spacer(Modifier.height(12.dp))

                    // Current price display
                    existingPrice?.let { ep ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (ep.verified) Color(0xFFE8F8E8) else Color(0xFFFFF3E0)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Current Price", fontSize = 12.sp, color = WiseUpColors.TextMuted)
                                    Text(
                                        CurrencyProvider.formatPrice(ep.price),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                    Text(
                                        "Source: ${ep.source ?: "unknown"}",
                                        fontSize = 11.sp,
                                        color = WiseUpColors.TextMuted
                                    )
                                    Text(
                                        if (ep.inStock) "In Stock" else "Out of Stock",
                                        fontSize = 11.sp,
                                        color = if (ep.inStock) WiseUpColors.Blue500 else WiseUpColors.Red500,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                VerificationBadge(verified = ep.verified)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    // New price input
                    OutlinedTextField(
                        value = newPriceInput,
                        onValueChange = { newPriceInput = it },
                        label = { Text("New Price") },
                        leadingIcon = { Icon(Icons.Default.AttachMoney, null) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )

                    Spacer(Modifier.height(12.dp))

                    // Stock status toggle
                    Text("Stock Status", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FilterChip(
                            selected = inStock,
                            onClick = { inStock = true },
                            label = { Text("In Stock") },
                            leadingIcon = if (inStock) {
                                { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = WiseUpColors.Blue500,
                                selectedLabelColor = Color.White,
                                selectedLeadingIconColor = Color.White
                            )
                        )
                        FilterChip(
                            selected = !inStock,
                            onClick = { inStock = false },
                            label = { Text("Out of Stock") },
                            leadingIcon = if (!inStock) {
                                { Icon(Icons.Default.Close, null, Modifier.size(16.dp)) }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = WiseUpColors.Red500,
                                selectedLabelColor = Color.White,
                                selectedLeadingIconColor = Color.White
                            )
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    // Info chip
                    AssistChip(
                        onClick = {},
                        label = { Text("Price will be saved as verified (store owner)", fontSize = 11.sp) },
                        leadingIcon = { Icon(Icons.Default.Verified, null, Modifier.size(14.dp)) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = Color(0xFFE8F8E8),
                            labelColor = WiseUpColors.Green600,
                            leadingIconContentColor = WiseUpColors.Green600
                        ),
                        modifier = Modifier.height(28.dp)
                    )

                    Spacer(Modifier.height(12.dp))

                    // Save button
                    Button(
                        onClick = { savePrice() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = WiseUpColors.Blue500),
                        enabled = !isSaving && newPriceInput.isNotBlank()
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Save, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Save Price")
                        }
                    }

                    // Save message
                    saveMessage?.let { msg ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            msg,
                            fontSize = 13.sp,
                            color = if (msg.startsWith("Error")) WiseUpColors.Red500 else WiseUpColors.Blue500,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
