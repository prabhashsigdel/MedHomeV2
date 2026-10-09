package com.medhome.nepal.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/**
 * A medicine. Times are minutes after midnight, comma separated ("480,1200"); [weekdays] is a
 * bit per [com.medhome.nepal.domain.Weekday] ordinal (Sunday = bit 0), used when not [everyDay].
 * [alarmDoseAtMillis] is the dose its alarm is set for (null: none), cleared when that alarm
 * fires; rescheduling uses it to re-set an alarm that hasn't fired yet rather than skip its dose.
 * Read through [ReminderMapper], which drops rows it can't trust.
 */
@Entity(tableName = "medicines")
data class MedicineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val name: String,
    val dose: String,
    val times: String,
    val everyDay: Boolean,
    val weekdays: Int,
    val startEpochDay: Long,
    val endEpochDay: Long?,
    val alarmDoseAtMillis: Long? = null,
)

/** What happened to one dose: taken, or snoozed. Deleted with its medicine. */
@Entity(
    tableName = "dose_records",
    primaryKeys = ["medicineId", "epochDay", "minuteOfDay"],
    foreignKeys = [
        ForeignKey(
            entity = MedicineEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicineId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("epochDay")],
)
data class DoseRecordEntity(
    val medicineId: Long,
    val epochDay: Long,
    val minuteOfDay: Int,
    val takenAtMillis: Long?,
    val snoozedUntilMillis: Long?,
)

/** An upcoming booking with reminders scheduled (see AppointmentReminder). */
@Entity(tableName = "appointment_reminders")
data class AppointmentReminderEntity(
    @PrimaryKey val bookingId: String,
    val startAtMillis: Long,
    val doctorName: String,
)

@Dao
interface ReminderDao {
    @Query("SELECT * FROM medicines ORDER BY name COLLATE NOCASE, id")
    fun observeMedicines(): Flow<List<MedicineEntity>>

    @Query("SELECT * FROM medicines")
    suspend fun medicines(): List<MedicineEntity>

    @Query("SELECT * FROM medicines WHERE id = :id")
    suspend fun medicine(id: Long): MedicineEntity?

    @Insert
    suspend fun insertMedicine(medicine: MedicineEntity): Long

    @Update
    suspend fun updateMedicine(medicine: MedicineEntity): Int

    @Query("UPDATE medicines SET alarmDoseAtMillis = :atMillis WHERE id = :id")
    suspend fun setAlarmDose(id: Long, atMillis: Long?)

    @Query("DELETE FROM medicines WHERE id = :id")
    suspend fun deleteMedicine(id: Long)

    @Query("SELECT * FROM dose_records WHERE epochDay = :epochDay")
    fun observeRecordsOn(epochDay: Long): Flow<List<DoseRecordEntity>>

    @Query("SELECT * FROM dose_records WHERE medicineId = :medicineId AND epochDay = :epochDay AND minuteOfDay = :minuteOfDay")
    suspend fun record(medicineId: Long, epochDay: Long, minuteOfDay: Int): DoseRecordEntity?

    @Query("SELECT * FROM dose_records WHERE snoozedUntilMillis IS NOT NULL")
    suspend fun snoozedRecords(): List<DoseRecordEntity>

    @Upsert
    suspend fun upsertRecord(record: DoseRecordEntity)

    @Query("DELETE FROM dose_records WHERE epochDay < :epochDay")
    suspend fun deleteRecordsBefore(epochDay: Long)

    @Query("SELECT * FROM appointment_reminders ORDER BY startAtMillis")
    fun observeAppointments(): Flow<List<AppointmentReminderEntity>>

    @Query("SELECT * FROM appointment_reminders")
    suspend fun appointments(): List<AppointmentReminderEntity>

    @Query("SELECT * FROM appointment_reminders WHERE bookingId = :bookingId")
    suspend fun appointment(bookingId: String): AppointmentReminderEntity?

    @Upsert
    suspend fun upsertAppointment(appointment: AppointmentReminderEntity)

    @Query("DELETE FROM appointment_reminders WHERE bookingId = :bookingId")
    suspend fun deleteAppointment(bookingId: String)

    @Query("DELETE FROM medicines")
    suspend fun deleteAllMedicines()

    @Query("DELETE FROM dose_records")
    suspend fun deleteAllRecords()

    @Query("DELETE FROM appointment_reminders")
    suspend fun deleteAllAppointments()

    /** Everything, on sign-out and account deletion. */
    @Transaction
    suspend fun deleteAll() {
        deleteAllRecords()
        deleteAllMedicines()
        deleteAllAppointments()
    }
}

/**
 * Reminders live on this phone only (never Firestore) and are wiped on sign-out. Excluded from
 * backup and device transfer by data_extraction_rules. Version 1; any schema change needs a
 * real migration (the schema JSON for each version is committed under app/schemas), never a
 * destructive fallback.
 */
@Database(
    entities = [MedicineEntity::class, DoseRecordEntity::class, AppointmentReminderEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class ReminderDatabase : RoomDatabase() {
    abstract fun reminderDao(): ReminderDao

    companion object {
        private const val NAME = "reminders.db"

        fun create(context: Context): ReminderDatabase =
            Room.databaseBuilder(context.applicationContext, ReminderDatabase::class.java, NAME)
                .addCallback(SecureDelete)
                .build()

        /** Deleted rows (medicine names) are overwritten on disk, not just unlinked. */
        private object SecureDelete : Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                db.query("PRAGMA secure_delete = ON").close()
            }
        }
    }
}
