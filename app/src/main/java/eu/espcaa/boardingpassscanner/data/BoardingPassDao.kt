package eu.espcaa.boardingpassscanner.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface BoardingPassDao {

    @Insert
    suspend fun insertBoardingPass(boardingPass: BoardingPassEntity): Long

    @Insert
    suspend fun insertLegs(legs: List<LegEntity>)

    @Transaction
    suspend fun insertBoardingPassWithLegs(boardingPass: BoardingPassEntity, legs: List<LegEntity>) {
        val passId = insertBoardingPass(boardingPass)
        insertLegs(legs.map { it.copy(boardingPassId = passId) })
    }

    @Transaction
    @Query("SELECT * FROM boarding_passes WHERE archived = :archived ORDER BY scannedAt DESC")
    fun getBoardingPasses(archived: Boolean = false): Flow<List<BoardingPassWithLegs>>

    @Transaction
    @Query("SELECT * FROM boarding_passes WHERE id = :id")
    suspend fun getBoardingPassById(id: Long): BoardingPassWithLegs?

    @Transaction
    @Query("SELECT * FROM boarding_passes WHERE id IN (:ids) ORDER BY scannedAt DESC")
    suspend fun getBoardingPassesByIds(ids: List<Long>): List<BoardingPassWithLegs>

    @Transaction
    @Query("SELECT * FROM boarding_passes WHERE rawBarcode = :rawBarcode LIMIT 1")
    suspend fun getBoardingPassByRawBarcode(rawBarcode: String): BoardingPassWithLegs?

    @Query("DELETE FROM boarding_passes WHERE id = :id")
    suspend fun deleteBoardingPass(id: Long)

    @Query("UPDATE boarding_passes SET archived = :archived WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean)

    @Query("SELECT EXISTS(SELECT 1 FROM boarding_passes WHERE rawBarcode = :rawBarcode LIMIT 1)")
    suspend fun existsByRawBarcode(rawBarcode: String): Boolean

    @Query("DELETE FROM boarding_passes WHERE rawBarcode = :rawBarcode")
    suspend fun deleteByRawBarcode(rawBarcode: String)
}
