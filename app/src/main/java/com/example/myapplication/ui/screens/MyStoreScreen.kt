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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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

data class StoreProductItem(
    val storePrice: StorePrice,
    val productDescription: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyStoreScreen(storeId: String, storeName: String) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var allItems by remember { mutableStateOf<List<StoreProductItem>>(emptyList()) }
    var filteredItems by remember { mutableStateOf<List<StoreProductItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var togglingId by remember { mutableStateOf<String?>(null) }
    var editingItemId by remember { mutableStateOf<String?>(null) }
    var editPriceInput by remember { mutableStateOf("") }
    var savingPriceId by remember { mutableStateOf<String?>(null) }

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

    // Load all products for this store
    fun loadProducts() {
        scope.launch {
            isLoading = true
            try {
                val prices: List<StorePrice> = SupabaseClient.client.from("store_prices")
                    .select { filter { eq("store_id", storeId) } }
                    .decodeList()

                val gtins = prices.map { it.productGtin }.distinct()
                val products = mutableMapOf<String, String>()
                if (gtins.isNotEmpty()) {
                    // Fetch in batches to avoid query limits
                    gtins.chunked(50).forEach { batch ->
                        val prods: List<Product> = SupabaseClient.client.from("products")
                            .select { filter { isIn("gtin", batch) } }
                            .decodeList()
                        prods.forEach { products[it.gtin] = it.description }
                    }
                }

                allItems = prices.map { sp ->
                    StoreProductItem(
                        storePrice = sp,
                        productDescription = products[sp.productGtin] ?: sp.productGtin
                    )
                }.sortedBy { it.productDescription.lowercase() }

                filteredItems = allItems
            } catch (_: Exception) { }
            isLoading = false
        }
    }

    LaunchedEffect(storeId) { loadProducts() }

    // Filter when search changes
    LaunchedEffect(searchQuery, allItems) {
        val q = searchQuery.trim().lowercase()
        filteredItems = if (q.isBlank()) allItems
        else allItems.filter {
            it.productDescription.lowercase().contains(q) ||
                    it.storePrice.productGtin.contains(q)
        }
    }

    // Handle barcode scan
    LaunchedEffect(scannedGtin) {
        scannedGtin?.let { gtin ->
            searchQuery = gtin
            showScanner = false
            scannedGtin = null
        }
    }

    // Save new price
    fun saveNewPrice(item: StoreProductItem) {
        val newPrice = editPriceInput.toDoubleOrNull()
        if (newPrice == null || newPrice <= 0 || newPrice > 999999.99) return
        savingPriceId = item.storePrice.id
        scope.launch {
            try {
                val userId = SupabaseClient.client.auth.currentUserOrNull()?.id ?: return@launch
                val upsert = StorePriceUpsert(
                    storeId = storeId,
                    productGtin = item.storePrice.productGtin,
                    price = newPrice,
                    inStock = item.storePrice.inStock,
                    verified = true,
                    verifiedBy = userId,
                    updatedBy = userId,
                    source = "store_owner",
                    unverifiedPrice = null
                )
                SupabaseClient.client.from("store_prices").upsert(upsert) {
                    onConflict = "store_id,product_gtin"
                }
                // Update local state
                allItems = allItems.map {
                    if (it.storePrice.id == item.storePrice.id) {
                        it.copy(storePrice = it.storePrice.copy(
                            price = newPrice, verified = true,
                            verifiedBy = userId, source = "store_owner"
                        ))
                    } else it
                }
                editingItemId = null
                editPriceInput = ""
            } catch (_: Exception) { }
            savingPriceId = null
        }
    }

    // Toggle stock status
    fun toggleStock(item: StoreProductItem) {
        togglingId = item.storePrice.id
        scope.launch {
            try {
                val newInStock = !item.storePrice.inStock
                SupabaseClient.client.from("store_prices").update(
                    mapOf("in_stock" to newInStock)
                ) {
                    filter { eq("id", item.storePrice.id) }
                }
                // Update local state
                allItems = allItems.map {
                    if (it.storePrice.id == item.storePrice.id) {
                        it.copy(storePrice = it.storePrice.copy(inStock = newInStock))
                    } else it
                }
            } catch (_: Exception) { }
            togglingId = null
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(storeName, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text(
                        "${allItems.size} products",
                        fontSize = 12.sp, color = WiseUpColors.TextSecondary
                    )
                }
                // Stock summary
                val inStockCount = allItems.count { it.storePrice.inStock }
                val outCount = allItems.size - inStockCount
                Column(horizontalAlignment = Alignment.End) {
                    Text("$inStockCount in stock", fontSize = 11.sp, color = WiseUpColors.Blue500, fontWeight = FontWeight.Medium)
                    if (outCount > 0) {
                        Text("$outCount out", fontSize = 11.sp, color = WiseUpColors.Red500)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

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
                singleLine = true
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

        // Barcode scanner
        if (showScanner && hasCameraPermission) {
            Spacer(Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth().height(200.dp),
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
                                                        barcode.rawValue?.let { scannedGtin = it }
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
                                    Log.e("MyStoreScreen", "Camera bind failed", e)
                                }
                            }, ContextCompat.getMainExecutor(ctx))
                            previewView
                        },
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(200.dp, 80.dp)
                            .border(2.dp, WiseUpColors.Blue500, RoundedCornerShape(8.dp))
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (isLoading) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = WiseUpColors.Blue500)
            }
        } else if (filteredItems.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Inventory, null, Modifier.size(48.dp), tint = WiseUpColors.TextMuted)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (searchQuery.isNotBlank()) "No products match \"$searchQuery\""
                        else "No products in this store yet",
                        color = WiseUpColors.TextMuted
                    )
                }
            }
        } else {
            Text(
                "${filteredItems.size} product${if (filteredItems.size != 1) "s" else ""}",
                fontSize = 13.sp, color = WiseUpColors.TextSecondary
            )
            Spacer(Modifier.height(6.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(filteredItems, key = { it.storePrice.id }) { item ->
                    val sp = item.storePrice
                    val isToggling = togglingId == sp.id

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (sp.inStock) Color.White else Color(0xFFFFF8F0)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Product info
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    item.productDescription,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp,
                                    maxLines = 2
                                )
                                Text(
                                    "GTIN: ${sp.productGtin}",
                                    fontSize = 11.sp,
                                    color = WiseUpColors.TextMuted
                                )
                                Spacer(Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        CurrencyProvider.formatPrice(sp.price),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = WiseUpColors.Blue500
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    IconButton(
                                        onClick = {
                                            if (editingItemId == sp.id) {
                                                editingItemId = null
                                            } else {
                                                editingItemId = sp.id
                                                editPriceInput = "%.2f".format(sp.price)
                                            }
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Edit, null,
                                            Modifier.size(14.dp),
                                            tint = WiseUpColors.Orange500
                                        )
                                    }
                                    Spacer(Modifier.width(4.dp))
                                    VerificationBadge(verified = sp.verified)
                                }
                                // Inline price editor
                                if (editingItemId == sp.id) {
                                    Spacer(Modifier.height(6.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = editPriceInput,
                                            onValueChange = { editPriceInput = it },
                                            label = { Text("New price", fontSize = 11.sp) },
                                            modifier = Modifier.weight(1f).height(52.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
                                        )
                                        val isSaving = savingPriceId == sp.id
                                        Button(
                                            onClick = { saveNewPrice(item) },
                                            enabled = !isSaving && editPriceInput.isNotBlank(),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = WiseUpColors.Blue500),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                            modifier = Modifier.height(40.dp)
                                        ) {
                                            if (isSaving) {
                                                CircularProgressIndicator(Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                                            } else {
                                                Icon(Icons.Default.Save, null, Modifier.size(14.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Save", fontSize = 12.sp)
                                            }
                                        }
                                        IconButton(
                                            onClick = { editingItemId = null },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Close, null, Modifier.size(16.dp), tint = WiseUpColors.TextMuted)
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.width(8.dp))

                            // Stock toggle
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (isToggling) {
                                    CircularProgressIndicator(
                                        Modifier.size(24.dp),
                                        color = WiseUpColors.Blue500,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Switch(
                                        checked = sp.inStock,
                                        onCheckedChange = { toggleStock(item) },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = WiseUpColors.Blue500,
                                            uncheckedThumbColor = Color.White,
                                            uncheckedTrackColor = WiseUpColors.Red500.copy(alpha = 0.5f)
                                        )
                                    )
                                }
                                Text(
                                    if (sp.inStock) "In Stock" else "Out",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (sp.inStock) WiseUpColors.Blue500 else WiseUpColors.Red500
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
