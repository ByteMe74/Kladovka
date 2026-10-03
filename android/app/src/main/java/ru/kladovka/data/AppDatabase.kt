package ru.kladovka.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Place::class, Shelf::class, Polka::class, Container::class, Item::class],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun placeDao(): PlaceDao
    abstract fun shelfDao(): ShelfDao
    abstract fun polkaDao(): PolkaDao
    abstract fun containerDao(): ContainerDao
    abstract fun itemDao(): ItemDao

    companion object {

        /** v1 → v2: добавляем поле location в стеллажи и контейнеры. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE shelves ADD COLUMN location TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE containers ADD COLUMN location TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v2 → v3: таблица мест, привязки placeId; старые текстовые location переносим в места. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS places (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "name TEXT NOT NULL, " +
                        "latitude REAL, " +
                        "longitude REAL, " +
                        "notes TEXT NOT NULL DEFAULT '')"
                )
                db.execSQL("ALTER TABLE shelves ADD COLUMN placeId INTEGER")
                db.execSQL("ALTER TABLE containers ADD COLUMN placeId INTEGER")
                db.execSQL("ALTER TABLE items ADD COLUMN placeId INTEGER")
                // Переносим старые текстовые «места» из стеллажей и контейнеров в таблицу мест
                db.execSQL(
                    "INSERT INTO places (name) SELECT DISTINCT location FROM shelves WHERE location <> ''"
                )
                db.execSQL(
                    "INSERT INTO places (name) SELECT DISTINCT location FROM containers " +
                        "WHERE location <> '' AND location NOT IN (SELECT name FROM places)"
                )
                db.execSQL(
                    "UPDATE shelves SET placeId = (SELECT id FROM places WHERE name = shelves.location) " +
                        "WHERE location <> ''"
                )
                db.execSQL(
                    "UPDATE containers SET placeId = (SELECT id FROM places WHERE name = containers.location) " +
                        "WHERE location <> ''"
                )
            }
        }

        /** v3 → v4: таблица полок (polki), опционально привязанных к стеллажу и месту. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS polki (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "name TEXT NOT NULL, " +
                        "notes TEXT NOT NULL DEFAULT '', " +
                        "shelfId INTEGER, " +
                        "placeId INTEGER)"
                )
            }
        }

        /** v4 → v5: закреплённые вещи (⭐ pinned) — поле в items. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE items ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "kladovka.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                    .also { instance = it }
            }
    }
}