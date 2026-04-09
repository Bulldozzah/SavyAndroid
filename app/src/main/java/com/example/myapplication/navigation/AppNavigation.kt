package com.example.myapplication.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.myapplication.data.*
import com.example.myapplication.ui.components.GradientBackground
import com.example.myapplication.ui.screens.*
import com.example.myapplication.ui.theme.WiseUpColors
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch

data class NavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    var isAuthenticated by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var needsProfileSetup by remember { mutableStateOf(false) }

    // Collect sessionStatus — this properly waits for session restoration from storage
    LaunchedEffect(Unit) {
        SupabaseClient.client.auth.sessionStatus.collect { status ->
            when (status) {
                is SessionStatus.Authenticated -> {
                    isAuthenticated = true
                    isLoading = false
                }
                is SessionStatus.NotAuthenticated -> {
                    isAuthenticated = false
                    isLoading = false
                }
                SessionStatus.Initializing -> {
                    isLoading = true
                }
                is SessionStatus.RefreshFailure -> {
                    isAuthenticated = false
                    isLoading = false
                }
            }
        }
    }

    if (isLoading) {
        // Show loading while session is being restored from storage
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = WiseUpColors.Blue500)
                Spacer(Modifier.height(16.dp))
                Text("Loading...", color = WiseUpColors.TextSecondary)
            }
        }
    } else if (!isAuthenticated) {
        AuthScreen(onAuthSuccess = { isNewUser ->
            needsProfileSetup = isNewUser
            isAuthenticated = true
        })
    } else if (needsProfileSetup) {
        ProfileSetupScreen(onComplete = { needsProfileSetup = false })
    } else {
        MainScaffold(
            navController = navController,
            onLogout = {
                isAuthenticated = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(navController: NavHostController, onLogout: () -> Unit) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    // Store owner state
    var isStoreOwner by remember { mutableStateOf(false) }
    var storeOwnerMode by remember { mutableStateOf(false) }
    var ownedStores by remember { mutableStateOf<List<Store>>(emptyList()) }
    var storeHqNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var selectedStore by remember { mutableStateOf<Store?>(null) }
    var showStorePicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        CurrencyProvider.load()
        try {
            val userId = SupabaseClient.client.auth.currentUserOrNull()?.id ?: return@LaunchedEffect
            val roles: List<UserRole> = SupabaseClient.client.from("user_roles")
                .select { filter { eq("user_id", userId) } }
                .decodeList()
            isStoreOwner = roles.any { it.role == "store_owner" }
            if (isStoreOwner) {
                val stores: List<Store> = SupabaseClient.client.from("stores")
                    .select { filter { eq("store_owner_id", userId) } }
                    .decodeList()
                ownedStores = stores
                if (stores.isNotEmpty()) selectedStore = stores.first()
                val hqs: List<StoreHq> = SupabaseClient.client.from("store_hq").select().decodeList()
                storeHqNames = hqs.associate { it.id to it.name }
            }
        } catch (_: Exception) { }
    }

    val shopperNavItems = listOf(
        NavItem(Screen.Dashboard.route, "Dashboard", Icons.Default.Home),
        NavItem(Screen.SearchProducts.route, "Search & Add Products", Icons.Default.Search),
        NavItem(Screen.BrowseStore.route, "Browse Store", Icons.Default.StoreMallDirectory),
        NavItem(Screen.BarcodeScanner.route, "Scan & Update Price", Icons.Default.QrCodeScanner),
        NavItem(Screen.ComparePrices.route, "Compare Prices", Icons.AutoMirrored.Filled.CompareArrows),
        NavItem(Screen.ShoppingLists.route, "Shopping Lists", Icons.AutoMirrored.Filled.FormatListBulleted),
        NavItem(Screen.StoreFeedback.route, "Store Feedback", Icons.Default.Star),
    )

    val storeOwnerNavItems = listOf(
        NavItem(Screen.MyStore.route, "My Store", Icons.Default.Inventory),
        NavItem(Screen.StoreOwnerPrices.route, "Store Prices", Icons.Default.AttachMoney),
        NavItem(Screen.StoreAdmin.route, "Store Admin", Icons.Default.AdminPanelSettings),
        NavItem(Screen.CustomerFeedback.route, "Customer Feedback", Icons.Default.RateReview),
    )

    val navItems = if (storeOwnerMode) storeOwnerNavItems else shopperNavItems

    val bottomNavItems = listOf(
        NavItem(Screen.Profile.route, "Profile", Icons.Default.Person),
        NavItem(Screen.About.route, "About", Icons.Default.Info),
        NavItem(Screen.Contact.route, "Contact", Icons.Default.ContactMail),
    )

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(280.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                ) {
                    // Header
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    if (storeOwnerMode) listOf(WiseUpColors.Orange500, Color(0xFFE8920E))
                                    else listOf(WiseUpColors.Blue500, WiseUpColors.Blue600)
                                )
                            )
                            .padding(24.dp)
                    ) {
                        Column {
                            Text(
                                "WiseUp Shop",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                if (storeOwnerMode) "Store Owner" else "Smart Shopper",
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                            if (isStoreOwner) {
                                Spacer(Modifier.height(8.dp))
                                AssistChip(
                                    onClick = {
                                        storeOwnerMode = !storeOwnerMode
                                        scope.launch { drawerState.close() }
                                        val dest = if (storeOwnerMode) Screen.MyStore.route else Screen.Dashboard.route
                                        navController.navigate(dest) {
                                            popUpTo(0) { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    },
                                    label = {
                                        Text(
                                            if (storeOwnerMode) "Switch to Shopper" else "Switch to Store Owner",
                                            fontSize = 11.sp
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            if (storeOwnerMode) Icons.Default.ShoppingCart else Icons.Default.Store,
                                            null, Modifier.size(14.dp), tint = Color.White
                                        )
                                    },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = Color.White.copy(alpha = 0.25f),
                                        labelColor = Color.White,
                                        leadingIconContentColor = Color.White
                                    ),
                                    modifier = Modifier.height(28.dp)
                                )
                            } else {
                                Spacer(Modifier.height(8.dp))
                                AssistChip(
                                    onClick = {},
                                    label = { Text("Shopper", fontSize = 11.sp) },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = Color.White.copy(alpha = 0.2f),
                                        labelColor = Color.White
                                    ),
                                    modifier = Modifier.height(24.dp)
                                )
                            }
                        }
                    }

                    // Store picker (store owner mode, multiple stores)
                    if (storeOwnerMode && ownedStores.size > 1) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Store, null, Modifier.size(16.dp), tint = WiseUpColors.Orange500)
                            Spacer(Modifier.width(6.dp))
                            TextButton(onClick = { showStorePicker = true }) {
                                Text(
                                    selectedStore?.let { s ->
                                        "${storeHqNames[s.hqId] ?: ""} - ${s.location}"
                                    } ?: "Select store",
                                    fontSize = 12.sp, color = WiseUpColors.TextPrimary
                                )
                                Icon(Icons.Default.ArrowDropDown, null, Modifier.size(16.dp))
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Nav items
                    navItems.forEach { item ->
                        val isSelected = currentRoute == item.route
                        NavigationDrawerItem(
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(item.label) },
                            selected = isSelected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(Screen.Dashboard.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = WiseUpColors.Blue100,
                                selectedIconColor = WiseUpColors.Blue600,
                                selectedTextColor = WiseUpColors.Blue600
                            )
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

                    // Bottom nav items
                    bottomNavItems.forEach { item ->
                        val isSelected = currentRoute == item.route
                        NavigationDrawerItem(
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(item.label) },
                            selected = isSelected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(Screen.Dashboard.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = WiseUpColors.Blue100,
                                selectedIconColor = WiseUpColors.Blue600,
                                selectedTextColor = WiseUpColors.Blue600
                            )
                        )
                    }

                    Spacer(Modifier.weight(1f))

                    // Logout
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    NavigationDrawerItem(
                        icon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = WiseUpColors.Red500) },
                        label = { Text("Logout", color = WiseUpColors.Red500) },
                        selected = false,
                        onClick = {
                            scope.launch {
                                try {
                                    SupabaseClient.client.auth.signOut()
                                } catch (_: Exception) { }
                                drawerState.close()
                                onLogout()
                            }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            when (currentRoute) {
                                Screen.Dashboard.route -> "Dashboard"
                                Screen.SearchProducts.route -> "Search Products"
                                Screen.BrowseStore.route -> "Browse Store"
                                Screen.BarcodeScanner.route -> "Scan & Update Price"
                                Screen.ComparePrices.route -> "Compare Prices"
                                Screen.ShoppingLists.route -> "Shopping Lists"
                                Screen.StoreFeedback.route -> "Store Feedback"
                                Screen.MyStore.route -> "My Store"
                                Screen.StoreOwnerPrices.route -> "Store Prices"
                                Screen.StoreAdmin.route -> "Store Admin"
                                Screen.CustomerFeedback.route -> "Customer Feedback"
                                Screen.Profile.route -> "Profile"
                                Screen.About.route -> "About"
                                Screen.Contact.route -> "Contact"
                                else -> "WiseUp Shop"
                            },
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.White,
                        titleContentColor = WiseUpColors.TextPrimary
                    )
                )
            }
        ) { innerPadding ->
            GradientBackground(modifier = Modifier.padding(innerPadding)) {
                NavHost(
                    navController = navController,
                    startDestination = Screen.Dashboard.route
                ) {
                    composable(Screen.Dashboard.route) { DashboardScreen() }
                    composable(Screen.SearchProducts.route) { SearchProductsScreen() }
                    composable(Screen.BrowseStore.route) { BrowseStoreScreen() }
                    composable(Screen.BarcodeScanner.route) {
                        BarcodeScannerScreen(
                            onNavigateToProduct = { gtin ->
                                navController.navigate(Screen.ProductDetail.createRoute(gtin))
                            }
                        )
                    }
                    composable(
                        Screen.ProductDetail.route,
                        arguments = listOf(
                            androidx.navigation.navArgument("gtin") {
                                type = androidx.navigation.NavType.StringType
                            }
                        )
                    ) { backStackEntry ->
                        val gtin = backStackEntry.arguments?.getString("gtin") ?: ""
                        ProductDetailScreen(productGtin = gtin)
                    }
                    composable(Screen.ComparePrices.route) { ComparePricesScreen() }
                    composable(Screen.ShoppingLists.route) { ShoppingListsScreen() }
                    composable(Screen.StoreFeedback.route) { StoreFeedbackScreen() }
                    composable(Screen.Profile.route) { ProfileScreen() }
                    composable(Screen.About.route) { AboutScreen() }
                    composable(Screen.Contact.route) { ContactScreen() }

                    // Store Owner screens
                    composable(Screen.MyStore.route) {
                        selectedStore?.let { store ->
                            val hqName = storeHqNames[store.hqId] ?: "Store"
                            MyStoreScreen(
                                storeId = store.id,
                                storeName = "$hqName - ${store.location}"
                            )
                        }
                    }
                    composable(Screen.StoreOwnerPrices.route) {
                        selectedStore?.let { store ->
                            val hqName = storeHqNames[store.hqId] ?: "Store"
                            StoreOwnerPricesScreen(
                                storeId = store.id,
                                storeName = "$hqName - ${store.location}"
                            )
                        }
                    }
                    composable(Screen.StoreAdmin.route) {
                        selectedStore?.let { store ->
                            val hqName = storeHqNames[store.hqId] ?: "Store"
                            StoreAdminScreen(
                                store = store,
                                hqName = hqName,
                                onStoreUpdated = { updated ->
                                    selectedStore = updated
                                    ownedStores = ownedStores.map {
                                        if (it.id == updated.id) updated else it
                                    }
                                }
                            )
                        }
                    }
                    composable(Screen.CustomerFeedback.route) {
                        val storeIds = ownedStores.map { it.id }
                        val storeNameMap = ownedStores.associate { s ->
                            s.id to "${storeHqNames[s.hqId] ?: ""} - ${s.location}"
                        }
                        CustomerFeedbackScreen(
                            storeIds = storeIds,
                            storeNames = storeNameMap
                        )
                    }
                }
            }
        }
    }

    // Store picker dialog
    if (showStorePicker) {
        AlertDialog(
            onDismissRequest = { showStorePicker = false },
            title = { Text("Select Store") },
            text = {
                Column {
                    ownedStores.forEach { store ->
                        val hqName = storeHqNames[store.hqId] ?: ""
                        val isSelected = store.id == selectedStore?.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    selectedStore = store
                                    showStorePicker = false
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("$hqName - ${store.location}")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showStorePicker = false }) { Text("Close") }
            }
        )
    }
}
