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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.myapplication.data.*
import com.example.myapplication.ui.theme.WiseUpColors
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@Composable
fun SearchProductsScreen() {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Product>>(emptyList()) }
    var lists by remember { mutableStateOf<List<ShoppingList>>(emptyList()) }
    var selectedListId by remember { mutableStateOf<String?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    var showListPicker by remember { mutableStateOf(false) }
    var pendingProduct by remember { mutableStateOf<Product?>(null) }
    var quantity by remember { mutableIntStateOf(1) }
    var successMsg by remember { mutableStateOf<String?>(null) }
    var showScanner by remember { mutableStateOf(false) }
    var scannerGtin by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        try {
            val userId = SupabaseClient.client.auth.currentUserOrNull()?.id ?: return@LaunchedEffect
            lists = SupabaseClient.client.from("shopping_lists")
                .select { filter { eq("user_id", userId) } }
                .decodeList()
            if (lists.isNotEmpty()) selectedListId = lists.first().id
        } catch (_: Exception) { }
    }

    fun search(searchQuery: String = query) {
        if (searchQuery.isBlank()) return
        isSearching = true
        scope.launch {
            try {
                // Try GTIN exact match first, then description search
                val byGtin: List<Product> = SupabaseClient.client.from("products")
                    .select { filter { eq("gtin", searchQuery) } }
                    .decodeList()
                results = byGtin.ifEmpty {
                    SupabaseClient.client.from("products")
                        .select { filter { ilike("description", "%$searchQuery%") } }
                        .decodeList()
                }
            } catch (_: Exception) { results = emptyList() }
            isSearching = false
        }
    }

    // List picker dialog
    if (showListPicker && pendingProduct != null) {
        AlertDialog(
            onDismissRequest = { showListPicker = false; pendingProduct = null },
            title = { Text("Add to List") },
            text = {
                Column {
                    Text("Product: ${pendingProduct!!.description}", fontSize = 14.sp, color = WiseUpColors.TextSecondary)
                    Spacer(Modifier.height(12.dp))
                    Text("Select list:", fontWeight = FontWeight.Medium)
                    lists.forEach { list ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedListId == list.id,
                                onClick = { selectedListId = list.id }
                            )
                            Text(list.name, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Quantity: ")
                        IconButton(onClick = { if (quantity > 1) quantity-- }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Remove, null)
                        }
                        Text("$quantity", fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
                        IconButton(onClick = { quantity++ }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Add, null)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val listId = selectedListId ?: return@Button
                        val prod = pendingProduct ?: return@Button
                        scope.launch {
                            try {
                                SupabaseClient.client.from("shopping_list_items")
                                    .insert(ShoppingListItemCreate(listId, prod.gtin, quantity))
                                successMsg = "${prod.description} added!"
                                showListPicker = false; pendingProduct = null; quantity = 1
                            } catch (_: Exception) { }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WiseUpColors.Green600),
                    enabled = selectedListId != null
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showListPicker = false; pendingProduct = null }) { Text("Cancel") }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Search & Add Products", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        // Search bar + Scan button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; scannerGtin = "" },
                placeholder = { Text("Search by name or GTIN...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { query = ""; scannerGtin = ""; results = emptyList() }) {
                            Icon(Icons.Default.Clear, null)
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { search() })
            )
            FilledTonalButton(
                onClick = {
                    showScanner = !showScanner
                    if (!showScanner) { scannerGtin = "" }
                },
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(12.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (showScanner) WiseUpColors.Green600 else WiseUpColors.Green100
                ),
                modifier = Modifier.height(56.dp)
            ) {
                Icon(
                    Icons.Default.QrCodeScanner,
                    contentDescription = "Scan barcode",
                    tint = if (showScanner) Color.White else WiseUpColors.Green600
                )
            }
        }

        // Inline barcode scanner
        if (showScanner) {
            Spacer(Modifier.height(8.dp))
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
                                    val barcodeScanner = BarcodeScanning.getClient()
                                    val executor = Executors.newSingleThreadExecutor()

                                    imageAnalysis.setAnalyzer(executor) { imageProxy ->
                                        @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
                                        val mediaImage = imageProxy.image
                                        if (mediaImage != null) {
                                            val inputImage = InputImage.fromMediaImage(
                                                mediaImage, imageProxy.imageInfo.rotationDegrees
                                            )
                                            barcodeScanner.process(inputImage)
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
                                                                    query = value
                                                                    showScanner = false
                                                                    search(value)
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
                                        Log.e("SearchScan", "Camera bind failed", e)
                                    }
                                }, ContextCompat.getMainExecutor(ctx))
                                previewView
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(180.dp, 80.dp)
                                .border(2.dp, WiseUpColors.Green600, RoundedCornerShape(8.dp))
                        )
                        Text(
                            "Point at barcode to search product",
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
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CameraAlt, null, tint = Color(0xFFFF9800))
                        Spacer(Modifier.width(8.dp))
                        Text("Camera permission required for scanning.", fontSize = 13.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = { search() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = WiseUpColors.Green600),
            shape = RoundedCornerShape(12.dp),
            enabled = query.isNotBlank() && !isSearching
        ) {
            if (isSearching) {
                CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Text("Search")
            }
        }

        successMsg?.let {
            Spacer(Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = WiseUpColors.Green100),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null, tint = WiseUpColors.Green600)
                    Spacer(Modifier.width(8.dp))
                    Text(it, color = WiseUpColors.Green600, fontWeight = FontWeight.Medium)
                }
            }
            LaunchedEffect(it) {
                kotlinx.coroutines.delay(3000)
                successMsg = null
            }
        }

        Spacer(Modifier.height(16.dp))

        if (results.isEmpty() && !isSearching && query.isNotBlank()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No products found", color = WiseUpColors.TextSecondary)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(results) { product ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(product.description, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                Text("GTIN: ${product.gtin}", fontSize = 12.sp, color = WiseUpColors.TextMuted)
                            }
                            Button(
                                onClick = {
                                    if (lists.isEmpty()) return@Button
                                    pendingProduct = product
                                    quantity = 1
                                    showListPicker = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = WiseUpColors.Green600),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Add", fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
