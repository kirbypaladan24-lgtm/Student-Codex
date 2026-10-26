package com.example.studentcodex

import android.app.Application
import com.example.studentcodex.di.AppContainer

class StudentCodexApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
