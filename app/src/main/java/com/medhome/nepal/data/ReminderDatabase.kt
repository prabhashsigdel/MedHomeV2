package com.medhome.nepal.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import androidx.room.migration.Migration
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

/**
 * One scheduled dose as it was on its day: the medicine's [name] and [dose] then, and what
 * happened to it (taken, or snoozed; not taken an hour after its time is missed). A day's doses
 * are written when that day is first worked out (see [DoseLogDayEntity]) and are history from
 * then on: editing or deleting the medicine never changes them, only today's doses still to
 * come. No foreign key, so a deleted medicine's doses stay; medicine IDs are never reused
 * (AUTOINCREMENT). Pruned after ReminderEngine.KEEP_RECORD_DAYS.
 */
@Entity(
    tableName = "dose_log",
    primaryKeys = ["medicineId", "epochDay", "minuteOfDay"],
    indices = [Index("epochDay")],
)
data class DoseLogEntity(
    val medicineId: Long,
    val epochDay: Long,
    val minuteOfDay: Int,
    val name: String,
    val dose: String,
    val takenAtMillis: Long?,
    val snoozedUntilMillis: Long?,
)

/** A day whose doses are in [DoseLogEntity] (a day with none is listed too). */
@Entity(tableName = "dose_log_days")
data class DoseLogDayEntity(@PrimaryKey val epochDay: Long)

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

    @Query("SELECT * FROM dose_log WHERE epochDay = :epochDay")
    fun observeLogOn(epochDay: Long): Flow<List<DoseLogEntity>>

    /** The logged doses from [fromEpochDay] to [toEpochDay], both included. */
    @Query("SELECT * FROM dose_log WHERE epochDay BETWEEN :fromEpochDay AND :toEpochDay")
    fun observeLogBetween(fromEpochDay: Long, toEpochDay: Long): Flow<List<DoseLogEntity>>

    @Query("SELECT * FROM dose_log WHERE medicineId = :medicineId AND epochDay = :epochDay AND minuteOfDay = :minuteOfDay")
    suspend fun logEntry(medicineId: Long, epochDay: Long, minuteOfDay: Int): DoseLogEntity?

    @Query("SELECT * FROM dose_log WHERE medicineId = :medicineId AND epochDay = :epochDay")
    suspend fun logOf(medicineId: Long, epochDay: Long): List<DoseLogEntity>

    @Query("SELECT * FROM dose_log WHERE snoozedUntilMillis IS NOT NULL")
    suspend fun snoozedEntries(): List<DoseLogEntity>

    @Upsert
    suspend fun upsertLogEntry(entry: DoseLogEntity)

    /** Adds doses, keeping any already logged at the same time. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLogEntries(entries: List<DoseLogEntity>)

    @Delete
    suspend fun deleteLogEntries(entries: List<DoseLogEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLogDay(day: DoseLogDayEntity)

    /** The latest day whose doses are logged, or null when none is. */
    @Query("SELECT MAX(epochDay) FROM dose_log_days")
    suspend fun lastLoggedDay(): Long?

    /** Writes a day's doses as scheduled, once: doses already logged on it are kept. */
    @Transaction
    suspend fun logDay(epochDay: Long, entries: List<DoseLogEntity>) {
        insertLogEntries(entries)
        insertLogDay(DoseLogDayEntity(epochDay))
    }

    @Query("DELETE FROM dose_log WHERE epochDay < :epochDay")
    suspend fun deleteLogEntriesBefore(epochDay: Long)

    @Query("DELETE FROM dose_log_days WHERE epochDay < :epochDay")
    suspend fun deleteLogDaysBefore(epochDay: Long)

    /** Drops every day before [epochDay] from the log (doses and day marks). */
    @Transaction
    suspend fun deleteLogBefore(epochDay: Long) {
        deleteLogEntriesBefore(epochDay)
        deleteLogDaysBefore(epochDay)
    }

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

    @Query("DELETE FROM dose_log")
    suspend fun deleteAllLogEntries()

    @Query("DELETE FROM dose_log_days")
    suspend fun deleteAllLogDays()

    @Query("DELETE FROM appointment_reminders")
    suspend fun deleteAllAppointments()

    /** Everything, on sign-out and account deletion. */
    @Transaction
    suspend fun deleteAll() {
        deleteAllLogEntries()
        deleteAllLogDays()
        deleteAllMedicines()
        deleteAllAppointments()
    }
}

/**
 * Reminders live on this phone only (never Firestore) and are wiped on sign-out. Excluded from
 * backup and device transfer by data_extraction_rules. Version 2 (the dose log, [MIGRATION_1_2]);
 * any schema change needs a real migration (the schema JSON for each version is committed under
 * app/schemas), never a destructive fallback.
 */
@Database(
    entities = [MedicineEntity::class, DoseLogEntity::class, DoseLogDayEntity::class, AppointmentReminderEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class ReminderDatabase : RoomDatabase() {
    abstract fun reminderDao(): ReminderDao

    companion object {
        private const val NAME = "reminders.db"

        fun create(context: Context): ReminderDatabase =
            Room.databaseBuilder(context.applicationContext, ReminderDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2)
                .addCallback(SecureDelete)
                .build()

        /**
         * Version 1 kept only what happened to a dose (`dose_records`, deleted with its
         * medicine) and worked each day's doses out from the medicines as they are now. Its
         * taken and snoozed records move to `dose_log` with the medicine's current name and dose
         * (the best known); records with neither carried nothing. No day is logged yet, so the
         * engine's first pass logs the days before the update from the medicines as they are,
         * and the history looks as it did before the update.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `dose_log` (`medicineId` INTEGER NOT NULL, `epochDay` INTEGER NOT NULL, " +
                        "`minuteOfDay` INTEGER NOT NULL, `name` TEXT NOT NULL, `dose` TEXT NOT NULL, `takenAtMillis` INTEGER, " +
                        "`snoozedUntilMillis` INTEGER, PRIMARY KEY(`medicineId`, `epochDay`, `minuteOfDay`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_dose_log_epochDay` ON `dose_log` (`epochDay`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `dose_log_days` (`epochDay` INTEGER NOT NULL, PRIMARY KEY(`epochDay`))")
                db.execSQL(
                    "INSERT INTO `dose_log` (`medicineId`, `epochDay`, `minuteOfDay`, `name`, `dose`, `takenAtMillis`, `snoozedUntilMillis`) " +
                        "SELECT r.`medicineId`, r.`epochDay`, r.`minuteOfDay`, m.`name`, m.`dose`, r.`takenAtMillis`, r.`snoozedUntilMillis` " +
                        "FROM `dose_records` r JOIN `medicines` m ON m.`id` = r.`medicineId` " +
                        "WHERE r.`takenAtMillis` IS NOT NULL OR r.`snoozedUntilMillis` IS NOT NULL",
                )
                db.execSQL("DROP TABLE `dose_records`")
            }
        }

        /** Deleted rows (medicine names) are overwritten on disk, not just unlinked. */
        private object SecureDelete : Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                db.query("PRAGMA secure_delete = ON").close()
            }
        }
    }
}
