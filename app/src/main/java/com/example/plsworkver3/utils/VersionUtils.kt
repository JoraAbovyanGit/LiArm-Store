package com.example.plsworkver3.utils

object VersionUtils {
    /**
     * Compare two version strings (e.g., "1.2.3" vs "1.2.4")
     * @return -1 if v1 < v2, 0 if v1 == v2, 1 if v1 > v2
     */
    fun compareVersions(v1: String?, v2: String?): Int {
        if (v1.isNullOrBlank() || v2.isNullOrBlank()) return 0
        
        val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }
        
        val maxLength = maxOf(parts1.size, parts2.size)
        
        for (i in 0 until maxLength) {
            val part1 = parts1.getOrNull(i) ?: 0
            val part2 = parts2.getOrNull(i) ?: 0
            
            when {
                part1 < part2 -> return -1
                part1 > part2 -> return 1
            }
        }
        
        return 0
    }
    
    /**
     * Check if version1 is newer than version2
     */
    fun isNewerVersion(v1: String?, v2: String?): Boolean {
        return compareVersions(v1, v2) > 0
    }
    
    /**
     * Check if version1 is older than version2
     */
    fun isOlderVersion(v1: String?, v2: String?): Boolean {
        return compareVersions(v1, v2) < 0
    }
}
