package com.example.plsworkver3.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.plsworkver3.data.AppInfo

class AppRecyclerAdapter(
    private val appList: List<AppInfo>,
    private val layoutManager: DynamicLayoutManager,
    private val onButtonClick: (AppInfo) -> Unit
) : RecyclerView.Adapter<AppRecyclerAdapter.AppViewHolder>() {

    inner class AppViewHolder(val cardView: android.view.View) : RecyclerView.ViewHolder(cardView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        // Create a placeholder app info for card creation if list is empty
        val placeholderApp = appList.getOrNull(0) ?: AppInfo(
            appName = "Loading...",
            appIcon = "",
            packageName = "",
            downloadUrl = "",
            appDescription = "",
            appVersion = ""
        )
        val cardView = layoutManager.createAppCard(parent, placeholderApp) { }
        return AppViewHolder(cardView)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val appInfo = appList[position]
        layoutManager.updateAppCardView(holder.cardView, appInfo, onButtonClick)
    }

    override fun getItemCount(): Int = appList.size
}
