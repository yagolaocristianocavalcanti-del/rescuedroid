package com.rescuedroid.rescuedroid.di

import android.content.Context
import androidx.room.Room
import com.rescuedroid.rescuedroid.data.local.AppDatabase
import com.rescuedroid.rescuedroid.data.local.ChatDao
import com.rescuedroid.rescuedroid.data.local.DeviceDao
import com.rescuedroid.rescuedroid.data.local.PackageDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "rescuedroid_db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideChatDao(database: AppDatabase): ChatDao {
        return database.chatDao()
    }

    @Provides
    fun provideDeviceDao(database: AppDatabase): DeviceDao {
        return database.deviceDao()
    }

    @Provides
    fun providePackageDao(database: AppDatabase): PackageDao {
        return database.packageDao()
    }
}
