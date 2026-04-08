package com.example.myapplication.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.*
import com.example.myapplication.ui.theme.WiseUpColors
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComparePricesScreen() {
    val scope = rememberCoroutineScope()
    var lists by remember { mutableStateOf<List<ShoppingList>>(emptyList()) }
    var stores by remember { mutableStateOf<List<Store>>(emptyList()) }
    var storeHqs by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var selectedListId by remember { mutableStateOf<String?>(null) }
    var selectedStoreIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var results by remember { mutableStateOf<List<ComparisonResult>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isComparing by remember { mutableStateOf(false) }
    var showStoreSelector by remember { mutableStateOf(false) }
    var listExpanded by remember { mutableStateOf(false) }

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

    fun compare() {
        val listId = selectedListId ?: return
        if (selectedStoreIds.isEmpty()) return
        isComparing = true
        scope.launch {
            try {
                val listItems: List<ShoppingListItem> = SupabaseClient.client.from("shopping_list_items")
                    .select { filter { eq("shopping_list_id", listId) } }
                    .decodeList()
                val compResults = mutableListOf<ComparisonResult>()
                selectedStoreIds.forEach { storeId ->
                    val store = stores.find { it.id == storeId } ?: return@forEach
                    val prices: List<StorePrice> = SupabaseClient.client.from("store_prices")
                        .select { filter { eq("store_id", storeId) } }
                        .decodeList()
                    val priceMap = prices.associateBy { it.productGtin }
                    var totalInStock = 0.0
                    var totalAll = 0.0
                    var inStockCount = 0
                    var missingCount = 0
                    val resultPriceMap = mutableMapOf<String, StorePrice?>()
                    listItems.forEach { item ->
                        val sp = priceMap[item.productGtin]
                        resultPriceMap[item.productGtin] = sp
                        if (sp != null) {
                            val cost = sp.price * item.quantity
                            totalAll += cost
                            if (sp.inStock) {
                                totalInStock += cost
                                inStockCount++
                            } else {
                                missingCount++
                            }
                        } else {
                            missingCount++
                        }
                    }
                    compResults.add(
                        ComparisonResult(
                            store = store,
                            hqName = storeHqs[store.hqId] ?: "Unknown",
                            totalInStock = totalInStock,
                            totalAll = totalAll,
                            itemsInStock = inStockCount,
                            itemsMissing = missingCount,
                            priceMap = resultPriceMap
                        )
                    )
                }
                results = compResults.sortedBy { it.totalInStock }
            } catch (_: Exception) { }
            isComparing = false
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
                    colors = ButtonDefaults.buttonColors(containerColor = WiseUpColors.Green600)
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
                CircularProgressIndicator(color = WiseUpColors.Green600)
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

            // Store selector button
            OutlinedButton(
                onClick = { showStoreSelector = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Store, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (selectedStoreIds.isEmpty()) "Select stores to compare"
                    else "${selectedStoreIds.size} store(s) selected"
                )
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = { compare() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = WiseUpColors.Green600),
                shape = RoundedCornerShape(12.dp),
                enabled = selectedListId != null && selectedStoreIds.isNotEmpty() && !isComparing
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
                                    Column {
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
                                                        containerColor = WiseUpColors.Green600,
                                                        labelColor = Color.White
                                                    ),
                                                    modifier = Modifier.height(24.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column {
                                        Text("In-Stock Total", fontSize = 12.sp, color = WiseUpColors.TextMuted)
                                        Text("%.2f".format(result.totalInStock), fontWeight = FontWeight.Bold, color = WiseUpColors.Green600)
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
                            }
                        }
                    }
                }
            }
        }
    }
}
