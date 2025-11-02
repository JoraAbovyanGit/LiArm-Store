package com.example.plsworkver3.installation

import android.content.Context
import android.os.Environment
import android.widget.Toast
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object XapkInstaller {

    fun isXapk(urlOrName: String): Boolean {
        return urlOrName.lowercase().endsWith(".xapk")
    }

    // Preferred: extract from a File path (avoids content resolver issues for file://)
    fun extractXapk(context: Context, xapkFile: File): File? {
        return try {
            val targetDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "xapk_extract_${System.currentTimeMillis()}"
            )
            if (!targetDir.exists()) targetDir.mkdirs()

            FileInputStream(xapkFile).use { fis ->
                ZipInputStream(fis).use { zis ->
                    var entry: ZipEntry? = zis.nextEntry
                    while (entry != null) {
                        val outFile = File(targetDir, entry.name)
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            outFile.outputStream().use { out ->
                                zis.copyTo(out)
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }
            targetDir
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to extract XAPK: ${e.message}", Toast.LENGTH_SHORT).show()
            null
        }
    }

    fun findApkParts(extractDir: File): List<File> {
        val apks = mutableListOf<File>()
        extractDir.walkTopDown().forEach { file ->
            if (file.isFile && file.name.endsWith(".apk", ignoreCase = true)) {
                apks.add(file)
            }
        }
        return apks.sortedBy { it.name }
    }
}


