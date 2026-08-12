package com.example.assistant

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

/**
 * Data class representing an Application Target for ARIA Assistant.
 * @param appName Display name of the app (e.g. "Flipkart")
 * @param packageName Android package identifier (e.g. "com.flipkart.android")
 * @param fallbackUrl Fallback website URL if app is not installed (e.g. "https://www.flipkart.com")
 * @param searchUrlTemplate Search URL template for web browser (e.g. "https://www.flipkart.com/search?q=%s")
 * @param deepLinkSearchUri Deep link URI format for native app search (e.g. "flipkart://search?q=%s")
 */
data class AppTargetInfo(
    val appName: String,
    val packageName: String,
    val fallbackUrl: String,
    val searchUrlTemplate: String? = null,
    val deepLinkSearchUri: String? = null
)

/**
 * Result of attempting to launch or open an app/search.
 */
sealed class AppLaunchResult {
    data class NativeAppOpened(val appName: String, val message: String) : AppLaunchResult()
    data class ExternalBrowserOpened(val title: String, val url: String, val message: String) : AppLaunchResult()
    data class SearchExecuted(val platformName: String, val query: String, val isNative: Boolean, val message: String) : AppLaunchResult()
}

/**
 * SmartAppOpener handles multi-step voice command parsing, native package launching,
 * deep-linking, and external browser (Chrome) search execution for A.R.I.A.
 */
object SmartAppOpener {

    private const val TAG = "ARIA_SMART_APP_OPENER"

    // Dictionary mapping app/site keywords to AppTargetInfo
    val appDictionary = mapOf(
        "flipkart" to AppTargetInfo(
            appName = "Flipkart",
            packageName = "com.flipkart.android",
            fallbackUrl = "https://www.flipkart.com",
            searchUrlTemplate = "https://www.flipkart.com/search?q=%s",
            deepLinkSearchUri = "flipkart://search?q=%s"
        ),
        "amazon" to AppTargetInfo(
            appName = "Amazon",
            packageName = "com.amazon.mShop.android.shopping",
            fallbackUrl = "https://www.amazon.in",
            searchUrlTemplate = "https://www.amazon.in/s?k=%s",
            deepLinkSearchUri = "https://www.amazon.in/s?k=%s"
        ),
        "youtube" to AppTargetInfo(
            appName = "YouTube",
            packageName = "com.google.android.youtube",
            fallbackUrl = "https://www.youtube.com",
            searchUrlTemplate = "https://www.youtube.com/results?search_query=%s",
            deepLinkSearchUri = "vnd.youtube://results?search_query=%s"
        ),
        "myntra" to AppTargetInfo(
            appName = "Myntra",
            packageName = "com.myntra.android",
            fallbackUrl = "https://www.myntra.com",
            searchUrlTemplate = "https://www.myntra.com/%s",
            deepLinkSearchUri = "myntra://search/%s"
        ),
        "zomato" to AppTargetInfo(
            appName = "Zomato",
            packageName = "com.application.zomato",
            fallbackUrl = "https://www.zomato.com",
            searchUrlTemplate = "https://www.zomato.com/search?q=%s"
        ),
        "swiggy" to AppTargetInfo(
            appName = "Swiggy",
            packageName = "in.swiggy.android",
            fallbackUrl = "https://www.swiggy.com",
            searchUrlTemplate = "https://www.swiggy.com/search?query=%s"
        ),
        "whatsapp" to AppTargetInfo(
            appName = "WhatsApp",
            packageName = "com.whatsapp",
            fallbackUrl = "https://web.whatsapp.com"
        ),
        "facebook" to AppTargetInfo(
            appName = "Facebook",
            packageName = "com.facebook.katana",
            fallbackUrl = "https://facebook.com"
        ),
        "fb" to AppTargetInfo(
            appName = "Facebook",
            packageName = "com.facebook.katana",
            fallbackUrl = "https://facebook.com"
        ),
        "newpipe" to AppTargetInfo(
            appName = "NewPipe",
            packageName = "org.schabi.newpipe",
            fallbackUrl = "https://newpipe.net"
        ),
        "new pipe" to AppTargetInfo(
            appName = "NewPipe",
            packageName = "org.schabi.newpipe",
            fallbackUrl = "https://newpipe.net"
        ),
        "instagram" to AppTargetInfo(
            appName = "Instagram",
            packageName = "com.instagram.android",
            fallbackUrl = "https://www.instagram.com"
        ),
        "telegram" to AppTargetInfo(
            appName = "Telegram",
            packageName = "org.telegram.messenger",
            fallbackUrl = "https://web.telegram.org"
        ),
        "twitter" to AppTargetInfo(
            appName = "X (Twitter)",
            packageName = "com.twitter.android",
            fallbackUrl = "https://twitter.com"
        ),
        "gmail" to AppTargetInfo(
            appName = "Gmail",
            packageName = "com.google.android.gm",
            fallbackUrl = "https://mail.google.com"
        ),
        "chrome" to AppTargetInfo(
            appName = "Google Chrome",
            packageName = "com.android.chrome",
            fallbackUrl = "https://www.google.com",
            searchUrlTemplate = "https://www.google.com/search?q=%s"
        ),
        "google" to AppTargetInfo(
            appName = "Google Search",
            packageName = "com.google.android.googlequicksearchbox",
            fallbackUrl = "https://www.google.com",
            searchUrlTemplate = "https://www.google.com/search?q=%s"
        ),
        "maps" to AppTargetInfo(
            appName = "Google Maps",
            packageName = "com.google.android.apps.maps",
            fallbackUrl = "https://maps.google.com",
            searchUrlTemplate = "https://www.google.com/maps/search/%s"
        )
    )

    /**
     * Checks whether an Android package is installed on the device.
     */
    fun isAppInstalled(context: Context, packageName: String): Boolean {
        val launchIntent = try {
            context.packageManager.getLaunchIntentForPackage(packageName)
        } catch (e: Exception) {
            Log.e(TAG, "[INSTALLED CHECK ERROR] Error checking package '$packageName': ${e.message}", e)
            null
        }
        val isInstalled = launchIntent != null
        Log.d(TAG, "[INSTALLED CHECK] Package '$packageName' -> isInstalled: $isInstalled")
        return isInstalled
    }

    /**
     * Generic Fallback: Scans device's installed applications list for name-based fuzzy match.
     * Useful when user asks to open an app that is not present in appDictionary (e.g. NewPipe, rare apps).
     */
    fun findInstalledAppByLabel(context: Context, query: String): AppTargetInfo? {
        val cleanQuery = query.lowercase().trim()
        val normalizedQuery = cleanQuery.replace("\\s+".toRegex(), "")
        if (normalizedQuery.isBlank()) return null

        Log.d(TAG, "[GENERIC SCAN] Searching all installed apps for query: '$query' (normalized: '$normalizedQuery')")

        try {
            val pm = context.packageManager
            val installedApps = pm.getInstalledApplications(0)

            for (appInfo in installedApps) {
                val pkgName = appInfo.packageName
                // Check if app has an intent to launch
                val launchIntent = pm.getLaunchIntentForPackage(pkgName) ?: continue

                val appLabel = appInfo.loadLabel(pm).toString()
                val cleanLabel = appLabel.lowercase().trim()
                val normalizedLabel = cleanLabel.replace("\\s+".toRegex(), "")

                if (cleanLabel == cleanQuery ||
                    normalizedLabel == normalizedQuery ||
                    cleanLabel.contains(cleanQuery) ||
                    normalizedLabel.contains(normalizedQuery) ||
                    normalizedQuery.contains(normalizedLabel)
                ) {
                    Log.d(TAG, "[GENERIC SCAN MATCH] Found App: '$appLabel' ($pkgName) matching '$query'")
                    return AppTargetInfo(
                        appName = appLabel,
                        packageName = pkgName,
                        fallbackUrl = "https://www.google.com/search?q=" + Uri.encode(appLabel)
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "[GENERIC SCAN ERROR] ${e.message}", e)
        }

        Log.d(TAG, "[GENERIC SCAN] No installed app matched query '$query'")
        return null
    }

    /**
     * BUG 1 FIX: Launches a URL explicitly in the external Chrome browser (or default system browser),
     * completely bypassing in-app embedded WebViews.
     */
    fun launchExternalBrowser(context: Context, url: String): Boolean {
        return try {
            val uri = Uri.parse(url)
            val chromeIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                setPackage("com.android.chrome")
            }
            context.startActivity(chromeIntent)
            Log.d(TAG, "[DECISION] Launching External Chrome Browser -> URL: $url")
            true
        } catch (e: Exception) {
            try {
                val defaultBrowserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(defaultBrowserIntent)
                Log.d(TAG, "[DECISION] Launching Default External Browser -> URL: $url")
                true
            } catch (ex: Exception) {
                Log.e(TAG, "Failed to launch any external browser for URL: $url", ex)
                false
            }
        }
    }

    /**
     * BUG 2 FIX: Multi-step voice command parser & smart search/app opener.
     * 
     * Handles voice commands such as:
     * - "open chrome and search flipkart and search oversize tshirt for boys"
     * - "open chrome and search oversize tshirt for boys"
     * - "search flipkart for running shoes"
     * - "open youtube and search kotlin tutorial"
     * - "open newpipe" / "open facebook"
     */
    fun processVoiceCommand(context: Context, rawQuery: String): AppLaunchResult {
        val queryLower = rawQuery.lowercase().trim()

        Log.d(TAG, "==========================================================")
        Log.d(TAG, "[VOICE COMMAND RECEIVED] Raw Query: '$rawQuery'")

        // Step 1: Split multi-step commands by conjunction words ("and", "aur", "&", "then", "phir")
        val conjunctionRegex = "(?i)\\b(and|aur|\\&|then|phir)\\b".toRegex()
        val segments = queryLower.split(conjunctionRegex)
            .map { it.trim() }
            .filter { it.isNotBlank() }

        var targetPlatformKey: String? = null
        var searchQuery: String? = null

        // Step 2: Iterate over segments to detect platform target and search query
        for (segment in segments) {
            val cleanSeg = segment
                .replace("open ", "")
                .replace("kholo ", "")
                .replace("launch ", "")
                .replace("chalao ", "")
                .trim()

            val normSeg = cleanSeg.replace("\\s+".toRegex(), "")

            // Check if segment mentions a specific target platform in dictionary
            for (entryKey in appDictionary.keys) {
                val normKey = entryKey.replace("\\s+".toRegex(), "")
                if (entryKey != "chrome" && entryKey != "google") {
                    if (cleanSeg.contains(entryKey) || normSeg.contains(normKey)) {
                        targetPlatformKey = entryKey
                        break
                    }
                }
            }

            // Extract search query if "search" / "khojo" / "dhoondho" keywords are present
            if (cleanSeg.contains("search") || cleanSeg.contains("khojo") || cleanSeg.contains("dhoondho")) {
                var extractedQuery = cleanSeg
                    .replace("search for", "")
                    .replace("search in", "")
                    .replace("search on", "")
                    .replace("search", "")
                    .replace("khojo", "")
                    .replace("dhoondho", "")
                    .trim()

                targetPlatformKey?.let { key ->
                    extractedQuery = extractedQuery.replace(key, "", ignoreCase = true).trim()
                }
                extractedQuery = extractedQuery.replace("chrome", "", ignoreCase = true)
                    .replace("google", "", ignoreCase = true)
                    .replace("browser", "", ignoreCase = true)
                    .trim()

                if (extractedQuery.isNotBlank()) {
                    searchQuery = extractedQuery
                }
            }
        }

        Log.d(TAG, "[PARSED VOICE COMMAND] Target Platform: '$targetPlatformKey', Search Term: '$searchQuery'")

        // Step 3: Action Execution Logic

        // Case A: Specific merchant/platform app specified with a search query
        if (targetPlatformKey != null && appDictionary.containsKey(targetPlatformKey)) {
            val targetInfo = appDictionary[targetPlatformKey]!!

            if (!searchQuery.isNullOrBlank()) {
                val encodedQuery = Uri.encode(searchQuery)
                val isInstalled = isAppInstalled(context, targetInfo.packageName)

                if (isInstalled && !targetInfo.deepLinkSearchUri.isNullOrBlank()) {
                    val deepLink = String.format(targetInfo.deepLinkSearchUri, encodedQuery)
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(deepLink)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            setPackage(targetInfo.packageName)
                        }
                        context.startActivity(intent)
                        val msg = "Opening ${targetInfo.appName} app to search for '$searchQuery', Boss!"
                        Log.d(TAG, "[DECISION] Launching Native DeepLink Search in ${targetInfo.appName}")
                        return AppLaunchResult.SearchExecuted(targetInfo.appName, searchQuery, true, msg)
                    } catch (e: Exception) {
                        Log.e(TAG, "Deep link failed for ${targetInfo.appName}, falling back to web search", e)
                    }
                }

                // If not installed or deep link fails -> Open direct search URL in external Chrome browser
                val webSearchUrl = if (targetInfo.searchUrlTemplate != null) {
                    String.format(targetInfo.searchUrlTemplate, encodedQuery)
                } else {
                    "https://www.google.com/search?q=" + Uri.encode("${targetInfo.appName} $searchQuery")
                }

                launchExternalBrowser(context, webSearchUrl)
                val msg = "Opening ${targetInfo.appName} search for '$searchQuery' in external Chrome browser, Boss!"
                return AppLaunchResult.SearchExecuted(targetInfo.appName, searchQuery, false, msg)
            } else {
                return smartLaunchApp(context, targetPlatformKey)
            }
        }

        // Case B: Search query specified without merchant app
        if (!searchQuery.isNullOrBlank()) {
            val encodedQuery = Uri.encode(searchQuery)
            val googleSearchUrl = "https://www.google.com/search?q=$encodedQuery"
            launchExternalBrowser(context, googleSearchUrl)

            val msg = "Searching Google in Chrome for '$searchQuery', Boss!"
            return AppLaunchResult.SearchExecuted("Google", searchQuery, false, msg)
        }

        // Case C: Standard single app launch command without search query (e.g. "open chrome", "open whatsapp", "open newpipe")
        return smartLaunchApp(context, rawQuery)
    }

    /**
     * Launches single application or opens fallback URL in external browser.
     */
    fun smartLaunchApp(context: Context, appQuery: String): AppLaunchResult {
        val cleanQuery = appQuery.lowercase().trim()
            .replace("open ", "")
            .replace("kholo ", "")
            .replace("launch ", "")
            .replace("chalao ", "")
            .trim()

        val normalizedCleanQuery = cleanQuery.replace("\\s+".toRegex(), "")

        Log.d(TAG, "[SMART LAUNCH SEARCH] App Query: '$appQuery' | Clean: '$cleanQuery' | Normalized: '$normalizedCleanQuery'")

        // 1. Try matching in static appDictionary (exact, normalized, or substring)
        var matchedTarget: AppTargetInfo? = null

        val matchedEntry = appDictionary.entries.find { (key, _) ->
            val normKey = key.replace("\\s+".toRegex(), "")
            cleanQuery == key ||
                    normalizedCleanQuery == normKey ||
                    cleanQuery.contains(key) ||
                    normalizedCleanQuery.contains(normKey) ||
                    key.contains(cleanQuery) ||
                    normKey.contains(normalizedCleanQuery)
        }

        if (matchedEntry != null) {
            matchedTarget = matchedEntry.value
            Log.d(TAG, "[DICTIONARY MATCH FOUND] Key: '${matchedEntry.key}' -> App: '${matchedTarget.appName}' (${matchedTarget.packageName})")
        } else {
            // 2. Generic Fallback: Search device's installed applications list
            Log.d(TAG, "[DICTIONARY MISS] Query '$cleanQuery' not in appDictionary. Trying generic installed app scanner...")
            matchedTarget = findInstalledAppByLabel(context, cleanQuery)
        }

        // 3. Execution based on matched target
        if (matchedTarget != null) {
            val isInstalled = isAppInstalled(context, matchedTarget.packageName)

            if (isInstalled) {
                return try {
                    val launchIntent = context.packageManager.getLaunchIntentForPackage(matchedTarget.packageName)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(launchIntent)
                        val msg = "Opening ${matchedTarget.appName} app, Boss!"
                        Log.d(TAG, "[DECISION] App Found & Installed -> LAUNCHING NATIVE APP '${matchedTarget.appName}' (${matchedTarget.packageName})")
                        AppLaunchResult.NativeAppOpened(matchedTarget.appName, msg)
                    } else {
                        launchExternalBrowser(context, matchedTarget.fallbackUrl)
                        val msg = "${matchedTarget.appName} launch intent null tha. External Chrome browser me open kar rahi hu, Boss!"
                        Log.d(TAG, "[DECISION] Launch intent null -> Launching Browser URL '${matchedTarget.fallbackUrl}'")
                        AppLaunchResult.ExternalBrowserOpened(matchedTarget.appName, matchedTarget.fallbackUrl, msg)
                    }
                } catch (e: Exception) {
                    launchExternalBrowser(context, matchedTarget.fallbackUrl)
                    val msg = "${matchedTarget.appName} open karte waqt error aaya. Browser me open kar rahi hu."
                    Log.e(TAG, "[DECISION ERROR] Failed to start activity for ${matchedTarget.packageName}", e)
                    AppLaunchResult.ExternalBrowserOpened(matchedTarget.appName, matchedTarget.fallbackUrl, msg)
                }
            } else {
                launchExternalBrowser(context, matchedTarget.fallbackUrl)
                val msg = "${matchedTarget.appName} app device me installed nahi hai. External Chrome browser me web version open kar rahi hu, Boss!"
                Log.d(TAG, "[DECISION] App Not Installed -> LAUNCHING EXTERNAL BROWSER '${matchedTarget.fallbackUrl}'")
                return AppLaunchResult.ExternalBrowserOpened(matchedTarget.appName, matchedTarget.fallbackUrl, msg)
            }
        }

        // 4. Final Fallback: App not found anywhere in dictionary or installed list
        val encodedQuery = Uri.encode("open $cleanQuery")
        val searchUrl = "https://www.google.com/search?q=$encodedQuery"
        launchExternalBrowser(context, searchUrl)

        val unknownMsg = "Sorry, main '$cleanQuery' app ko nahi jaanti. Chrome browser me Google search kar rahi hu."
        Log.d(TAG, "[DECISION] App Not Found Anywhere -> LAUNCHING GOOGLE SEARCH IN CHROME '$searchUrl'")
        return AppLaunchResult.ExternalBrowserOpened(cleanQuery, searchUrl, unknownMsg)
    }
}
