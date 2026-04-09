package com.rescuedroid.rescuedroid.data.local

import androidx.room.*

@Dao
interface PackageDao {
    @Query("SELECT * FROM package_cache WHERE packageName = :pkg")
    suspend fun getPackageInfo(pkg: String): PackageCache?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(pkg: PackageCache)
}
