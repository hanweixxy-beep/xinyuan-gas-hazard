package com.xinyuan.gas

import android.app.Application
import com.xinyuan.gas.data.db.AppDatabase

class GasApp : Application() {
    val database by lazy { AppDatabase.getInstance(this) }
}
