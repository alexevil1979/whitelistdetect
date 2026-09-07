package ru.whitelist.pulse.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "custom_sites")
data class CustomSiteEntity(
    @PrimaryKey val id: String,
    val host: String,
    val url: String,
    val tag: String,
    val enabled: Boolean,
    val createdAt: Long,
)

@Entity(tableName = "check_history")
data class CheckHistoryEntity(
    @PrimaryKey val id: String,
    val timestamp: Long,
    val verdict: String,
    val confidence: Float,
    val networkType: String,
    val vpnActive: Boolean,
    val groupARate: Float,
    val groupBRate: Float,
    val groupCRate: Float,
    val publicIp: String?,
    val country: String?,
)

@Entity(tableName = "bundled_override")
data class BundledOverrideEntity(
    @PrimaryKey val id: Int = 1,
    val json: String,
    val updatedAt: Long,
)
