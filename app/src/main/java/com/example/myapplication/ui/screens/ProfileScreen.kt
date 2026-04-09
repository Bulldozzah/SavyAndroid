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
import com.example.myapplication.data.*
import com.example.myapplication.ui.theme.WiseUpColors
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen() {
    val scope = rememberCoroutineScope()
    var profile by remember { mutableStateOf<Profile?>(null) }
    var displayName by remember { mutableStateOf("") }
    var selectedCountry by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var whatsapp by remember { mutableStateOf("") }
    var additionalContact by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var successMsg by remember { mutableStateOf<String?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var countryExpanded by remember { mutableStateOf(false) }

    val countryInfo = CountryData.countries.find { it.name == selectedCountry }
    val currency = countryInfo?.currency ?: profile?.currency ?: ""
    val phoneCode = countryInfo?.phoneCode ?: profile?.phoneAreaCode ?: ""

    LaunchedEffect(Unit) {
        try {
            val userId = SupabaseClient.client.auth.currentUserOrNull()?.id ?: return@LaunchedEffect
            val profiles: List<Profile> = SupabaseClient.client.from("profiles")
                .select { filter { eq("user_id", userId) } }
                .decodeList()
            profiles.firstOrNull()?.let { p ->
                profile = p
                displayName = p.displayName ?: ""
                selectedCountry = p.country ?: ""
                phone = p.phone ?: ""
                email = p.email ?: ""
                whatsapp = p.whatsapp ?: ""
                additionalContact = p.contact ?: ""
            }
        } catch (_: Exception) { }
        isLoading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("My Profile", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Manage your account details", fontSize = 14.sp, color = WiseUpColors.TextSecondary)
        Spacer(Modifier.height(20.dp))

        if (isLoading) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = WiseUpColors.Blue500)
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    OutlinedTextField(
                        value = displayName, onValueChange = { displayName = it },
                        label = { Text("Display Name") },
                        leadingIcon = { Icon(Icons.Default.Person, null) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(Modifier.height(12.dp))

                    ExposedDropdownMenuBox(
                        expanded = countryExpanded,
                        onExpandedChange = { countryExpanded = !countryExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedCountry, onValueChange = {}, readOnly = true,
                            label = { Text("Country") },
                            leadingIcon = { Icon(Icons.Default.Public, null) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = countryExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = countryExpanded,
                            onDismissRequest = { countryExpanded = false }
                        ) {
                            CountryData.countries.forEach { country ->
                                DropdownMenuItem(
                                    text = { Text("${country.name} (${country.currency})") },
                                    onClick = { selectedCountry = country.name; countryExpanded = false }
                                )
                            }
                        }
                    }

                    if (currency.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text("Currency: $currency  |  Phone code: $phoneCode",
                            fontSize = 12.sp, color = WiseUpColors.TextSecondary, modifier = Modifier.padding(start = 8.dp))
                    }

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = phone, onValueChange = { phone = it },
                        label = { Text("Phone Number") },
                        leadingIcon = { Icon(Icons.Default.Phone, null) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        shape = RoundedCornerShape(12.dp),
                        prefix = if (phoneCode.isNotEmpty()) { { Text("$phoneCode ") } } else null
                    )

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = email, onValueChange = { email = it },
                        label = { Text("Email") },
                        leadingIcon = { Icon(Icons.Default.Email, null) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = whatsapp, onValueChange = { whatsapp = it },
                        label = { Text("WhatsApp Number") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Chat, null) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = additionalContact, onValueChange = { additionalContact = it },
                        label = { Text("Additional Contact") },
                        leadingIcon = { Icon(Icons.Default.ContactPhone, null) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    successMsg?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = WiseUpColors.Blue500, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    }
                    errorMsg?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = WiseUpColors.Red500, fontSize = 13.sp)
                    }

                    Spacer(Modifier.height(20.dp))

                    Button(
                        onClick = {
                            successMsg = null; errorMsg = null
                            if (displayName.isBlank()) { errorMsg = "Display name is required"; return@Button }
                            isSaving = true
                            scope.launch {
                                try {
                                    val userId = SupabaseClient.client.auth.currentUserOrNull()?.id ?: return@launch
                                    SupabaseClient.client.from("profiles")
                                        .upsert(ProfileUpsert(
                                            userId = userId,
                                            displayName = displayName,
                                            country = selectedCountry.ifBlank { null },
                                            phone = phone.ifBlank { null },
                                            phoneAreaCode = phoneCode.ifBlank { null },
                                            currency = currency.ifBlank { null },
                                            email = email.ifBlank { null },
                                            whatsapp = whatsapp.ifBlank { null },
                                            contact = additionalContact.ifBlank { null },
                                            profileCompleted = true
                                        )) { onConflict = "user_id" }
                                    successMsg = "Profile updated successfully!"
                                } catch (e: Exception) {
                                    errorMsg = e.message ?: "Failed to update profile"
                                } finally {
                                    isSaving = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = WiseUpColors.Blue500),
                        enabled = !isSaving
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Text("Save Changes", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}
