package com.gbaoperator.plugin.data

/**
 * Data class representing information about a GBA cartridge
 * Contains metadata extracted from the cartridge header and detected properties
 */
data class CartridgeInfo(
    /**
     * Game title extracted from the cartridge header
     */
    val title: String,
    
    /**
     * 4-character game code identifier
     */
    val gameCode: String,
    
    /**
     * 2-character maker/publisher code
     */
    val makerCode: String,
    
    /**
     * Game version number
     */
    val version: Int,
    
    /**
     * ROM size in bytes
     */
    val romSize: Int,
    
    /**
     * Save memory size in bytes, null if no save memory or unknown
     */
    val saveSize: Int?
) {
    /**
     * Get a human-readable ROM size string
     */
    fun getFormattedRomSize(): String {
        val sizeInMB = romSize / (1024 * 1024)
        return "${sizeInMB}MB"
    }
    
    /**
     * Get a human-readable save size string
     */
    fun getFormattedSaveSize(): String {
        return when (saveSize) {
            null -> "Unknown"
            0 -> "No Save"
            else -> {
                when {
                    saveSize >= 1024 * 1024 -> "${saveSize / (1024 * 1024)}MB"
                    saveSize >= 1024 -> "${saveSize / 1024}KB"
                    else -> "${saveSize}B"
                }
            }
        }
    }
    
    /**
     * Check if the cartridge has save memory
     */
    fun hasSave(): Boolean = saveSize != null && saveSize > 0
}