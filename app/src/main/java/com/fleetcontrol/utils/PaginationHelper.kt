package com.fleetcontrol.utils

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Pagination helper for efficient data loading
 * 
 * Provides:
 * - Paging support for large datasets
 * - Memory-efficient loading
 * - Smooth scrolling experience
 * - Configurable page sizes
 */
data class PaginationConfig(
    val pageSize: Int = 20,
    val prefetchDistance: Int = 3, // Load next page when 3 items away from end
    val enablePlaceholders: Boolean = false,
    val initialLoadSize: Int = 20
)

/**
 * Paginated data wrapper
 */
data class PaginatedData<T>(
    val items: List<T>,
    val hasMore: Boolean,
    val totalCount: Int?,
    val currentPage: Int,
    val pageSize: Int
) {
    val isLastPage: Boolean get() = !hasMore
    val isFirstPage: Boolean get() = currentPage == 0
    val nextPage: Int get() = currentPage + 1
    val previousPage: Int get() = (currentPage - 1).coerceAtLeast(0)
}

/**
 * Pagination state for UI
 */
data class PaginationState(
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val hasError: Boolean = false,
    val canLoadMore: Boolean = true,
    val endReached: Boolean = false
) {
    val isIdle: Boolean get() = !isLoading && !isLoadingMore && !hasError
    val showLoadingMore: Boolean get() = isLoadingMore && !hasError
    val showRetry: Boolean get() = hasError && !isLoading
}

/**
 * Repository interface for paginated data
 */
interface PaginatedRepository<T> {
    suspend fun getPage(page: Int, pageSize: Int): PaginatedData<T>
    fun getTotalCount(): Flow<Int?>
}

/**
 * Pagination manager for handling paginated data loading
 */
class PaginationManager<T>(
    private val repository: PaginatedRepository<T>,
    private val config: PaginationConfig = PaginationConfig()
) {
    private var currentPage = 0
    private var allItems = mutableListOf<T>()
    private var totalCount: Int? = null
    private var hasMore = true
    private var isLoading = false
    private var isLoadingMore = false
    private var error: String? = null
    
    val state: PaginationState
        get() = PaginationState(
            isLoading = isLoading && currentPage == 0,
            isLoadingMore = isLoadingMore,
            error = error,
            hasError = error != null,
            canLoadMore = hasMore && !isLoading && error == null,
            endReached = !hasMore
        )
    
    val currentData: List<T> get() = allItems.toList()
    
    /**
     * Load initial page
     */
    suspend fun loadInitial(): Result<List<T>> {
        return try {
            isLoading = true
            error = null
            currentPage = 0
            allItems.clear()
            
            val result = repository.getPage(0, config.initialLoadSize)
            allItems.addAll(result.items)
            hasMore = result.hasMore
            totalCount = result.totalCount
            currentPage = 0
            
            isLoading = false
            Result.success(allItems.toList())
            
        } catch (e: Exception) {
            isLoading = false
            error = e.message
            Result.failure(e)
        }
    }
    
    /**
     * Load next page
     */
    suspend fun loadNextPage(): Result<List<T>> {
        if (!hasMore || isLoadingMore || isLoading) {
            return Result.success(emptyList())
        }
        
        return try {
            isLoadingMore = true
            error = null
            
            val nextPage = currentPage + 1
            val result = repository.getPage(nextPage, config.pageSize)
            
            allItems.addAll(result.items)
            hasMore = result.hasMore
            totalCount = result.totalCount
            currentPage = nextPage
            
            isLoadingMore = false
            Result.success(result.items)
            
        } catch (e: Exception) {
            isLoadingMore = false
            error = e.message
            Result.failure(e)
        }
    }
    
    /**
     * Refresh data (reload from page 0)
     */
    suspend fun refresh(): Result<List<T>> {
        return loadInitial()
    }
    
    /**
     * Retry loading
     */
    suspend fun retry(): Result<List<T>> {
        error = null
        return if (currentPage == 0) {
            loadInitial()
        } else {
            loadNextPage()
        }
    }
    
    /**
     * Check if should load more based on position
     */
    fun shouldLoadMore(lastVisibleIndex: Int): Boolean {
        return !isLoadingMore && 
               hasMore && 
               error == null &&
               lastVisibleIndex >= allItems.size - config.prefetchDistance
    }
    
    /**
     * Get item count
     */
    fun getItemCount(): Int {
        return totalCount ?: allItems.size + if (hasMore) 1 else 0
    }
    
    /**
     * Get item at position
     */
    fun getItemAt(position: Int): T? {
        return if (position < allItems.size) {
            allItems[position]
        } else {
            null
        }
    }
}

/**
 * Extension function to convert Flow<List<T>> to paginated repository
 */
fun <T> Flow<List<T>>.asPaginatedRepository(
    getTotalCount: (() -> Flow<Int?>)? = null
): PaginatedRepository<T> {
    return object : PaginatedRepository<T> {
        override suspend fun getPage(page: Int, pageSize: Int): PaginatedData<T> {
            val allItems: List<T> = this@asPaginatedRepository.first()
            val startIndex = page * pageSize
            val endIndex = (startIndex + pageSize).coerceAtMost(allItems.size)
            val pageItems = if (startIndex < allItems.size) {
                allItems.subList(startIndex, endIndex)
            } else {
                emptyList()
            }
            
            return PaginatedData(
                items = pageItems,
                hasMore = endIndex < allItems.size,
                totalCount = allItems.size,
                currentPage = page,
                pageSize = pageSize
            )
        }
        
        override fun getTotalCount(): Flow<Int?> {
            return getTotalCount?.invoke() ?: this@asPaginatedRepository.map { it.size }
        }
    }
}

/**
 * Simple pagination for Room DAOs
 */
abstract class RoomPaginatedDao<T> {
    
    /**
     * Get items with pagination
     */
    abstract suspend fun getItemsPaged(
        offset: Int,
        limit: Int
    ): List<T>
    
    /**
     * Get total count
     */
    abstract suspend fun getTotalCount(): Int
    
    /**
     * Create paginated repository
     */
    fun createPaginatedRepository(): PaginatedRepository<T> {
        return object : PaginatedRepository<T> {
            override suspend fun getPage(page: Int, pageSize: Int): PaginatedData<T> {
                val offset = page * pageSize
                val items = getItemsPaged(offset, pageSize)
                val totalCount = getTotalCount()
                val hasMore = offset + items.size < (totalCount as Int)
                
                return PaginatedData(
                    items = items,
                    hasMore = hasMore,
                    totalCount = totalCount as Int?,
                    currentPage = page,
                    pageSize = pageSize
                )
            }
            
            override fun getTotalCount(): Flow<Int?> {
                return kotlinx.coroutines.flow.flow {
                    val totalCount = getTotalCount()
                    emit(totalCount as Int?)
                }
            }
        }
    }
}

/**
 * Pagination utilities
 */
object PaginationUtils {
    
    /**
     * Calculate optimal page size based on item height and screen height
     */
    fun calculateOptimalPageSize(itemHeightPx: Int, screenHeightPx: Int, multiplier: Float = 1.5f): Int {
        val itemsPerScreen = (screenHeightPx / itemHeightPx).toInt()
        return (itemsPerScreen * multiplier).toInt().coerceIn(10, 50)
    }
    
    /**
     * Calculate prefetch distance based on page size
     */
    fun calculatePrefetchDistance(pageSize: Int, multiplier: Float = 0.2f): Int {
        return (pageSize * multiplier).toInt().coerceAtLeast(1)
    }
    
    /**
     * Create pagination config for different use cases
     */
    fun createListConfig(): PaginationConfig {
        return PaginationConfig(
            pageSize = 20,
            prefetchDistance = 5,
            enablePlaceholders = false,
            initialLoadSize = 20
        )
    }
    
    fun createGridConfig(): PaginationConfig {
        return PaginationConfig(
            pageSize = 24, // 6x4 grid
            prefetchDistance = 6,
            enablePlaceholders = false,
            initialLoadSize = 24
        )
    }
    
    fun createCardConfig(): PaginationConfig {
        return PaginationConfig(
            pageSize = 10,
            prefetchDistance = 3,
            enablePlaceholders = false,
            initialLoadSize = 10
        )
    }
}
