package com.fleetcontrol.viewmodel.settings

import androidx.lifecycle.viewModelScope
import com.fleetcontrol.data.managers.DataMigrationManager
import com.fleetcontrol.viewmodel.BaseViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MigrationViewModel(
    private val migrationManager: DataMigrationManager
) : BaseViewModel() {

    val migrationStatus: StateFlow<DataMigrationManager.MigrationStatus> = migrationManager.migrationStatus

    fun startMigration() {
        viewModelScope.launch {
            migrationManager.startMigration()
        }
    }
}
