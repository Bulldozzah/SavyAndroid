package com.example.myapplication.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.CountryData
import com.example.myapplication.data.ProfileUpsert
import com.example.myapplication.data.SupabaseClient
import com.example.myapplication.ui.components.GradientBackground
import com.example.myapplication.ui.theme.WiseUpColors
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSetupScreen(onComplete: () -> Unit) {
    var displayName by remember { mutableStateOf("") }
    var selectedCountry by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var whatsapp by remember { mutableStateOf("") }
    var additionalContact by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var countryExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val countryInfo = CountryData.countries.find { it.name == selectedCountry }
    val currency = countryInfo?.currency ?: ""
    val phoneCode = countryInfo?.phoneCode ?: ""

    GradientBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))

            Text(
                text = "Complete Your Profile",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = WiseUpColors.TextPrimary
            )
            Text(
                text = "Tell us a bit about yourself",
                fontSize = 14.sp,
                color = WiseUpColors.TextSecondary
            )

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text("Display Name") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    ExposedDropdownMenuBox(
                        expanded = countryExpanded,
                        onExpandedChange = { countryExpanded = !countryExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedCountry,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Country") },
                            leadingIcon = { Icon(Icons.Default.Public, contentDescription = null) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = countryExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = countryExpanded,
                            onDismissRequest = { countryExpanded = false }
                        ) {
                            CountryData.countries.forEach { country ->
                                DropdownMenuItem(
                                    text = { Text("${country.name} (${country.currency})") },
                                    onClick = {
                                        selectedCountry = country.name
                                        countryExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    if (currency.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Currency: $currency  |  Phone code: $phoneCode",
                            fontSize = 12.sp,
                            color = WiseUpColors.TextSecondary,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Phone Number") },
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        prefix = if (phoneCode.isNotEmpty()) {
                            { Text("$phoneCode ") }
                        } else null
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = whatsapp,
                        onValueChange = { whatsapp = it },
                        label = { Text("WhatsApp Number") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = additionalContact,
                        onValueChange = { additionalContact = it },
                        label = { Text("Additional Contact") },
                        leadingIcon = { Icon(Icons.Default.ContactPhone, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    errorMessage?.let { msg ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = msg, color = WiseUpColors.Red500, fontSize = 13.sp)
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            if (displayName.isBlank()) {
                                errorMessage = "Display name is required"
                                return@Button
                            }
                            isLoading = true
                            errorMessage = null
                            scope.launch {
                                try {
                                    val userId = SupabaseClient.client.auth.currentUserOrNull()?.id ?: ""
                                    val userEmail = SupabaseClient.client.auth.currentUserOrNull()?.email ?: email
                                    val profile = ProfileUpsert(
                                        userId = userId,
                                        displayName = displayName,
                                        country = selectedCountry.ifBlank { null },
                                        phone = phone.ifBlank { null },
                                        phoneAreaCode = phoneCode.ifBlank { null },
                                        currency = currency.ifBlank { null },
                                        email = userEmail.ifBlank { null },
                                        whatsapp = whatsapp.ifBlank { null },
                                        contact = additionalContact.ifBlank { null },
                                        profileCompleted = true
                                    )
                                    SupabaseClient.client.from("profiles")
                                        .upsert(profile) {
                                            onConflict = "user_id"
                                        }
                                    onComplete()
                                } catch (e: Exception) {
                                    errorMessage = e.message ?: "Failed to save profile"
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = WiseUpColors.Green600),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Complete Profile", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
