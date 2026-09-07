package ru.whitelist.pulse.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import ru.whitelist.pulse.data.local.dao.BundledOverrideDao
import ru.whitelist.pulse.data.local.dao.CustomSiteDao
import ru.whitelist.pulse.data.local.dao.HistoryDao
import ru.whitelist.pulse.data.local.entity.BundledOverrideEntity
import ru.whitelist.pulse.data.local.entity.CheckHistoryEntity
import ru.whitelist.pulse.data.local.entity.CustomSiteEntity

@Database(
    entities = [
        CustomSiteEntity::class,
        CheckHistoryEntity::class,
        BundledOverrideEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun customSites(): CustomSiteDao
    abstract fun history(): HistoryDao
    abstract fun bundled(): BundledOverrideDao
}
