package ru.whitelist.pulse.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import ru.whitelist.pulse.data.local.entity.BundledOverrideEntity
import ru.whitelist.pulse.data.local.entity.CheckHistoryEntity
import ru.whitelist.pulse.data.local.entity.CustomSiteEntity

@Dao
interface CustomSiteDao {
    @Query("SELECT * FROM custom_sites ORDER BY host ASC")
    fun observe(): Flow<List<CustomSiteEntity>>

    @Query("SELECT * FROM custom_sites ORDER BY host ASC")
    suspend fun all(): List<CustomSiteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CustomSiteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<CustomSiteEntity>)

    @Update
    suspend fun update(entity: CustomSiteEntity)

    @Query("DELETE FROM custom_sites WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM check_history ORDER BY timestamp DESC LIMIT :limit")
    fun observe(limit: Int): Flow<List<CheckHistoryEntity>>

    @Query("SELECT * FROM check_history ORDER BY timestamp DESC LIMIT :limit")
    suspend fun latest(limit: Int): List<CheckHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CheckHistoryEntity)

    @Query("DELETE FROM check_history WHERE id NOT IN (SELECT id FROM check_history ORDER BY timestamp DESC LIMIT 20)")
    suspend fun trim()

    @Query("DELETE FROM check_history")
    suspend fun clear()
}

@Dao
interface BundledOverrideDao {
    @Query("SELECT * FROM bundled_override WHERE id = 1")
    suspend fun get(): BundledOverrideEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BundledOverrideEntity)
}
