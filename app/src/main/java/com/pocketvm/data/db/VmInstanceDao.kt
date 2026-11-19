package com.pocketvm.data.db

import androidx.room.*
import com.pocketvm.data.model.InstalledApp
import com.pocketvm.data.model.VmInstance
import com.pocketvm.data.model.VmState
import kotlinx.coroutines.flow.Flow

@Dao
interface VmInstanceDao {
    @Query("SELECT * FROM vm_instances ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<VmInstance>>

    @Query("SELECT * FROM vm_instances ORDER BY createdAt DESC")
    suspend fun getAll(): List<VmInstance>

    @Query("SELECT * FROM vm_instances WHERE id = :id")
    suspend fun getById(id: String): VmInstance?

    @Query("SELECT * FROM vm_instances WHERE id = :id")
    fun observeById(id: String): Flow<VmInstance?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(instance: VmInstance)

    @Delete
    suspend fun delete(instance: VmInstance)

    @Query("UPDATE vm_instances SET state = :state WHERE id = :id")
    suspend fun updateState(id: String, state: VmState)

    @Query("UPDATE vm_instances SET lastBootedAt = :timestamp WHERE id = :id")
    suspend fun updateLastBooted(id: String, timestamp: Long)

    @Query("UPDATE vm_instances SET storageUsedBytes = :bytes WHERE id = :id")
    suspend fun updateStorageUsed(id: String, bytes: Long)
}

@Dao
interface InstalledAppDao {
    @Query("SELECT * FROM installed_apps WHERE vmId = :vmId ORDER BY appName ASC")
    fun observeByVm(vmId: String): Flow<List<InstalledApp>>

    @Query("SELECT * FROM installed_apps WHERE vmId = :vmId ORDER BY appName ASC")
    suspend fun getByVm(vmId: String): List<InstalledApp>

    @Query("SELECT * FROM installed_apps WHERE id = :id")
    suspend fun getById(id: String): InstalledApp?

    @Query("SELECT * FROM installed_apps WHERE vmId = :vmId AND packageName = :packageName")
    suspend fun getByPackage(vmId: String, packageName: String): InstalledApp?

    @Query("SELECT * FROM installed_apps WHERE vmId = :vmId AND packageName = :packageName")
    fun getByPackageSync(vmId: String, packageName: String): InstalledApp?

    @Query("SELECT * FROM installed_apps WHERE vmId = :vmId ORDER BY appName ASC")
    fun getByVmSync(vmId: String): List<InstalledApp>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(app: InstalledApp)

    @Delete
    suspend fun delete(app: InstalledApp)

    @Query("DELETE FROM installed_apps WHERE vmId = :vmId")
    suspend fun deleteAllForVm(vmId: String)
}
