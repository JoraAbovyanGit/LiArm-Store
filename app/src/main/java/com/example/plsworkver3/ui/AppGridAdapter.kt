package com.example.plsworkver3.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import com.example.plsworkver3.AppManager
import com.example.plsworkver3.R
import com.example.plsworkver3.data.AppInfo

class AppGridAdapter(
    private val context: Context,
    private val appList: List<AppInfo>,
    private val onButtonClick: (AppInfo) -> Unit
) : BaseAdapter() {

    private val layoutManager = DynamicLayoutManager(context)

    override fun getCount(): Int = appList.size

    override fun getItem(position: Int): AppInfo = appList[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val appInfo = appList[position]
        val cardView = convertView ?: layoutManager.createAppCard(
            parent!!,
            appInfo,
            onButtonClick
        )
        
        // Always update the view content to prevent showing stale data when recycling views
        layoutManager.updateAppCardView(cardView, appInfo, onButtonClick)
        
        return cardView
    }
}

