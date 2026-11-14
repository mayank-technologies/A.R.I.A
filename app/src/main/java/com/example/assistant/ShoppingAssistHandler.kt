package com.example.assistant

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

data class ShoppingDeepLinkConfig(
    val appKey: String,
    val displayName: String,
    val packageName: String,
    val deepLinkSearchTemplate: String?,
    val webSearchTemplate: String,
    val fallbackUrl: String
)

sealed class ShoppingAssistResult {
    data class OpenedWithSearch(
        val appName: String,
        val productQuery: String,
        val openedUri: String,
        val isNativeApp: Boolean,
        val message: String
    ) : ShoppingAssistResult()

    data class OpenedAppOnly(
        val appName: String,
        val productQuery: String,
        val fallbackSearchUrl: String,
        val message: String
    ) : ShoppingAssistResult()

    data class Failure(val message: String) : ShoppingAssistResult()
}

/**
 * ShoppingAssistHandler:
 * Supports voice commands such as:
 * - "Flipkart se 500g sugar order karo"
 * - "Flipkart Minutes me milk aur bread search karo"
 * - "Blinkit se 1kg aata mangwao"
 * - "Zepto se cold drink order karo"
 * - "Amazon par wireless mouse search karo"
 *
 * SECURITY & POLICY BOUNDARY:
 * ARC ONLY opens the shopping app and pre-populates the product search deep-link.
 * ARC NEVER places an order or triggers checkout/payment automatically. Final
 * add-to-cart, checkout confirmation, and payment are ALWAYS done by the user.
 */
object ShoppingAssistHandler {

    private const val TAG = "ShoppingAssistHandler"

    val supportedStores = listOf(
        ShoppingDeepLinkConfig(
            appKey = "flipkart minutes",
            displayName = "Flipkart Minutes",
            packageName = "com.flipkart.android",
            deepLinkSearchTemplate = "flipkart://search?q=%s",
            webSearchTemplate = "https://www.flipkart.com/search?q=%s",
            fallbackUrl = "https://www.flipkart.com"
        ),
        ShoppingDeepLinkConfig(
            appKey = "flipkart",
            displayName = "Flipkart",
            packageName = "com.flipkart.android",
            deepLinkSearchTemplate = "flipkart://search?q=%s",
            webSearchTemplate = "https://www.flipkart.com/search?q=%s",
            fallbackUrl = "https://www.flipkart.com"
        ),
        ShoppingDeepLinkConfig(
            appKey = "blinkit",
            displayName = "Blinkit",
            packageName = "com.grofers.customerapp",
            deepLinkSearchTemplate = "blinkit://search?q=%s",
            webSearchTemplate = "https://blinkit.com/s/?q=%s",
            fallbackUrl = "https://blinkit.com"
        ),
        ShoppingDeepLinkConfig(
            appKey = "zepto",
            displayName = "Zepto",
            packageName = "com.zeptoconsumerapp",
            deepLinkSearchTemplate = "zepto://search?query=%s",
            webSearchTemplate = "https://www.zeptonow.com/search?query=%s",
            fallbackUrl = "https://www.zeptonow.com"
        ),
        ShoppingDeepLinkConfig(
            appKey = "instamart",
            displayName = "Swiggy Instamart",
            packageName = "in.swiggy.android",
            deepLinkSearchTemplate = "swiggy://store-search?query=%s",
            webSearchTemplate = "https://www.swiggy.com/instamart/search?query=%s",
            fallbackUrl = "https://www.swiggy.com/instamart"
        ),
        ShoppingDeepLinkConfig(
            appKey = "amazon",
            displayName = "Amazon",
            packageName = "com.amazon.mShop.android.shopping",
            deepLinkSearchTemplate = "com.amazon.mobile.shopping.web://amazon.in/s?k=%s",
            webSearchTemplate = "https://www.amazon.in/s?k=%s",
            fallbackUrl = "https://www.amazon.in"
        ),
        ShoppingDeepLinkConfig(
            appKey = "swiggy",
            displayName = "Swiggy",
            packageName = "in.swiggy.android",
            deepLinkSearchTemplate = "swiggy://explore?query=%s",
            webSearchTemplate = "https://www.swiggy.com/search?query=%s",
            fallbackUrl = "https://www.swiggy.com"
        ),
        ShoppingDeepLinkConfig(
            appKey = "zomato",
            displayName = "Zomato",
            packageName = "com.application.zomato",
            deepLinkSearchTemplate = null,
            webSearchTemplate = "https://www.zomato.com/search?q=%s",
            fallbackUrl = "https://www.zomato.com"
        ),
        ShoppingDeepLinkConfig(
            appKey = "myntra",
            displayName = "Myntra",
            packageName = "com.myntra.android",
            deepLinkSearchTemplate = "myntra://search/%s",
            webSearchTemplate = "https://www.myntra.com/%s",
            fallbackUrl = "https://www.myntra.com"
        )
    )

    fun isShoppingOrderCommand(queryLower: String): Boolean {
        val mentionsStore = supportedStores.any { queryLower.contains(it.appKey) }
        val hasShoppingIntent = queryLower.contains("order") ||
            queryLower.contains("mangwao") ||
            queryLower.contains("mangao") ||
            queryLower.contains("kharid") ||
            queryLower.contains("buy") ||
            queryLower.contains("cart") ||
            queryLower.contains("search") ||
            queryLower.contains("khojo") ||
            queryLower.contains("minutes")
        return mentionsStore && hasShoppingIntent
    }

    /**
     * Parses target store + clean product search query from natural Hinglish/English voice text.
     * Example: "Flipkart se 500g sugar order karo" -> Pair(Flipkart, "500g sugar")
     */
    fun extractStoreAndProduct(rawQuery: String): Pair<ShoppingDeepLinkConfig, String> {
        val lower = rawQuery.lowercase().trim()
        val store = supportedStores.firstOrNull { lower.contains(it.appKey) }
            ?: supportedStores.first { it.appKey == "flipkart" }

        var product = rawQuery
            .replace(Regex("(?i)\\b${Regex.escape(store.appKey)}\\b"), " ")
            .replace(Regex("(?i)\\b(flipkart minutes|flipkart|amazon|blinkit|zepto|swiggy instamart|instamart|swiggy|zomato|myntra)\\b"), " ")
            .replace(Regex("(?i)\\b(order karo|order kar do|order|mangwao|mangao|kharido|kharidna hai|buy|search karo|search for|search|khojo|dhoondho|khole|kholo|open|app|par|pe|me|mein|se|on|from|for|please|pls|arc)\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (product.isBlank()) {
            product = "groceries"
        }
        return Pair(store, product)
    }

    /**
     * Builds the shopping search deep-link URI + web fallback URL for a given store and product.
     */
    fun buildSearchUris(store: ShoppingDeepLinkConfig, productQuery: String): Pair<String?, String> {
        val encoded = Uri.encode(productQuery.trim())
        val deepLink = store.deepLinkSearchTemplate?.let { String.format(it, encoded) }
        val webUrl = String.format(store.webSearchTemplate, encoded)
        return Pair(deepLink, webUrl)
    }

    fun processShoppingCommand(
        context: Context,
        rawQuery: String,
        explicitStoreName: String? = null,
        explicitProductQuery: String? = null
    ): ShoppingAssistResult {
        val parsed = extractStoreAndProduct(rawQuery)
        val store = if (!explicitStoreName.isNullOrBlank()) {
            val sLower = explicitStoreName.lowercase().trim()
            supportedStores.firstOrNull { sLower.contains(it.appKey) || it.displayName.lowercase().contains(sLower) }
                ?: parsed.first
        } else {
            parsed.first
        }
        val productQuery = explicitProductQuery?.trim()?.takeIf { it.isNotBlank() } ?: parsed.second

        val (deepLinkUri, webSearchUrl) = buildSearchUris(store, productQuery)
        val isInstalled = SmartAppOpener.isAppInstalled(context, store.packageName)

        if (isInstalled) {
            // 1. Try app custom search deep-link (e.g. flipkart://search?q=500g%20sugar)
            if (!deepLinkUri.isNullOrBlank()) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(deepLinkUri)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        setPackage(store.packageName)
                    }
                    if (intent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(intent)
                        return ShoppingAssistResult.OpenedWithSearch(
                            appName = store.displayName,
                            productQuery = productQuery,
                            openedUri = deepLinkUri,
                            isNativeApp = true,
                            message = "${store.displayName} khol diya hai aur '$productQuery' search kar diya hai. Cart me add karke final confirm aur payment aap khud kijiyega."
                        )
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Custom deep-link scheme failed for ${store.displayName}: ${e.message}")
                }
            }

            // 2. Try Android App Link (https://... directed to the installed store package)
            try {
                val appLinkIntent = Intent(Intent.ACTION_VIEW, Uri.parse(webSearchUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    setPackage(store.packageName)
                }
                if (appLinkIntent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(appLinkIntent)
                    return ShoppingAssistResult.OpenedWithSearch(
                        appName = store.displayName,
                        productQuery = productQuery,
                        openedUri = webSearchUrl,
                        isNativeApp = true,
                        message = "${store.displayName} app me '$productQuery' search khol diya hai. Cart me add karke final confirm aur payment aap khud karenge."
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "App Link failed for ${store.displayName}: ${e.message}")
            }

            // 3. Fallback: Open the native app directly and tell user what to search & add
            try {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(store.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    return ShoppingAssistResult.OpenedAppOnly(
                        appName = store.displayName,
                        productQuery = productQuery,
                        fallbackSearchUrl = webSearchUrl,
                        message = "${store.displayName} khol diya hai, '$productQuery' search kar ke cart me add karo — final order confirm aur payment aap khud karenge."
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Native launch failed for ${store.displayName}: ${e.message}")
            }
        }

        // 4. App not installed or native launch failed -> external Chrome browser search
        SmartAppOpener.launchExternalBrowser(context, webSearchUrl)
        return ShoppingAssistResult.OpenedWithSearch(
            appName = store.displayName,
            productQuery = productQuery,
            openedUri = webSearchUrl,
            isNativeApp = false,
            message = "${store.displayName} browser me '$productQuery' search khol diya hai. Cart me add karke final confirm aur payment aap khud kijiyega."
        )
    }
}
