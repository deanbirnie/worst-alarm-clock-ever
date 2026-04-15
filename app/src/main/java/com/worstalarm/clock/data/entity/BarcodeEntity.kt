package com.worstalarm.clock.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A saved barcode the user has registered. Reusable across locations/alarms.
 *
 * [format] is an ML Kit `Barcode.FORMAT_*` int (e.g. FORMAT_QR_CODE = 256). We match
 * on rawValue AND format so a printed QR and a UPC barcode that happen to have the
 * same string won't be confused.
 */
@Entity(tableName = "barcodes")
data class BarcodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val rawValue: String,
    val format: Int
)
