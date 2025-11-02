package com.example.plsworkver3

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import java.io.File

class DownloadCompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
        if (completedId <= 0) return

        val prefs = context.getSharedPreferences("download_meta", Context.MODE_PRIVATE)
        val expectedId = prefs.getLong("downloadId", -1)
        if (completedId != expectedId) return

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(completedId)
        val cursor = dm.query(query)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val status = if (statusIdx >= 0) cursor.getInt(statusIdx) else -1
                val uriIdx = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                val localUri = if (uriIdx >= 0) cursor.getString(uriIdx) else null
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    val fallbackPath = prefs.getString("filePath", null)
                    val pkg = prefs.getString("requestedPackage", "") ?: ""
                    val file = resolveDownloadedFile(context, localUri, fallbackPath)
                    if (file != null) {
                        val isXapk = file.name.lowercase().endsWith(".xapk")
                        if (isXapk) {
                            AppManager.installXapk(context, file, pkg)
                        } else {
                            AppManager.installApk(context, file, pkg)
                        }
                    } else {
                        Toast.makeText(context, "Downloaded file missing", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } finally {
            cursor?.close()
        }
    }

    private fun resolveDownloadedFile(context: Context, localUriString: String?, fallbackPath: String?): File? {
        if (!localUriString.isNullOrBlank()) {
            val uri = Uri.parse(localUriString)
            if ("file".equals(uri.scheme, true)) return File(uri.path ?: return null)
            if ("content".equals(uri.scheme, true)) {
                val input = context.contentResolver.openInputStream(uri) ?: return null
                val target = File(context.cacheDir, "dl_${System.currentTimeMillis()}_${uri.lastPathSegment ?: "file"}")
                input.use { ins -> target.outputStream().use { outs -> ins.copyTo(outs) } }
                return target
            }
        }
        return if (!fallbackPath.isNullOrBlank()) File(fallbackPath) else null
    }
}


