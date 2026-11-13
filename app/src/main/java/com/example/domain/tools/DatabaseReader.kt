package com.example.domain.tools

import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class DbTableSummary(
    val name: String,
    val rowCount: Long,
    val createSql: String
)

data class DbTablePage(
    val columns: List<String>,
    val rows: List<List<String>>
)

object DatabaseReader {

    suspend fun inspectTables(dbFile: File): Result<List<DbTableSummary>> = withContext(Dispatchers.IO) {
        var db: SQLiteDatabase? = null
        try {
            db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            val tables = mutableListOf<DbTableSummary>()
            db.rawQuery(
                "SELECT name, sql FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name != 'android_metadata' ORDER BY name",
                null
            ).use { c ->
                while (c.moveToNext()) {
                    val name = c.getString(0) ?: continue
                    val sql = c.getString(1) ?: ""
                    val count = try {
                        db.rawQuery("SELECT COUNT(*) FROM \"${name.replace("\"", "\"\"")}\"", null).use { cc ->
                            if (cc.moveToFirst()) cc.getLong(0) else 0L
                        }
                    } catch (_: Exception) {
                        0L
                    }
                    tables.add(DbTableSummary(name, count, sql))
                }
            }
            Result.success(tables)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try { db?.close() } catch (_: Exception) {}
        }
    }

    suspend fun queryTablePage(
        dbFile: File,
        tableName: String,
        limit: Int = 50,
        offset: Int = 0
    ): Result<DbTablePage> = withContext(Dispatchers.IO) {
        var db: SQLiteDatabase? = null
        try {
            db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            val safeTable = tableName.replace("\"", "\"\"")
            val rows = mutableListOf<List<String>>()
            var cols: List<String> = emptyList()
            db.rawQuery("SELECT * FROM \"$safeTable\" LIMIT $limit OFFSET $offset", null).use { c ->
                cols = c.columnNames.toList()
                while (c.moveToNext()) {
                    val row = List(cols.size) { idx ->
                        when (c.getType(idx)) {
                            android.database.Cursor.FIELD_TYPE_NULL -> "NULL"
                            android.database.Cursor.FIELD_TYPE_BLOB -> "<BLOB ${c.getBlob(idx)?.size ?: 0}B>"
                            else -> c.getString(idx) ?: ""
                        }
                    }
                    rows.add(row)
                }
            }
            Result.success(DbTablePage(cols, rows))
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try { db?.close() } catch (_: Exception) {}
        }
    }
}
