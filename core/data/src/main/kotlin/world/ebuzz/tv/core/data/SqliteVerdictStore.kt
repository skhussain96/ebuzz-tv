package world.ebuzz.tv.core.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import world.ebuzz.filter.VerdictStore
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

// The table is tiny (a key and a bit per title), so it is read into memory once and answers without touching the disk;
// writes go to SQLite on a background thread.
class SqliteVerdictStore(context: Context) : VerdictStore {
    private val helper = object : SQLiteOpenHelper(context.applicationContext, "verdicts.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) = db.execSQL("CREATE TABLE verdict (k TEXT PRIMARY KEY, allowed INTEGER NOT NULL, at INTEGER NOT NULL)")
        override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) { db.execSQL("DROP TABLE IF EXISTS verdict"); onCreate(db) }
    }
    private val io = Executors.newSingleThreadExecutor()
    private val cache by lazy {
        ConcurrentHashMap<String, Boolean>().also { map ->
            runCatching { helper.readableDatabase.rawQuery("SELECT k, allowed FROM verdict", null).use { c -> while (c.moveToNext()) map[c.getString(0)] = c.getInt(1) == 1 } }
        }
    }

    override fun get(key: String): Boolean? = cache[key]

    override fun put(key: String, allowed: Boolean) {
        if (cache.put(key, allowed) == allowed) return
        io.execute {
            runCatching {
                helper.writableDatabase.insertWithOnConflict("verdict", null,
                    ContentValues().apply { put("k", key); put("allowed", if (allowed) 1 else 0); put("at", System.currentTimeMillis()) }, SQLiteDatabase.CONFLICT_REPLACE)
            }
        }
    }
}
