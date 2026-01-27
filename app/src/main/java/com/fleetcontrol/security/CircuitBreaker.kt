package com.fleetcontrol.security

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.system.measureTimeMillis

/**
 * Circuit Breaker pattern for handling failures and preventing cascading failures
 * 
 * States:
 * - CLOSED: Normal operation, counting failures
 * - OPEN: Circuit is open, blocking calls
 * - HALF_OPEN: Testing if service has recovered
 */
class CircuitBreaker(
    private val name: String,
    private val failureThreshold: Int = 5,
    private val timeoutMs: Long = 60_000, // 1 minute
    private val successThreshold: Int = 3, // Successes needed to close circuit
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    
    enum class State {
        CLOSED, OPEN, HALF_OPEN
    }
    
    private val _state = MutableStateFlow(State.CLOSED)
    val state = _state.asStateFlow()
    
    private val failureCount = AtomicInteger(0)
    private val successCount = AtomicInteger(0)
    private val lastFailureTime = AtomicLong(0)
    private val lastStateChange = AtomicLong(System.currentTimeMillis())
    
    private val metrics = CircuitBreakerMetrics()
    
    /**
     * Execute operation with circuit breaker protection
     */
    suspend fun <T> execute(operation: suspend () -> T): Result<T> {
        val currentState = _state.value
        
        return when (currentState) {
            State.OPEN -> {
                if (shouldAttemptReset()) {
                    transitionToHalfOpen()
                    executeWithHalfOpen(operation)
                } else {
                    metrics.recordBlockedCall()
                    Result.failure(CircuitBreakerOpenException(name, "Circuit breaker is OPEN"))
                }
            }
            State.HALF_OPEN -> {
                executeWithHalfOpen(operation)
            }
            State.CLOSED -> {
                executeWithClosed(operation)
            }
        }
    }
    
    private suspend fun <T> executeWithClosed(operation: suspend () -> T): Result<T> {
        return try {
            val result = operation()
            onSuccess()
            Result.success(result)
        } catch (e: Exception) {
            onFailure()
            Result.failure(e)
        }
    }
    
    private suspend fun <T> executeWithHalfOpen(operation: suspend () -> T): Result<T> {
        return try {
            val result = operation()
            onHalfOpenSuccess()
            Result.success(result)
        } catch (e: Exception) {
            onHalfOpenFailure()
            Result.failure(e)
        }
    }
    
    private fun onSuccess() {
        failureCount.set(0)
        metrics.recordSuccess()
    }
    
    private fun onFailure() {
        val failures = failureCount.incrementAndGet()
        lastFailureTime.set(System.currentTimeMillis())
        metrics.recordFailure()
        
        if (failures >= failureThreshold) {
            transitionToOpen()
        }
    }
    
    private fun onHalfOpenSuccess() {
        val successes = successCount.incrementAndGet()
        metrics.recordSuccess()
        
        if (successes >= successThreshold) {
            transitionToClosed()
        }
    }
    
    private fun onHalfOpenFailure() {
        successCount.set(0)
        transitionToOpen()
    }
    
    private fun transitionToOpen() {
        _state.value = State.OPEN
        lastStateChange.set(System.currentTimeMillis())
        metrics.recordStateChange(State.OPEN)
    }
    
    private fun transitionToHalfOpen() {
        _state.value = State.HALF_OPEN
        successCount.set(0)
        lastStateChange.set(System.currentTimeMillis())
        metrics.recordStateChange(State.HALF_OPEN)
    }
    
    private fun transitionToClosed() {
        _state.value = State.CLOSED
        failureCount.set(0)
        successCount.set(0)
        lastStateChange.set(System.currentTimeMillis())
        metrics.recordStateChange(State.CLOSED)
    }
    
    private fun shouldAttemptReset(): Boolean {
        return System.currentTimeMillis() - lastStateChange.get() >= timeoutMs
    }
    
    /**
     * Force circuit to open state
     */
    fun forceOpen() {
        transitionToOpen()
    }
    
    /**
     * Force circuit to closed state
     */
    fun forceClose() {
        transitionToClosed()
    }
    
    /**
     * Get current metrics
     */
    fun getMetrics(): CircuitBreakerMetrics {
        return metrics.copy().apply {
            currentState = _state.value
            failureCount = this@CircuitBreaker.failureCount.get()
            successCount = this@CircuitBreaker.successCount.get()
            lastFailureTime = this@CircuitBreaker.lastFailureTime.get()
            lastStateChange = this@CircuitBreaker.lastStateChange.get()
        }
    }
    
    /**
     * Reset circuit breaker
     */
    fun reset() {
        forceClose()
        metrics.reset()
    }
}

/**
 * Circuit breaker metrics
 */
data class CircuitBreakerMetrics(
    var totalCalls: Long = 0,
    var successfulCalls: Long = 0,
    var failedCalls: Long = 0,
    var blockedCalls: Long = 0,
    var stateChanges: Map<CircuitBreaker.State, Long> = emptyMap(),
    var currentState: CircuitBreaker.State = CircuitBreaker.State.CLOSED,
    var failureCount: Int = 0,
    var successCount: Int = 0,
    var lastFailureTime: Long = 0,
    var lastStateChange: Long = 0
) {
    
    fun recordSuccess() {
        totalCalls++
        successfulCalls++
    }
    
    fun recordFailure() {
        totalCalls++
        failedCalls++
    }
    
    fun recordBlockedCall() {
        totalCalls++
        blockedCalls++
    }
    
    fun recordStateChange(state: CircuitBreaker.State) {
        stateChanges = stateChanges.toMutableMap().apply {
            this[state] = (this[state] ?: 0) + 1
        }
    }
    
    fun reset() {
        totalCalls = 0
        successfulCalls = 0
        failedCalls = 0
        blockedCalls = 0
        stateChanges = emptyMap()
    }
    
    val successRate: Double get() = if (totalCalls > 0) successfulCalls.toDouble() / totalCalls else 0.0
    val failureRate: Double get() = if (totalCalls > 0) failedCalls.toDouble() / totalCalls else 0.0
}

/**
 * Circuit breaker manager for multiple services
 */
class CircuitBreakerManager {
    private val circuitBreakers = mutableMapOf<String, CircuitBreaker>()
    
    /**
     * Get or create circuit breaker for service
     */
    fun getCircuitBreaker(
        name: String,
        failureThreshold: Int = 5,
        timeoutMs: Long = 60_000,
        successThreshold: Int = 3
    ): CircuitBreaker {
        return circuitBreakers.getOrPut(name) {
            CircuitBreaker(name, failureThreshold, timeoutMs, successThreshold)
        }
    }
    
    /**
     * Execute operation with circuit breaker
     */
    suspend fun <T> execute(
        serviceName: String,
        failureThreshold: Int = 5,
        timeoutMs: Long = 60_000,
        successThreshold: Int = 3,
        operation: suspend () -> T
    ): Result<T> {
        val circuitBreaker = getCircuitBreaker(serviceName, failureThreshold, timeoutMs, successThreshold)
        return circuitBreaker.execute(operation)
    }
    
    /**
     * Get all circuit breaker metrics
     */
    fun getAllMetrics(): Map<String, CircuitBreakerMetrics> {
        return circuitBreakers.mapValues { (_, breaker) -> breaker.getMetrics() }
    }
    
    /**
     * Force open specific circuit breaker
     */
    fun forceOpen(serviceName: String) {
        circuitBreakers[serviceName]?.forceOpen()
    }
    
    /**
     * Force close specific circuit breaker
     */
    fun forceClose(serviceName: String) {
        circuitBreakers[serviceName]?.forceClose()
    }
    
    /**
     * Reset all circuit breakers
     */
    fun resetAll() {
        circuitBreakers.values.forEach { it.reset() }
    }
    
    /**
     * Get circuit breaker state
     */
    fun getState(serviceName: String): CircuitBreaker.State? {
        return circuitBreakers[serviceName]?.state?.value
    }
}

/**
 * Circuit breaker exceptions
 */
class CircuitBreakerOpenException(
    val serviceName: String,
    message: String
) : Exception(message)

class CircuitBreakerTimeoutException(
    val serviceName: String,
    message: String
) : Exception(message)

/**
 * Circuit breaker configurations
 */
object CircuitBreakerConfigs {
    
    // Network services
    val FIRESTORE = CircuitBreakerConfig(
        failureThreshold = 3,
        timeoutMs = 30_000, // 30 seconds
        successThreshold = 2,
        description = "Firestore operations"
    )
    
    val AUTH_SERVICE = CircuitBreakerConfig(
        failureThreshold = 5,
        timeoutMs = 60_000, // 1 minute
        successThreshold = 3,
        description = "Authentication service"
    )
    
    val IMAGE_PROCESSING = CircuitBreakerConfig(
        failureThreshold = 3,
        timeoutMs = 45_000, // 45 seconds
        successThreshold = 2,
        description = "Image processing"
    )
    
    val EXPORT_SERVICE = CircuitBreakerConfig(
        failureThreshold = 2,
        timeoutMs = 120_000, // 2 minutes
        successThreshold = 2,
        description = "Data export"
    )
    
    val BACKUP_SERVICE = CircuitBreakerConfig(
        failureThreshold = 2,
        timeoutMs = 300_000, // 5 minutes
        successThreshold = 1,
        description = "Backup operations"
    )
}

/**
 * Circuit breaker configuration
 */
data class CircuitBreakerConfig(
    val failureThreshold: Int,
    val timeoutMs: Long,
    val successThreshold: Int,
    val description: String
)

/**
 * Circuit breaker annotation for automatic protection
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class CircuitBreakerProtected(
    val serviceName: String,
    val failureThreshold: Int = 5,
    val timeoutMs: Long = 60_000,
    val successThreshold: Int = 3
)
