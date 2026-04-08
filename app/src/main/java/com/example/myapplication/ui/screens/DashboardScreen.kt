package com.example.myapplication.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.myapplication.data.*
import com.example.myapplication.ui.theme.WiseUpColors
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen() {
    var banners by remember { mutableStateOf<List<AdBanner>>(emptyList()) }
    var promotions by remember { mutableStateOf<List<AdPromotion>>(emptyList()) }
    var generalAds by remember { mutableStateOf<List<AdGeneral>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var currentBannerIndex by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        try {
            banners = SupabaseClient.client.from("ad_banners")
                .select {
                    filter { eq("is_active", true) }
                }
                .decodeList()
            promotions = SupabaseClient.client.from("ad_promotions")
                .select {
                    filter { eq("is_active", true) }
                }
                .decodeList()
            generalAds = SupabaseClient.client.from("ad_general")
                .select {
                    filter { eq("is_active", true) }
                }
                .decodeList()
        } catch (_: Exception) { }
        isLoading = false
    }

    LaunchedEffect(banners) {
        if (banners.isNotEmpty()) {
            while (true) {
                delay(5000)
                currentBannerIndex = (currentBannerIndex + 1) % banners.size
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp)
    ) {
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = WiseUpColors.Green600)
            }
        } else {
            // Welcome banner
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                listOf(WiseUpColors.Green500, WiseUpColors.Blue500)
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column {
                        Text(
                            text = "Welcome to WiseUp Shop!",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Compare prices, save money, shop smarter.",
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
            }

            // Banner Carousel
            if (banners.isNotEmpty()) {
                Text(
                    text = "Featured",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = WiseUpColors.TextPrimary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        val banner = banners[currentBannerIndex]
                        AsyncImage(
                            model = banner.imageUrl,
                            contentDescription = banner.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.3f))
                        )
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(16.dp)
                        ) {
                            Text(
                                text = banner.title,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            banner.description?.let {
                                Text(
                                    text = it,
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 12.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        // Dots indicator
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            banners.forEachIndexed { index, _ ->
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 3.dp)
                                        .size(if (index == currentBannerIndex) 8.dp else 6.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (index == currentBannerIndex) Color.White
                                            else Color.White.copy(alpha = 0.5f)
                                        )
                                )
                            }
                        }
                    }
                }
            } else {
                // Placeholder when no banners
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(WiseUpColors.Green500, WiseUpColors.Blue500)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.ShoppingCart,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Start comparing prices today!",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Flash Deals
            if (promotions.isNotEmpty()) {
                Text(
                    text = "Flash Deals",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = WiseUpColors.TextPrimary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(promotions) { promo ->
                        Card(
                            modifier = Modifier
                                .width(160.dp)
                                .height(200.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column {
                                promo.imageUrl?.let { url ->
                                    AsyncImage(
                                        model = url,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(110.dp),
                                        contentScale = ContentScale.Crop
                                    )
                                } ?: Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(110.dp)
                                        .background(WiseUpColors.Green100),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.LocalOffer,
                                        contentDescription = null,
                                        tint = WiseUpColors.Green600,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = promo.productGtin ?: "Special Deal",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "%.2f".format(promo.promotionalPrice),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = WiseUpColors.Green600
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // General Ads (Promotional Grid)
            if (generalAds.isNotEmpty()) {
                Text(
                    text = "Promotions",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = WiseUpColors.TextPrimary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                generalAds.take(6).forEach { ad ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                        ) {
                            AsyncImage(
                                model = ad.imageUrl,
                                contentDescription = ad.title,
                                modifier = Modifier
                                    .width(120.dp)
                                    .fillMaxHeight(),
                                contentScale = ContentScale.Crop
                            )
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = ad.title,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                ad.description?.let {
                                    Text(
                                        text = it,
                                        fontSize = 12.sp,
                                        color = WiseUpColors.TextSecondary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Quick Action Cards when no ads
            if (promotions.isEmpty() && generalAds.isEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Quick Actions",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = WiseUpColors.TextPrimary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                val quickActions = listOf(
                    Triple(Icons.Default.Search, "Search Products", "Find and add products to your lists"),
                    Triple(Icons.AutoMirrored.Filled.CompareArrows, "Compare Prices", "Find the cheapest store for your list"),
                    Triple(Icons.AutoMirrored.Filled.FormatListBulleted, "Shopping Lists", "Create and manage your shopping lists"),
                    Triple(Icons.Default.Star, "Store Feedback", "Rate and review stores")
                )
                quickActions.forEach { (icon, title, subtitle) ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(WiseUpColors.Green100),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    icon,
                                    contentDescription = null,
                                    tint = WiseUpColors.Green600,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = title,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = subtitle,
                                    fontSize = 12.sp,
                                    color = WiseUpColors.TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
