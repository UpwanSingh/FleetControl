package com.fleetcontrol

import android.app.Application
import com.fleetcontrol.core.AppInitializer

/**
 * Main Application class for FleetControl
 */
class FleetControlApp : Application() {
    
    override fun onCreate() {
        super.onCreate()
        setInstance(this)
        // Initialize app components
        // AppInitializer.initialize(this)
    }
    
    companion object {
        @Volatile
        private var _instance: FleetControlApp? = null
        
        val instance: FleetControlApp
            get() = _instance ?: throw IllegalStateException("FleetControlApp not initialized")
        
        fun setInstance(app: FleetControlApp) {
            _instance = app
        }
    }
}
