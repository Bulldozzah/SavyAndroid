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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.*
import com.example.myapplication.ui.theme.WiseUpColors
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch

@Composable
fun CustomerFeedbackScreen(storeIds: List<String>, storeNames: Map<String, String>) {
    val scope = rememberCoroutineScope()
    var feedbackList by remember { mutableStateOf<List<StoreFeedbackRead>>(emptyList()) }
    var authorNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(storeIds) {
        if (storeIds.isEmpty()) { isLoading = false; return@LaunchedEffect }
        try {
            val allFeedback = mutableListOf<StoreFeedbackRead>()
            storeIds.forEach { sid ->
                val fb: List<StoreFeedbackRead> = SupabaseClient.client.from("store_feedback")
                    .select { filter { eq("store_id", sid) } }
                    .decodeList()
                allFeedback.addAll(fb)
            }
            feedbackList = allFeedback.sortedByDescending { it.createdAt }

            // Fetch author display names
            val userIds = allFeedback.map { it.userId }.distinct()
            if (userIds.isNotEmpty()) {
                val profiles: List<Profile> = SupabaseClient.client.from("profiles")
                    .select { filter { isIn("user_id", userIds) } }
                    .decodeList()
                authorNames = profiles.associate { it.userId to (it.displayName ?: "Anonymous") }
            }
        } catch (_: Exception) { }
        isLoading = false
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = WiseUpColors.Blue500)
            }
        } else if (feedbackList.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.RateReview, null, Modifier.size(48.dp), tint = WiseUpColors.TextMuted)
                    Spacer(Modifier.height(8.dp))
                    Text("No customer feedback yet", color = WiseUpColors.TextMuted)
                }
            }
        } else {
            Text("${feedbackList.size} Reviews", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(12.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(feedbackList) { fb ->
                    val ratingColor = when {
                        fb.rating >= 4 -> WiseUpColors.Green600
                        fb.rating >= 3 -> Color(0xFFF5A623)
                        else -> WiseUpColors.Red500
                    }
                    val sName = storeNames[fb.storeId] ?: "Unknown Store"
                    val author = authorNames[fb.userId] ?: "Anonymous"
                    val dateStr = try {
                        fb.createdAt.substringBefore("T").let { d ->
                            val parts = d.split("-")
                            if (parts.size == 3) "${parts[2]}/${parts[1]}/${parts[0]}" else d
                        }
                    } catch (_: Exception) { fb.createdAt }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            // Rating + type badge
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    repeat(5) { i ->
                                        Icon(
                                            if (i < fb.rating) Icons.Default.Star else Icons.Default.StarBorder,
                                            contentDescription = null,
                                            tint = ratingColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text("${fb.rating}/5", fontWeight = FontWeight.Bold, color = ratingColor, fontSize = 13.sp)
                                }
                                AssistChip(
                                    onClick = {},
                                    label = { Text(fb.feedbackType, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = WiseUpColors.Blue100,
                                        labelColor = WiseUpColors.Blue600
                                    ),
                                    modifier = Modifier.height(24.dp)
                                )
                            }

                            // Title
                            fb.title?.let { title ->
                                if (title.isNotBlank()) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                }
                            }

                            // Body
                            Spacer(Modifier.height(6.dp))
                            Text(
                                fb.body,
                                fontSize = 14.sp,
                                color = WiseUpColors.TextPrimary,
                                lineHeight = 20.sp
                            )

                            Spacer(Modifier.height(10.dp))
                            HorizontalDivider(color = WiseUpColors.TextMuted.copy(alpha = 0.2f))
                            Spacer(Modifier.height(8.dp))

                            // Meta: store, author, date
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(sName, fontSize = 11.sp, color = WiseUpColors.TextMuted)
                                    Text("By $author", fontSize = 11.sp, color = WiseUpColors.TextSecondary)
                                }
                                Text(dateStr, fontSize = 11.sp, color = WiseUpColors.TextMuted)
                            }
                        }
                    }
                }
            }
        }
    }
}
