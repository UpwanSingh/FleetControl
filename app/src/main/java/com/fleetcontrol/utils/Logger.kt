package com.fleetcontrol.utils

import android.util.Log

/**
 * Logging utility
 */
object Logger {
    private const val TAG = "FleetControl"
    
    fun d(message: String) = Log.d(TAG, message)
    fun i(message: String) = Log.i(TAG, message)
    fun w(message: String) = Log.w(TAG, message)
    fun e(message: String, throwable: Throwable? = null) = Log.e(TAG, message, throwable)
}
