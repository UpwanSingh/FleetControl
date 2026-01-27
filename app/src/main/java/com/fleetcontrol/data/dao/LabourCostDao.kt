package com.fleetcontrol.data.dao

import androidx.room.*
import com.fleetcontrol.data.entities.LabourCostRuleEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for Labour Cost Rule operations
 */
@Dao
interface LabourCostDao {
    @Query("SELECT * FROM labour_cost_rules WHERE isActive = 1 ORDER BY name ASC")
    fun getAllActiveRules(): Flow<List<LabourCostRuleEntity>>

    @Query("SELECT * FROM labour_cost_rules WHERE isDefault = 1 AND isActive = 1 LIMIT 1")
    suspend fun getDefaultRule(): LabourCostRuleEntity?

    @Query("SELECT * FROM labour_cost_rules WHERE id = :id")
    suspend fun getRuleById(id: Long): LabourCostRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: LabourCostRuleEntity): Long

    @Update
    suspend fun update(rule: LabourCostRuleEntity)

    @Delete
    suspend fun delete(rule: LabourCostRuleEntity)
    
    /**
     * Get all rules once (for export)
     */
    @Query("SELECT * FROM labour_cost_rules WHERE isActive = 1")
    suspend fun getAllRulesOnce(): List<LabourCostRuleEntity>
}
