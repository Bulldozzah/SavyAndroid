package com.example.myapplication.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Profile(
    val id: String = "",
    @SerialName("user_id") val userId: String = "",
    @SerialName("display_name") val displayName: String? = null,
    val country: String? = null,
    val phone: String? = null,
    @SerialName("phone_area_code") val phoneAreaCode: String? = null,
    val currency: String? = null,
    @SerialName("profile_completed") val profileCompleted: Boolean = false,
    val email: String? = null,
    val whatsapp: String? = null,
    val contact: String? = null
)

@Serializable
data class ProfileUpsert(
    @SerialName("user_id") val userId: String,
    @SerialName("display_name") val displayName: String? = null,
    val country: String? = null,
    val phone: String? = null,
    @SerialName("phone_area_code") val phoneAreaCode: String? = null,
    val currency: String? = null,
    @SerialName("profile_completed") val profileCompleted: Boolean = true,
    val email: String? = null,
    val whatsapp: String? = null,
    val contact: String? = null
)

@Serializable
data class Product(
    val gtin: String,
    val description: String
)

@Serializable
data class ShoppingList(
    val id: String = "",
    @SerialName("user_id") val userId: String = "",
    val name: String = "",
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
    @SerialName("assigned_store_id") val assignedStoreId: String? = null,
    val budget: Double? = null
)

@Serializable
data class ShoppingListCreate(
    @SerialName("user_id") val userId: String,
    val name: String,
    val budget: Double? = null
)

@Serializable
data class ShoppingListItem(
    val id: String = "",
    @SerialName("shopping_list_id") val shoppingListId: String = "",
    @SerialName("product_gtin") val productGtin: String = "",
    val quantity: Int = 1
)

@Serializable
data class ShoppingListItemCreate(
    @SerialName("shopping_list_id") val shoppingListId: String,
    @SerialName("product_gtin") val productGtin: String,
    val quantity: Int = 1
)

@Serializable
data class Store(
    val id: String = "",
    @SerialName("hq_id") val hqId: String = "",
    val location: String = "",
    @SerialName("store_owner_id") val storeOwnerId: String? = null,
    val city: String? = null,
    val email: String? = null,
    val contact: String? = null,
    val whatsapp: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address: String? = null
)

@Serializable
data class StoreHq(
    val id: String = "",
    val name: String = ""
)

@Serializable
data class StorePrice(
    val id: String = "",
    @SerialName("store_id") val storeId: String = "",
    @SerialName("product_gtin") val productGtin: String = "",
    val price: Double = 0.0,
    @SerialName("in_stock") val inStock: Boolean = true,
    val verified: Boolean = false,
    @SerialName("verified_by") val verifiedBy: String? = null,
    @SerialName("updated_by") val updatedBy: String? = null,
    val source: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("unverified_price") val unverifiedPrice: Double? = null
)

@Serializable
data class StorePriceUpsert(
    @SerialName("store_id") val storeId: String,
    @SerialName("product_gtin") val productGtin: String,
    val price: Double,
    @SerialName("in_stock") val inStock: Boolean = true,
    val verified: Boolean = false,
    @SerialName("verified_by") val verifiedBy: String? = null,
    @SerialName("updated_by") val updatedBy: String,
    val source: String = "shopper",
    @SerialName("unverified_price") val unverifiedPrice: Double? = null
)

@Serializable
data class StoreFeedbackEntry(
    @SerialName("user_id") val userId: String,
    @SerialName("store_id") val storeId: String,
    val rating: Int,
    @SerialName("feedback_type") val feedbackType: String,
    val title: String? = null,
    val body: String
)

@Serializable
data class AdBanner(
    val id: String = "",
    val title: String = "",
    @SerialName("image_url") val imageUrl: String = "",
    @SerialName("link_url") val linkUrl: String = "",
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("display_order") val displayOrder: Int = 0,
    val description: String? = null
)

@Serializable
data class AdPromotion(
    val id: String = "",
    @SerialName("store_id") val storeId: String? = null,
    @SerialName("product_gtin") val productGtin: String? = null,
    @SerialName("promotional_price") val promotionalPrice: Double = 0.0,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("display_order") val displayOrder: Int = 0
)

@Serializable
data class AdGeneral(
    val id: String = "",
    val title: String = "",
    val description: String? = null,
    @SerialName("image_url") val imageUrl: String = "",
    @SerialName("link_url") val linkUrl: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("display_order") val displayOrder: Int = 0
)

@Serializable
data class UserRole(
    val id: String = "",
    @SerialName("user_id") val userId: String = "",
    val role: String = ""
)

data class StoreWithHq(
    val store: Store,
    val hqName: String
)

data class ComparisonResult(
    val store: Store,
    val hqName: String,
    val totalInStock: Double,
    val totalAll: Double,
    val itemsInStock: Int,
    val itemsMissing: Int,
    val priceMap: Map<String, StorePrice?>
)
