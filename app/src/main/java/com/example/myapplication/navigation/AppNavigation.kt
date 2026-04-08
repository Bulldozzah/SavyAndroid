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
import com.example.myapplication.data.SupabaseClient
import com.example.myapplication.ui.components.GradientBackground
import com.example.myapplication.ui.screens.*
import com.example.myapplication.ui.theme.WiseUpColors
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
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
                CircularProgressIndicator(color = WiseUpColors.Green600)
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

    val navItems = listOf(
        NavItem(Screen.Dashboard.route, "Dashboard", Icons.Default.Home),
        NavItem(Screen.SearchProducts.route, "Search & Add Products", Icons.Default.Search),
        NavItem(Screen.BrowseStore.route, "Browse Store", Icons.Default.StoreMallDirectory),
        NavItem(Screen.BarcodeScanner.route, "Scan & Update Price", Icons.Default.QrCodeScanner),
        NavItem(Screen.ComparePrices.route, "Compare Prices", Icons.AutoMirrored.Filled.CompareArrows),
        NavItem(Screen.ShoppingLists.route, "Shopping Lists", Icons.AutoMirrored.Filled.FormatListBulleted),
        NavItem(Screen.StoreFeedback.route, "Store Feedback", Icons.Default.Star),
    )

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
                                    listOf(WiseUpColors.Green500, WiseUpColors.Blue500)
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
                                "Smart Shopper",
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                            Spacer(Modifier.height(8.dp))
                            AssistChip(
                                onClick = {},
                                label = { Text("Shopper", fontSize = 11.sp) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = WiseUpColors.Green100,
                                    labelColor = WiseUpColors.Green600
                                ),
                                modifier = Modifier.height(24.dp)
                            )
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
                                selectedContainerColor = WiseUpColors.Green100,
                                selectedIconColor = WiseUpColors.Green600,
                                selectedTextColor = WiseUpColors.Green600
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
                                selectedContainerColor = WiseUpColors.Green100,
                                selectedIconColor = WiseUpColors.Green600,
                                selectedTextColor = WiseUpColors.Green600
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
                }
            }
        }
    }
}
