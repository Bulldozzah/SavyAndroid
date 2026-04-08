package com.example.myapplication.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ui.theme.WiseUpColors

@Composable
fun ContactScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))

        Text("Contact Us", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = WiseUpColors.Green600)
        Text("We'd love to hear from you", fontSize = 14.sp, color = WiseUpColors.TextSecondary)

        Spacer(Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Get in Touch", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(16.dp))

                val contacts = listOf(
                    Triple(Icons.Default.Email, "Email", "support@wiseupshop.com"),
                    Triple(Icons.Default.Phone, "Phone", "+27 (0) 11 000 0000"),
                    Triple(Icons.AutoMirrored.Filled.Chat, "WhatsApp", "+27 60 000 0000"),
                    Triple(Icons.Default.LocationOn, "Address", "Johannesburg, South Africa")
                )

                contacts.forEach { (icon, label, value) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(icon, null, tint = WiseUpColors.Green600, modifier = Modifier.size(24.dp))
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

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Business Hours", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(12.dp))

                val hours = listOf(
                    "Monday - Friday" to "08:00 - 17:00",
                    "Saturday" to "09:00 - 13:00",
                    "Sunday" to "Closed"
                )

                hours.forEach { (day, time) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(day, fontSize = 14.sp, color = WiseUpColors.TextPrimary)
                        Text(time, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                            color = if (time == "Closed") WiseUpColors.Red500 else WiseUpColors.Green600)
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}
