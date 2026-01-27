package com.fleetcontrol.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import com.fleetcontrol.core.AppConfig
import com.fleetcontrol.data.entities.*
import com.fleetcontrol.data.dao.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Main Room database for FleetControl
 * 
 * Per Section 13 of BUSINESS_LOGIC_SPEC.md:
 * - All writes are ACID-compliant
 * - No partial saves
 * - No silent failures
 * 
 * Transaction Safety Features:
 * - Use withTransaction {} for complex multi-DAO operations
 * - All DAOs have @Transaction annotated methods for related operations
 * - Database version tracking for migrations
 */
@Database(
    entities = [
        UserEntity::class,
        OwnerEntity::class,
        CompanyEntity::class,
        ClientEntity::class,
        DriverEntity::class,
        PickupLocationEntity::class,
        PickupClientDistanceEntity::class,
        TripEntity::class,
        TripAttachmentEntity::class,
        FuelEntryEntity::class,
        DriverRateSlabEntity::class,
        LabourCostRuleEntity::class,
        AdvanceEntity::class,
        AuditLogEntity::class,
        SubscriptionEntity::class
    ],
    version = 9,
    autoMigrations = [],
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    
    // DAOs
    abstract fun userDao(): UserDao
    abstract fun companyDao(): CompanyDao
    abstract fun clientDao(): ClientDao
    abstract fun driverDao(): DriverDao
    abstract fun pickupLocationDao(): PickupLocationDao
    abstract fun pickupClientDistanceDao(): PickupClientDistanceDao
    abstract fun tripDao(): TripDao
    abstract fun tripAttachmentDao(): TripAttachmentDao
    abstract fun fuelDao(): FuelDao
    abstract fun rateSlabDao(): RateSlabDao
    abstract fun labourCostDao(): LabourCostDao
    abstract fun advanceDao(): AdvanceDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun ownerDao(): OwnerDao
    
    /**
     * Execute a block within a database transaction.
     * All operations inside the block will be atomic - either all succeed or all fail.
     * 
     * Usage:
     * ```kotlin
     * database.runInTransaction {
     *     tripDao().insert(trip)
     *     auditLogDao().insert(auditLog)
     * }
     * ```
     */
    suspend fun <R> runInTransaction(block: suspend () -> R): R {
        return withTransaction(block)
    }

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    AppConfig.DATABASE_NAME
                )
                .addMigrations(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                .fallbackToDestructiveMigration() // Handles schema mismatches by rebuilding DB
                .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * Migration from Version 5 to 6
         * Adds 'isSynced' column to 'trips' table
         */
        private val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE trips ADD COLUMN isSynced INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE drivers ADD COLUMN firestoreId TEXT")
                database.execSQL("ALTER TABLE companies ADD COLUMN firestoreId TEXT")
                database.execSQL("ALTER TABLE clients ADD COLUMN firestoreId TEXT")
                database.execSQL("ALTER TABLE pickup_locations ADD COLUMN firestoreId TEXT")
                database.execSQL("ALTER TABLE driver_rate_slabs ADD COLUMN firestoreId TEXT")
                database.execSQL("ALTER TABLE pickup_client_distances ADD COLUMN firestoreId TEXT")
                database.execSQL("ALTER TABLE fuel_entries ADD COLUMN firestoreId TEXT")
                database.execSQL("ALTER TABLE advances ADD COLUMN firestoreId TEXT")
            }
        }

        /**
         * Migration from Version 7 to 8
         * Multi-Tenancy: Adds 'ownerId' column to all entities and creates 'owners' table
         */
        private val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create owners table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `owners` (
                        `firebaseUid` TEXT NOT NULL,
                        `email` TEXT NOT NULL,
                        `businessName` TEXT NOT NULL,
                        `phone` TEXT NOT NULL,
                        `isActive` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`firebaseUid`)
                    )
                """.trimIndent())
                
                // Add ownerId column to all relevant tables
                database.execSQL("ALTER TABLE drivers ADD COLUMN ownerId TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE companies ADD COLUMN ownerId TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE clients ADD COLUMN ownerId TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE pickup_locations ADD COLUMN ownerId TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE driver_rate_slabs ADD COLUMN ownerId TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE pickup_client_distances ADD COLUMN ownerId TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE trips ADD COLUMN ownerId TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE trips ADD COLUMN firestoreId TEXT")
                database.execSQL("ALTER TABLE fuel_entries ADD COLUMN ownerId TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE advances ADD COLUMN ownerId TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * Close and clear the database instance.
         * Crucial for Restore operations to release file locks.
         */
        fun closeDatabase() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }
    }


}
