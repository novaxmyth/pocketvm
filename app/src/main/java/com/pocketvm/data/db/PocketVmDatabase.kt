package com.pocketvm.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.pocketvm.data.model.InstalledApp
import com.pocketvm.data.model.VmInstance

@Database(
    entities = [VmInstance::class, InstalledApp::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class PocketVmDatabase : RoomDatabase() {

    abstract fun vmInstanceDao(): VmInstanceDao
    abstract fun installedAppDao(): InstalledAppDao

    companion object {
        @Volatile
        private var INSTANCE: PocketVmDatabase? = null

        fun getInstance(context: Context): PocketVmDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PocketVmDatabase::class.java,
                    "pocketvm.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
