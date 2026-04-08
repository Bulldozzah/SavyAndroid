package com.example.myapplication.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.example.myapplication.ui.theme.WiseUpColors
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreFeedbackScreen() {
    val scope = rememberCoroutineScope()
    var stores by remember { mutableStateOf<List<Store>>(emptyList()) }
    var storeHqs by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var selectedStoreId by remember { mutableStateOf<String?>(null) }
    var feedbackType by remember { mutableStateOf("Store Service & Experience") }
    var rating by remember { mutableIntStateOf(0) }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var isSubmitting by remember { mutableStateOf(false) }
    var successMsg by remember { mutableStateOf<String?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var storeExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            stores = SupabaseClient.client.from("stores").select().decodeList()
            val hqs: List<StoreHq> = SupabaseClient.client.from("store_hq").select().decodeList()
            storeHqs = hqs.associate { it.id to it.name }
        } catch (_: Exception) { }
        isLoading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Store Feedback", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Share your shopping experience", fontSize = 14.sp, color = WiseUpColors.TextSecondary)
        Spacer(Modifier.height(20.dp))

        if (isLoading) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = WiseUpColors.Green600)
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    // Store selector
                    Text("Select Store", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
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
                            placeholder = { Text("Choose a store") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = storeExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
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

                    Spacer(Modifier.height(16.dp))

                    // Feedback type
                    Text("Feedback Type", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        FilterChip(
                            selected = feedbackType == "Store Service & Experience",
                            onClick = { feedbackType = "Store Service & Experience" },
                            label = { Text("Service", fontSize = 12.sp) },
                            modifier = Modifier.padding(end = 8.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = WiseUpColors.Green600,
                                selectedLabelColor = Color.White
                            )
                        )
                        FilterChip(
                            selected = feedbackType == "Product Quality & Experience",
                            onClick = { feedbackType = "Product Quality & Experience" },
                            label = { Text("Product Quality", fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = WiseUpColors.Green600,
                                selectedLabelColor = Color.White
                            )
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    // Rating
                    Text("Rating", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Row {
                        (1..5).forEach { star ->
                            IconButton(onClick = { rating = star }) {
                                Icon(
                                    if (star <= rating) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = null,
                                    tint = if (star <= rating) WiseUpColors.Yellow500 else WiseUpColors.TextMuted,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Title
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(Modifier.height(12.dp))

                    // Body
                    OutlinedTextField(
                        value = body,
                        onValueChange = { body = it },
                        label = { Text("Your feedback") },
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        shape = RoundedCornerShape(12.dp)
                    )

                    errorMsg?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = WiseUpColors.Red500, fontSize = 13.sp)
                    }

                    successMsg?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = WiseUpColors.Green600, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }

                    Spacer(Modifier.height(20.dp))

                    Button(
                        onClick = {
                            errorMsg = null; successMsg = null
                            if (selectedStoreId == null) { errorMsg = "Please select a store"; return@Button }
                            if (rating == 0) { errorMsg = "Please select a rating"; return@Button }
                            if (body.isBlank()) { errorMsg = "Please write your feedback"; return@Button }
                            isSubmitting = true
                            scope.launch {
                                try {
                                    val userId = SupabaseClient.client.auth.currentUserOrNull()?.id ?: return@launch
                                    SupabaseClient.client.from("store_feedback").insert(
                                        StoreFeedbackEntry(
                                            userId = userId,
                                            storeId = selectedStoreId!!,
                                            rating = rating,
                                            feedbackType = feedbackType,
                                            title = title.ifBlank { null },
                                            body = body
                                        )
                                    )
                                    successMsg = "Feedback submitted successfully!"
                                    selectedStoreId = null; rating = 0; title = ""; body = ""
                                } catch (e: Exception) {
                                    errorMsg = e.message ?: "Failed to submit feedback"
                                } finally {
                                    isSubmitting = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = WiseUpColors.Green600),
                        enabled = !isSubmitting
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Text("Submit Feedback", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
