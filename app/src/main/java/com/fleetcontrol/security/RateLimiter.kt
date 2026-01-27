package com.fleetcontrol.security

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.min

/**
 * Rate limiter for preventing abuse and protecting system resources
 * 
 * Features:
 * - Token bucket algorithm for smooth rate limiting
 * - Multiple rate limit strategies
 * - Per-user and global limits
 * - Automatic cleanup of inactive users
 */
class RateLimiter(
    private val maxRequests: Int,
    private val windowMs: Long,
    private val refillRate: Int = maxRequests,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    
    private val userBuckets = ConcurrentHashMap<String, TokenBucket>()
    private val cleanupJob = scope.launch {
        while (isActive) {
            delay(windowMs)
            cleanup()
        }
    }
    
    data class TokenBucket(
        var tokens: Int,
        var lastRefill: Long,
        val maxTokens: Int,
        val refillRate: Int
    )
    
    /**
     * Check if request is allowed
     */
    fun isAllowed(userId: String): Boolean {
        val now = System.currentTimeMillis()
        val bucket = userBuckets.computeIfAbsent(userId) {
            TokenBucket(maxRequests, now, maxRequests, refillRate)
        }
        
        // Refill tokens based on time elapsed
        val timeSinceLastRefill = now - bucket.lastRefill
        val tokensToAdd = (timeSinceLastRefill / windowMs) * refillRate
        
        bucket.tokens = kotlin.math.min(bucket.tokens + tokensToAdd.toInt(), bucket.maxTokens)
        bucket.lastRefill = now
        
        return if (bucket.tokens > 0) {
            bucket.tokens--
            true
        } else {
            false
        }
    }
    
    /**
     * Get remaining tokens for user
     */
    fun getRemainingTokens(userId: String): Int {
        val bucket = userBuckets[userId] ?: return maxRequests
        val now = System.currentTimeMillis()
        val timeSinceLastRefill = now - bucket.lastRefill
        val tokensToAdd = (timeSinceLastRefill / windowMs) * refillRate
        
        return kotlin.math.min(bucket.tokens + tokensToAdd.toInt(), bucket.maxTokens)
    }
    
    /**
     * Reset user's token bucket
     */
    fun resetUser(userId: String) {
        userBuckets.remove(userId)
    }
    
    /**
     * Cleanup inactive users
     */
    private fun cleanup() {
        val now = System.currentTimeMillis()
        val threshold = windowMs * 2 // Remove users inactive for 2 windows
        
        userBuckets.entries.removeIf { (_, bucket) ->
            now - bucket.lastRefill > threshold
        }
    }
    
    /**
     * Get statistics
     */
    fun getStats(): RateLimitStats {
        return RateLimitStats(
            totalUsers = userBuckets.size,
            maxRequests = maxRequests,
            windowMs = windowMs,
            refillRate = refillRate
        )
    }
    
    fun cancel() {
        cleanupJob.cancel()
        userBuckets.clear()
    }
}

/**
 * Rate limit statistics
 */
data class RateLimitStats(
    val totalUsers: Int,
    val maxRequests: Int,
    val windowMs: Long,
    val refillRate: Int
)

/**
 * Rate limit configurations
 */
object RateLimitConfigs {
    // Authentication limits
    val LOGIN_ATTEMPTS = RateLimitConfig(
        maxRequests = 5,
        windowMs = 15_000, // 15 minutes
        refillRate = 1, // Refill 1 token per 15 minutes
        description = "Login attempts"
    )
    
    val PASSWORD_RESET = RateLimitConfig(
        maxRequests = 3,
        windowMs = 60_000 * 60, // 1 hour
        refillRate = 1, // Refill 1 token per hour
        description = "Password reset requests"
    )
    
    val REGISTRATION = RateLimitConfig(
        maxRequests = 3,
        windowMs = 60_000 * 60, // 1 hour
        refillRate = 1, // Refill 1 token per hour
        description = "Account registration"
    )
    
    // API limits
    val API_REQUESTS = RateLimitConfig(
        maxRequests = 100,
        windowMs = 60_000, // 1 minute
        refillRate = 10, // Refill 10 tokens per minute
        description = "General API requests"
    )
    
    val DATA_EXPORT = RateLimitConfig(
        maxRequests = 5,
        windowMs = 60_000 * 5, // 5 minutes
        refillRate = 1, // Refill 1 token per 5 minutes
        description = "Data export requests"
    )
    
    val IMAGE_UPLOAD = RateLimitConfig(
        maxRequests = 10,
        windowMs = 60_000, // 1 minute
        refillRate = 2, // Refill 2 tokens per minute
        description = "Image uploads"
    )
    
    // Sensitive operations
    val INVITE_CODE_GENERATION = RateLimitConfig(
        maxRequests = 10,
        windowMs = 60_000 * 10, // 10 minutes
        refillRate = 1, // Refill 1 token per 10 minutes
        description = "Invite code generation"
    )
    
    val BULK_OPERATIONS = RateLimitConfig(
        maxRequests = 3,
        windowMs = 60_000 * 30, // 30 minutes
        refillRate = 1, // Refill 1 token per 30 minutes
        description = "Bulk data operations"
    )
}

/**
 * Rate limit configuration
 */
data class RateLimitConfig(
    val maxRequests: Int,
    val windowMs: Long,
    val refillRate: Int,
    val description: String
)

/**
 * Centralized rate limiting manager
 */
class RateLimitManager(scope: CoroutineScope = CoroutineScope(Dispatchers.IO)) {
    
    private val limiters = ConcurrentHashMap<String, RateLimiter>()
    
    init {
        // Initialize all rate limiters directly (no reflection)
        initLimiter(RateLimitConfigs.LOGIN_ATTEMPTS, scope)
        initLimiter(RateLimitConfigs.PASSWORD_RESET, scope)
        initLimiter(RateLimitConfigs.REGISTRATION, scope)
        initLimiter(RateLimitConfigs.API_REQUESTS, scope)
        initLimiter(RateLimitConfigs.DATA_EXPORT, scope)
        initLimiter(RateLimitConfigs.IMAGE_UPLOAD, scope)
        initLimiter(RateLimitConfigs.INVITE_CODE_GENERATION, scope)
        initLimiter(RateLimitConfigs.BULK_OPERATIONS, scope)
    }
    
    private fun initLimiter(config: RateLimitConfig, scope: CoroutineScope) {
        val limiter = RateLimiter(
            maxRequests = config.maxRequests,
            windowMs = config.windowMs,
            refillRate = config.refillRate,
            scope = scope
        )
        limiters[config.description] = limiter
    }
    
    /**
     * Check if request is allowed
     */
    fun isAllowed(limitType: String, userId: String): Boolean {
        val limiter = limiters[limitType] ?: return true
        return limiter.isAllowed(userId)
    }
    
    /**
     * Get remaining tokens
     */
    fun getRemainingTokens(limitType: String, userId: String): Int {
        val limiter = limiters[limitType] ?: return Int.MAX_VALUE
        return limiter.getRemainingTokens(userId)
    }
    
    /**
     * Reset user for specific limit type
     */
    fun resetUser(limitType: String, userId: String) {
        limiters[limitType]?.resetUser(userId)
    }
    
    /**
     * Reset user across all limit types
     */
    fun resetUserAll(userId: String) {
        limiters.values.forEach { it.resetUser(userId) }
    }
    
    /**
     * Get all statistics
     */
    fun getAllStats(): Map<String, RateLimitStats> {
        return limiters.mapValues { (_, limiter) -> limiter.getStats() }
    }
    
    /**
     * Cancel all limiters
     */
    fun cancel() {
        limiters.values.forEach { it.cancel() }
        limiters.clear()
    }
}

/**
 * Rate limit annotation for automatic enforcement
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class RateLimited(
    val limitType: String,
    val userIdParam: String = "userId"
)

/**
 * Rate limit exception
 */
class RateLimitExceededException(
    val limitType: String,
    val userId: String,
    val retryAfterMs: Long,
    message: String = "Rate limit exceeded for $limitType"
) : Exception(message)
