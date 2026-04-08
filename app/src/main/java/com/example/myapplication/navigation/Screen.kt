package com.example.myapplication.navigation

sealed class Screen(val route: String) {
    object Auth : Screen("auth")
    object ProfileSetup : Screen("profile_setup")
    object Dashboard : Screen("dashboard")
    object SearchProducts : Screen("search_products")
    object ComparePrices : Screen("compare_prices")
    object ShoppingLists : Screen("shopping_lists")
    object StoreFeedback : Screen("store_feedback")
    object Profile : Screen("profile")
    object About : Screen("about")
    object Contact : Screen("contact")
    object BrowseStore : Screen("browse_store")
    object BarcodeScanner : Screen("barcode_scanner")
    object ProductDetail : Screen("product_detail/{gtin}") {
        fun createRoute(gtin: String) = "product_detail/$gtin"
    }
}
