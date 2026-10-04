package app.car.parking.data.storage

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

object LocationSource {
    const val UNAVAILABLE = "unavailable"
    /** 차량 해제 직후 앱이 표시된 시점의 현재 위치 */
    const val AFTER_DISCONNECT = "after-disconnect"
    /** 사용자가 직접 기록한 시점의 현재 위치 */
    const val MANUAL = "manual"
    /** 홈의 위치 저장 아이콘으로 저장한 현재 위치 */
    const val SAVED = "saved"
}

object DetectionSource {
    const val BLUETOOTH = "bluetooth-disconnect"
    const val MANUAL = "manual"
}

/** 사용자가 저장한 확정 기록. 후보와 분리한다. */
@Entity(tableName = "parking_record")
data class ParkingRecordEntity(
    @PrimaryKey val id: String,
    val vehicleId: String?,
    val detectedAt: Long,
    val confirmedAt: Long,
    val floorLevel: Int?,
    val placeName: String?,
    val zoneMemo: String?,
    val photoPath: String?,
    val latitude: Double?,
    val longitude: Double?,
    val locationAccuracyMeters: Float?,
    val locationCapturedAt: Long?,
    val locationSource: String,
    val detectionSource: String,
    /** 이 기록을 상태바에 표시할지. 패널의 상태바 스위치로 기록할 때 정한다 */
    @ColumnInfo(defaultValue = "0") val statusBarShown: Boolean = false,
)

object CandidateStatus {
    const val CHECKING = "checking"
    const val READY = "ready"
    const val CONFIRMED = "confirmed"
    const val CANCELLED = "cancelled"
}

/** 연결 해제로 생긴 주차 후보. 중복 해제는 시간 창으로 합치며 sessionKey는 `차량@해제시각`이다. */
@Entity(tableName = "parking_candidate", indices = [Index(value = ["sessionKey"], unique = true)])
data class CandidateEntity(
    @PrimaryKey val id: String,
    val vehicleId: String,
    val sessionKey: String,
    val detectedAt: Long,
    val status: String,
    val pressureHpa: Float?,
    val pressureAt: Long?,
    val latitude: Double?,
    val longitude: Double?,
    val locationAccuracyMeters: Float?,
    val locationCapturedAt: Long?,
    val locationSource: String,
)

/** 같은 방문에서 사용자가 확정한 층과 해제 시점 기압. 다음 추천의 상대 기준으로만 사용한다. */
@Entity(tableName = "floor_reference")
data class FloorReferenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val floorLevel: Int,
    val pressureHpa: Float,
    val measuredAt: Long,
    val latitude: Double?,
    val longitude: Double?,
    val floorHeightM: Float,
)

@Dao
interface ParkingDao {
    @Query("SELECT * FROM parking_record ORDER BY confirmedAt DESC LIMIT 1")
    fun observeLatestRecord(): Flow<ParkingRecordEntity?>

    @Query("SELECT * FROM parking_record ORDER BY confirmedAt DESC LIMIT 1")
    suspend fun latestRecord(): ParkingRecordEntity?

    @Upsert
    suspend fun upsertRecord(record: ParkingRecordEntity)

    @Query("SELECT * FROM parking_candidate WHERE vehicleId = :vehicleId AND status IN ('checking', 'ready') ORDER BY detectedAt DESC LIMIT 1")
    suspend fun activeCandidate(vehicleId: String): CandidateEntity?

    @Query("SELECT * FROM parking_candidate WHERE id = :id")
    suspend fun candidate(id: String): CandidateEntity?

    @Query("SELECT * FROM parking_candidate WHERE status = :status ORDER BY detectedAt DESC LIMIT 1")
    fun observeCandidate(status: String): Flow<CandidateEntity?>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCandidate(candidate: CandidateEntity): Long

    @Upsert
    suspend fun upsertCandidate(candidate: CandidateEntity)

    @Query("UPDATE parking_candidate SET status = :to WHERE vehicleId = :vehicleId AND status = :from")
    suspend fun moveCandidates(vehicleId: String, from: String, to: String): Int

    @Query("UPDATE parking_candidate SET status = :status WHERE id = :id")
    suspend fun setCandidateStatus(id: String, status: String)

    @Insert
    suspend fun insertReference(reference: FloorReferenceEntity)

    @Query("SELECT * FROM floor_reference ORDER BY measuredAt DESC LIMIT 1")
    suspend fun latestReference(): FloorReferenceEntity?
}

@Database(
    entities = [ParkingRecordEntity::class, CandidateEntity::class, FloorReferenceEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class ParkingDatabase : RoomDatabase() {
    abstract fun dao(): ParkingDao

    companion object {
        fun create(context: Context): ParkingDatabase =
            Room.databaseBuilder(context, ParkingDatabase::class.java, "parking.db")
                .addMigrations(MIGRATION_1_2)
                .build()

        /** 0.2.0 → 0.3.0: 기록별 상태바 표시 여부 */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE parking_record ADD COLUMN statusBarShown INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
