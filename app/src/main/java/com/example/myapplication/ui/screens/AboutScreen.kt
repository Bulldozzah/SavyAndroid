package com.example.myapplication.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ui.theme.WiseUpColors

@Composable
fun AboutScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))

        Text("WiseUp Shop", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = WiseUpColors.Blue500)
        Text("Smart Shopping, Better Savings", fontSize = 14.sp, color = WiseUpColors.TextSecondary)

        Spacer(Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("About Us", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(12.dp))
                Text(
                    "WiseUp Shop is a smart, multi-store shopping and price comparison platform designed to help consumers save money and make better purchasing decisions.",
                    fontSize = 14.sp, color = WiseUpColors.TextSecondary, lineHeight = 22.sp
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "At its core, the app allows shoppers to create shopping lists, search for products, and compare prices across multiple stores to find the cheapest option based on real-time availability and pricing.",
                    fontSize = 14.sp, color = WiseUpColors.TextSecondary, lineHeight = 22.sp
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Key Features", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(12.dp))

                val features = listOf(
                    Icons.Default.Search to "Search & compare prices across stores",
                    Icons.AutoMirrored.Filled.FormatListBulleted to "Create and manage smart shopping lists",
                    Icons.Default.AttachMoney to "Set budgets and track spending",
                    Icons.Default.Share to "Share lists via WhatsApp",
                    Icons.Default.Star to "Rate and review stores",
                    Icons.AutoMirrored.Filled.CompareArrows to "Multi-store price comparison"
                )

                features.forEach { (icon, text) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(icon, null, tint = WiseUpColors.Blue500, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(text, fontSize = 14.sp, color = WiseUpColors.TextPrimary)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Contact Us", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(16.dp))

                val contacts = listOf(
                    Triple(Icons.Default.Phone, "Phone", "+260 973 433 321"),
                    Triple(Icons.AutoMirrored.Filled.Chat, "WhatsApp", "+260 973 433 321"),
                    Triple(Icons.Default.Email, "Email", "macxiontech@gmail.com"),
                    Triple(Icons.Default.LocationOn, "Address", "Lusaka, Zambia")
                )

                contacts.forEach { (icon, label, value) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(icon, null, tint = WiseUpColors.Blue500, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(label, fontSize = 12.sp, color = WiseUpColors.TextMuted)
                            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Text("Version 1.0.0", fontSize = 12.sp, color = WiseUpColors.TextMuted)
        Spacer(Modifier.height(32.dp))
    }
}
