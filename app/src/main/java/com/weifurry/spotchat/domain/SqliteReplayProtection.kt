package com.weifurry.spotchat.domain

import android.content.ContentValues
import android.content.Context
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class SqliteReplayProtection(
    context: Context,
    private val localFingerprint: String,
    private val maxEntries: Int = ReplayPolicy.DEFAULT_MAX_ENTRIES
) : SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION),
    ReplayProtection {
    private var cachedRowCount: Long? = null
    private var lastMaintenanceAtEpochMillis = 0L

    init {
        require(localFingerprint.isNotBlank()) {
            "Local fingerprint cannot be blank"
        }
        require(maxEntries > 0) {
            "Replay protection capacity must be positive"
        }
    }

    override fun onCreate(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE $TABLE_SEEN_PACKETS (
                $COLUMN_LOCAL_FINGERPRINT TEXT NOT NULL,
                $COLUMN_SENDER_FINGERPRINT TEXT NOT NULL,
                $COLUMN_MESSAGE_ID TEXT NOT NULL,
                $COLUMN_SEEN_AT INTEGER NOT NULL,
                PRIMARY KEY (
                    $COLUMN_LOCAL_FINGERPRINT,
                    $COLUMN_SENDER_FINGERPRINT,
                    $COLUMN_MESSAGE_ID
                )
            ) WITHOUT ROWID
            """.trimIndent()
        )
        createSeenAtIndex(database)
    }

    override fun onUpgrade(
        database: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int
    ) {
        if (oldVersion < 2) {
            createSeenAtIndex(database)
        }
    }

    override fun onOpen(database: SQLiteDatabase) {
        super.onOpen(database)
        database.delete(
            TABLE_SEEN_PACKETS,
            "$COLUMN_LOCAL_FINGERPRINT <> ?",
            arrayOf(localFingerprint)
        )
    }

    @Synchronized
    override fun markIfNew(
        senderFingerprint: String,
        messageId: String
    ): Boolean {
        require(senderFingerprint.isNotBlank()) {
            "Replay sender fingerprint cannot be blank"
        }
        require(messageId.isNotBlank()) {
            "Replay message id cannot be blank"
        }
        val nowEpochMillis = System.currentTimeMillis()
        val database = writableDatabase
        maintain(database, nowEpochMillis)
        val values =
            ContentValues().apply {
                put(COLUMN_LOCAL_FINGERPRINT, localFingerprint)
                put(COLUMN_SENDER_FINGERPRINT, senderFingerprint)
                put(COLUMN_MESSAGE_ID, messageId)
                put(COLUMN_SEEN_AT, nowEpochMillis)
            }
        val inserted = database.insertWithOnConflict(
            TABLE_SEEN_PACKETS,
            null,
            values,
            SQLiteDatabase.CONFLICT_IGNORE
        )
        if (inserted == -1L) {
            return false
        }
        val newCount = (cachedRowCount ?: 0L) + 1L
        if (newCount > maxEntries) {
            database.delete(
                TABLE_SEEN_PACKETS,
                "$COLUMN_LOCAL_FINGERPRINT = ? AND $COLUMN_SENDER_FINGERPRINT = ? AND $COLUMN_MESSAGE_ID = ?",
                arrayOf(localFingerprint, senderFingerprint, messageId)
            )
            throw ReplayProtectionCapacityException()
        }
        cachedRowCount = newCount
        return true
    }

    private fun maintain(
        database: SQLiteDatabase,
        nowEpochMillis: Long
    ) {
        if (
            cachedRowCount != null &&
            nowEpochMillis - lastMaintenanceAtEpochMillis < MAINTENANCE_INTERVAL_MS &&
            (cachedRowCount ?: 0L) < maxEntries
        ) {
            return
        }
        database.delete(
            TABLE_SEEN_PACKETS,
            "$COLUMN_LOCAL_FINGERPRINT = ? AND $COLUMN_SEEN_AT <= ?",
            arrayOf(
                localFingerprint,
                (nowEpochMillis - ReplayPolicy.ENTRY_RETENTION_MS).toString()
            )
        )
        cachedRowCount =
            DatabaseUtils.queryNumEntries(
                database,
                TABLE_SEEN_PACKETS,
                "$COLUMN_LOCAL_FINGERPRINT = ?",
                arrayOf(localFingerprint)
            )
        lastMaintenanceAtEpochMillis = nowEpochMillis
    }

    private fun createSeenAtIndex(database: SQLiteDatabase) {
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS $INDEX_SEEN_AT ON $TABLE_SEEN_PACKETS " +
                "($COLUMN_LOCAL_FINGERPRINT, $COLUMN_SEEN_AT)"
        )
    }

    private companion object {
        const val DATABASE_NAME = "spotchat_replay.db"
        const val DATABASE_VERSION = 2
        const val TABLE_SEEN_PACKETS = "seen_packets"
        const val COLUMN_LOCAL_FINGERPRINT = "local_fingerprint"
        const val COLUMN_SENDER_FINGERPRINT = "sender_fingerprint"
        const val COLUMN_MESSAGE_ID = "message_id"
        const val COLUMN_SEEN_AT = "seen_at"
        const val INDEX_SEEN_AT = "index_seen_packets_local_seen_at"
        const val MAINTENANCE_INTERVAL_MS = 60L * 60L * 1000L
    }
}
