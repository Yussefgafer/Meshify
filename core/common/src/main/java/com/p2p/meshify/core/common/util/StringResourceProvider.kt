package com.p2p.meshify.core.common.util

/**
 * Interface for providing string resources in a platform-agnostic way.
 * This allows core:data module to access strings without directly depending on Android Context.
 */
interface StringResourceProvider {
    /**
     * Get a string resource by its ID.
     * @param resourceId The resource ID (e.g., R.string.some_string)
     * @param args Optional format arguments for formatted strings
     * @return The localized string
     */
    fun getString(resourceId: Int, vararg args: Any): String
}

/**
 * Android implementation of StringResourceProvider.
 * Should be instantiated in the app module and provided to repositories.
 */
class AndroidStringResourceProvider(
    private val context: android.content.Context
) : StringResourceProvider {
    override fun getString(resourceId: Int, vararg args: Any): String {
        return if (args.isNotEmpty()) {
            context.getString(resourceId, *args)
        } else {
            context.getString(resourceId)
        }
    }
}
