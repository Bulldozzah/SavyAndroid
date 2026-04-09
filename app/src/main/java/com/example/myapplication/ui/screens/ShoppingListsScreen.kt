package com.example.myapplication.ui.screens

import android.content.Intent

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.*
import com.example.myapplication.ui.components.VerificationBadge
import com.example.myapplication.ui.theme.WiseUpColors
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListsScreen() {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var lists by remember { mutableStateOf<List<ShoppingList>>(emptyList()) }
    var items by remember { mutableStateOf<Map<String, List<ShoppingListItem>>>(emptyMap()) }
    var products by remember { mutableStateOf<Map<String, Product>>(emptyMap()) }
    var stores by remember { mutableStateOf<List<Store>>(emptyList()) }
    var storeHqs by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var storePrices by remember { mutableStateOf<Map<String, List<StorePrice>>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<String?>(null) }
    var showStoreDialog by remember { mutableStateOf<String?>(null) }
    var showBudgetDialog by remember { mutableStateOf<String?>(null) }
    var newListName by remember { mutableStateOf("") }
    var newBudget by remember { mutableStateOf("") }
    var selectedListId by remember { mutableStateOf<String?>(null) }
    var expandedListId by remember { mutableStateOf<String?>(null) }

    fun loadData() {
        scope.launch {
            try {
                val userId = SupabaseClient.client.auth.currentUserOrNull()?.id ?: return@launch
                lists = SupabaseClient.client.from("shopping_lists")
                    .select { filter { eq("user_id", userId) } }
                    .decodeList<ShoppingList>()
                val allItems = mutableMapOf<String, List<ShoppingListItem>>()
                val allGtins = mutableSetOf<String>()
                lists.forEach { list ->
                    val listItems: List<ShoppingListItem> = SupabaseClient.client.from("shopping_list_items")
                        .select { filter { eq("shopping_list_id", list.id) } }
                        .decodeList<ShoppingListItem>()
                    allItems[list.id] = listItems
                    allGtins.addAll(listItems.map { it.productGtin })
                }
                items = allItems
                if (allGtins.isNotEmpty()) {
                    val prods: List<Product> = SupabaseClient.client.from("products")
                        .select { filter { isIn("gtin", allGtins.toList()) } }
                        .decodeList()
                    products = prods.associateBy { it.gtin }
                }
                stores = SupabaseClient.client.from("stores").select().decodeList()
                val hqs: List<StoreHq> = SupabaseClient.client.from("store_hq").select().decodeList()
                storeHqs = hqs.associate { it.id to it.name }
                val assignedIds = lists.mapNotNull { it.assignedStoreId }.distinct()
                val priceMap = mutableMapOf<String, List<StorePrice>>()
                assignedIds.forEach { storeId ->
                    val prices: List<StorePrice> = SupabaseClient.client.from("store_prices")
                        .select { filter { eq("store_id", storeId) } }
                        .decodeList()
                    priceMap[storeId] = prices
                }
                storePrices = priceMap
            } catch (_: Exception) { }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { loadData() }

    // Create Dialog
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New Shopping List") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newListName, onValueChange = { newListName = it },
                        label = { Text("List Name") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newBudget, onValueChange = { newBudget = it },
                        label = { Text("Budget (optional)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                val userId = SupabaseClient.client.auth.currentUserOrNull()?.id ?: return@launch
                                SupabaseClient.client.from("shopping_lists").insert(
                                    ShoppingListCreate(userId, newListName, newBudget.toDoubleOrNull())
                                )
                                newListName = ""; newBudget = ""; showCreateDialog = false
                                isLoading = true; loadData()
                            } catch (_: Exception) { }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WiseUpColors.Blue500),
                    enabled = newListName.isNotBlank()
                ) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") } }
        )
    }

    // Delete Dialog
    showDeleteDialog?.let { listId ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete List?") },
            text = { Text("This will permanently delete this shopping list and all its items.") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                SupabaseClient.client.from("shopping_list_items")
                                    .delete { filter { eq("shopping_list_id", listId) } }
                                SupabaseClient.client.from("shopping_lists")
                                    .delete { filter { eq("id", listId) } }
                                showDeleteDialog = null; isLoading = true; loadData()
                            } catch (_: Exception) { }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WiseUpColors.Red500)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = null }) { Text("Cancel") } }
        )
    }

    // Store Assignment Dialog
    showStoreDialog?.let { listId ->
        AlertDialog(
            onDismissRequest = { showStoreDialog = null },
            title = { Text("Assign Store") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(stores) { store ->
                        val hqName = storeHqs[store.hqId] ?: "Unknown"
                        TextButton(
                            onClick = {
                                scope.launch {
                                    try {
                                        SupabaseClient.client.from("shopping_lists")
                                            .update({ set("assigned_store_id", store.id) }) {
                                                filter { eq("id", listId) }
                                            }
                                        showStoreDialog = null; isLoading = true; loadData()
                                    } catch (_: Exception) { }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("$hqName - ${store.location}", modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showStoreDialog = null }) { Text("Cancel") } }
        )
    }

    // Budget Dialog
    showBudgetDialog?.let { listId ->
        var budgetVal by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showBudgetDialog = null },
            title = { Text("Set Budget") },
            text = {
                OutlinedTextField(
                    value = budgetVal, onValueChange = { budgetVal = it },
                    label = { Text("Budget Amount") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                SupabaseClient.client.from("shopping_lists")
                                    .update({ set("budget", budgetVal.toDoubleOrNull()) }) {
                                        filter { eq("id", listId) }
                                    }
                                showBudgetDialog = null; isLoading = true; loadData()
                            } catch (_: Exception) { }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WiseUpColors.Blue500)
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showBudgetDialog = null }) { Text("Cancel") } }
        )
    }

    // Main Content
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Shopping Lists", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Button(
                onClick = { showCreateDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = WiseUpColors.Blue500),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("New List")
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = WiseUpColors.Blue500)
            }
        } else if (lists.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.ShoppingCart, null, Modifier.size(64.dp), tint = WiseUpColors.TextMuted)
                    Spacer(Modifier.height(8.dp))
                    Text("No shopping lists yet", color = WiseUpColors.TextSecondary)
                    Text("Tap 'New List' to get started", color = WiseUpColors.TextMuted, fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(lists) { list ->
                    val listItems = items[list.id] ?: emptyList()
                    val isExpanded = expandedListId == list.id
                    val assignedStore = stores.find { it.id == list.assignedStoreId }
                    val hqName = assignedStore?.let { storeHqs[it.hqId] } ?: "Unassigned"
                    val prices = list.assignedStoreId?.let { storePrices[it] } ?: emptyList()
                    val priceMap = prices.associateBy { it.productGtin }

                    var inStockTotal = 0.0
                    var allTotal = 0.0
                    listItems.forEach { item ->
                        val sp = priceMap[item.productGtin]
                        if (sp != null) {
                            val cost = sp.price * item.quantity
                            allTotal += cost
                            if (sp.inStock) inStockTotal += cost
                        }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(list.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text("Store: $hqName", fontSize = 12.sp, color = WiseUpColors.TextSecondary)
                                    Text("${listItems.size} items", fontSize = 12.sp, color = WiseUpColors.TextMuted)
                                }
                                IconButton(onClick = { expandedListId = if (isExpanded) null else list.id }) {
                                    Icon(
                                        if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null
                                    )
                                }
                            }

                            // Totals
                            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text("In-stock total", fontSize = 11.sp, color = WiseUpColors.TextMuted)
                                    Text(CurrencyProvider.formatPrice(inStockTotal), fontWeight = FontWeight.Bold, color = WiseUpColors.Blue500)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("All items total", fontSize = 11.sp, color = WiseUpColors.TextMuted)
                                    Text(CurrencyProvider.formatPrice(allTotal), fontWeight = FontWeight.Bold)
                                }
                            }

                            list.budget?.let { budget ->
                                Spacer(Modifier.height(4.dp))
                                Text("Budget: ${CurrencyProvider.formatPrice(budget)}", fontSize = 12.sp, color = if (allTotal > budget) WiseUpColors.Red500 else WiseUpColors.Blue500)
                            }

                            // Action buttons
                            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                                IconButton(onClick = { showStoreDialog = list.id }) {
                                    Icon(Icons.Default.Store, null, tint = WiseUpColors.Blue500)
                                }
                                IconButton(onClick = { showBudgetDialog = list.id }) {
                                    Icon(Icons.Default.AttachMoney, null, tint = WiseUpColors.Blue500)
                                }
                                IconButton(onClick = {
                                    val msg = buildString {
                                        append("*${list.name}*\n")
                                        append("Store: $hqName\n\n")
                                        listItems.forEach { item ->
                                            val prod = products[item.productGtin]
                                            val price = priceMap[item.productGtin]
                                            append("- ${prod?.description ?: item.productGtin} x${item.quantity}")
                                            price?.let { append(" @ ${CurrencyProvider.formatPrice(it.price)}") }
                                            append("\n")
                                        }
                                        append("\nTotal: ${CurrencyProvider.formatPrice(allTotal)}")
                                    }
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, msg)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share list via"))
                                }) {
                                    Icon(Icons.Default.Share, null, tint = WiseUpColors.Blue500)
                                }
                                IconButton(onClick = { showDeleteDialog = list.id }) {
                                    Icon(Icons.Default.Delete, null, tint = WiseUpColors.Red500)
                                }
                            }

                            // Expanded items
                            if (isExpanded && listItems.isNotEmpty()) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                listItems.forEach { item ->
                                    val prod = products[item.productGtin]
                                    val sp = priceMap[item.productGtin]
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(prod?.description ?: item.productGtin, fontSize = 14.sp)
                                            Text("Qty: ${item.quantity}", fontSize = 12.sp, color = WiseUpColors.TextMuted)
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            sp?.let {
                                                Text(CurrencyProvider.formatPrice(it.price * item.quantity), fontWeight = FontWeight.SemiBold)
                                                VerificationBadge(verified = it.verified)
                                                Spacer(Modifier.height(2.dp))
                                                Text(
                                                    if (it.inStock) "In Stock" else "Out of Stock",
                                                    fontSize = 11.sp,
                                                    color = if (it.inStock) WiseUpColors.Blue500 else WiseUpColors.Red500
                                                )
                                            } ?: Text("No price", fontSize = 12.sp, color = WiseUpColors.TextMuted)
                                        }
                                        IconButton(onClick = {
                                            scope.launch {
                                                try {
                                                    SupabaseClient.client.from("shopping_list_items")
                                                        .delete { filter { eq("id", item.id) } }
                                                    isLoading = true; loadData()
                                                } catch (_: Exception) { }
                                            }
                                        }, modifier = Modifier.size(32.dp)) {
                                            Icon(Icons.Default.Close, null, Modifier.size(16.dp), tint = WiseUpColors.Red500)
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
}
