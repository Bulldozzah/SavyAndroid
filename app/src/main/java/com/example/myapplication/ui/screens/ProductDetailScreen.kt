package com.example.myapplication.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.*
import com.example.myapplication.ui.components.VerificationBadge
import com.example.myapplication.ui.theme.WiseUpColors
import io.github.jan.supabase.postgrest.from

@Composable
fun ProductDetailScreen(productGtin: String) {
    var product by remember { mutableStateOf<Product?>(null) }
    var storePrices by remember { mutableStateOf<List<StorePrice>>(emptyList()) }
    var stores by remember { mutableStateOf<Map<String, Store>>(emptyMap()) }
    var storeHqs by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(productGtin) {
        try {
            // Load product
            val products: List<Product> = SupabaseClient.client.from("products")
                .select { filter { eq("gtin", productGtin) } }
                .decodeList()
            product = products.firstOrNull()

            // Load all prices for this product
            storePrices = SupabaseClient.client.from("store_prices")
                .select { filter { eq("product_gtin", productGtin) } }
                .decodeList()

            // Load stores
            val allStores: List<Store> = SupabaseClient.client.from("stores").select().decodeList()
            stores = allStores.associateBy { it.id }

            // Load store HQs
            val hqs: List<StoreHq> = SupabaseClient.client.from("store_hq").select().decodeList()
            storeHqs = hqs.associate { it.id to it.name }
        } catch (_: Exception) { }
        isLoading = false
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = WiseUpColors.Green600)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Product header
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.ShoppingBag,
                            contentDescription = null,
                            tint = WiseUpColors.Green600,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                product?.description ?: "Unknown Product",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Text(
                                "GTIN: $productGtin",
                                fontSize = 12.sp,
                                color = WiseUpColors.TextMuted
                            )
                        }
                    }
                }
            }
        }

        // Section header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Store Prices",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text(
                    "${storePrices.size} stores",
                    fontSize = 13.sp,
                    color = WiseUpColors.TextMuted
                )
            }
        }

        if (storePrices.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.SearchOff,
                            null,
                            Modifier.size(48.dp),
                            tint = WiseUpColors.TextMuted
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "No price data yet",
                            color = WiseUpColors.TextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "Be the first to scan and submit a price!",
                            fontSize = 13.sp,
                            color = WiseUpColors.TextMuted
                        )
                    }
                }
            }
        }

        // Price cards sorted: cheapest first
        val sorted = storePrices.sortedBy { it.price }
        items(sorted) { sp ->
            val store = stores[sp.storeId]
            val hqName = store?.let { storeHqs[it.hqId] } ?: "Unknown"
            val location = store?.location ?: ""
            val isCheapest = sp == sorted.firstOrNull()

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isCheapest) Color(0xFFE8F8E8) else Color.White
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
                                Text(hqName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                if (isCheapest) {
                                    Spacer(Modifier.width(8.dp))
                                    AssistChip(
                                        onClick = {},
                                        label = { Text("Cheapest", fontSize = 10.sp) },
                                        colors = AssistChipDefaults.assistChipColors(
                                            containerColor = WiseUpColors.Green100,
                                            labelColor = WiseUpColors.Green600
                                        ),
                                        modifier = Modifier.height(22.dp)
                                    )
                                }
                            }
                            if (location.isNotBlank()) {
                                Text(location, fontSize = 12.sp, color = WiseUpColors.TextSecondary)
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "%.2f".format(sp.price),
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = if (isCheapest) WiseUpColors.Green600 else WiseUpColors.TextPrimary
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        VerificationBadge(verified = sp.verified)

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (sp.inStock) "In Stock" else "Out of Stock",
                                fontSize = 11.sp,
                                color = if (sp.inStock) WiseUpColors.Green600 else WiseUpColors.Red500
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                if (sp.inStock) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                null,
                                modifier = Modifier.size(14.dp),
                                tint = if (sp.inStock) WiseUpColors.Green600 else WiseUpColors.Red500
                            )
                        }
                    }

                    sp.source?.let { source ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Updated by: ${source.replaceFirstChar { it.uppercase() }}",
                            fontSize = 11.sp,
                            color = WiseUpColors.TextMuted
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}
