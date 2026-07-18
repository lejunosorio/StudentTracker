package dev.soloistdev.studenttracker

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class StudentTrackerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}