package com.example.skyeos.data.db

import android.database.Cursor

fun Cursor.getStringOrNull(columnIndex: Int): String? {
    return if (isNull(columnIndex)) null else getString(columnIndex)
}

fun Cursor.getLongOrNull(columnIndex: Int): Long? {
    return if (isNull(columnIndex)) null else getLong(columnIndex)
}
