package dev.soloistdev.studenttracker.security

import android.content.Context
import com.scottyab.rootbeer.RootBeer

class IntegrityChecker(private val context: Context) {
    fun isDeviceRooted(): Boolean {
        val rootBeer = RootBeer(context)
        return rootBeer.isRooted
    }
}