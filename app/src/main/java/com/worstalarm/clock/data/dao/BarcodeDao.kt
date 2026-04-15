package com.worstalarm.clock.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.worstalarm.clock.data.entity.BarcodeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BarcodeDao {
    @Query("SELECT * FROM barcodes ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<BarcodeEntity>>

    @Query("SELECT * FROM barcodes WHERE id = :id")
    suspend fun getById(id: Long): BarcodeEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(barcode: BarcodeEntity): Long

    @Update
    suspend fun update(barcode: BarcodeEntity)

    @Delete
    suspend fun delete(barcode: BarcodeEntity)

    @Query("SELECT COUNT(*) FROM routine_steps WHERE barcodeId = :id")
    suspend fun usageCount(id: Long): Int
}
