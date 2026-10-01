package com.bblvn.composebaseproject.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.bblvn.composebaseproject.data.database.entity.CreationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CreationDao {

    @Query("SELECT * FROM creations ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<CreationEntity>>

    @Query("SELECT * FROM creations WHERE type = :type ORDER BY createdAt DESC")
    fun observeByType(type: String): Flow<List<CreationEntity>>

    @Query("SELECT * FROM creations WHERE id = :id")
    suspend fun getById(id: String): CreationEntity?

    @Query("SELECT * FROM creations WHERE status = 'PROCESSING' ORDER BY createdAt DESC")
    suspend fun getProcessing(): List<CreationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CreationEntity)

    @Update
    suspend fun update(entity: CreationEntity)

    @Query("DELETE FROM creations WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM creations")
    suspend fun deleteAll()
}
