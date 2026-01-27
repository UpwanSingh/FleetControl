package com.fleetcontrol.data.sync

import android.util.Log
import kotlinx.coroutines.delay

/**
 * Cloud Sync Helper with Retry Logic
 * Provides reliable cloud operations with exponential backoff
 */
object CloudSyncHelper {
    
    private const val TAG = "CloudSyncHelper"
    private const val MAX_RETRIES = 3
    private const val INITIAL_DELAY_MS = 1000L
    
    /**
     * Execute a cloud operation with retry logic
     * Uses exponential backoff: 1s, 2s, 4s
     * 
     * @param operation Suspend function that returns a result
     * @param onSuccess Called with result on success
     * @param onFailure Called on final failure after all retries
     */
    suspend fun <T> withRetry(
        operationName: String,
        operation: suspend () -> T,
        onSuccess: suspend (T) -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ): T? {
        var lastException: Exception? = null
        var delayMs = INITIAL_DELAY_MS
        
        repeat(MAX_RETRIES) { attempt ->
            try {
                val result = operation()
                Log.d(TAG, "$operationName succeeded on attempt ${attempt + 1}")
                onSuccess(result)
                return result
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "$operationName failed (attempt ${attempt + 1}/$MAX_RETRIES): ${e.message}")
                
                if (attempt < MAX_RETRIES - 1) {
                    delay(delayMs)
                    delayMs *= 2 // Exponential backoff
                }
            }
        }
        
        Log.e(TAG, "$operationName failed after $MAX_RETRIES attempts")
        lastException?.let { onFailure(it) }
        return null
    }
    
    /**
     * Fire-and-forget cloud push with retry
     * Does not block, logs failures
     */
    suspend fun pushWithRetry(
        operationName: String,
        operation: suspend () -> String
    ): String? {
        return withRetry(
            operationName = operationName,
            operation = operation,
            onFailure = { e ->
                Log.e(TAG, "Cloud push permanently failed: $operationName - ${e.message}")
            }
        )
    }
}
