package com.xinyuan.gas

import android.app.Application
import com.xinyuan.gas.data.db.AppDatabase
import com.xinyuan.gas.reminder.RectificationReminder

class GasApp : Application() {
    val database by lazy { AppDatabase.getInstance(this) }

    override fun onCreate() {
        super.onCreate()
        RectificationReminder.ensureScheduled(this)
    }
}
