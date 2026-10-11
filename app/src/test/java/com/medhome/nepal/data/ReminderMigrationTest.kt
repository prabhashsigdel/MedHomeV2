package com.medhome.nepal.data

import android.app.Application
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.medhome.nepal.domain.CalendarDate
import com.medhome.nepal.domain.DoseSchedule
import com.medhome.nepal.domain.DoseState
import com.medhome.nepal.domain.NepalTime
import com.medhome.nepal.domain.TimeOfDay
import com.medhome.nepal.fakes.FakeAlarmScheduler
import com.medhome.nepal.fakes.FakeReminderNotifier
import com.medhome.nepal.fakes.InMemoryReminderSettings
import com.medhome.nepal.reminders.ReminderEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * Version 1 to 2: a database made from the committed 1.json schema, with version 1 rows, opened
 * by Room with [ReminderDatabase.MIGRATION_1_2]. Room checks the migrated tables against version
 * 2's entities when it opens, so a mismatch fails here, not on a phone.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [35])
class ReminderMigrationTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val name = "migration-test.db"
    private var room: ReminderDatabase? = null

    private val thursday = CalendarDate(2026, 10, 8)
    private val wednesday = thursday.plusDays(-1)
    private val eight = TimeOfDay(8 * 60)
    private val twenty = TimeOfDay(20 * 60)

    @After
    fun tearDown() {
        room?.close()
        context.deleteDatabase(name)
    }

    /** Creates [name] at version 1 exactly as 1.json describes it, then fills it with [fill]. */
    private fun createVersion1(fill: (SupportSQLiteDatabase) -> Unit) {
        val schema = JSONObject(File("schemas/com.medhome.nepal.data.ReminderDatabase/1.json").readText()).getJSONObject("database")
        val callback = object : SupportSQLiteOpenHelper.Callback(1) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                val entities = schema.getJSONArray("entities")
                for (i in 0 until entities.length()) {
                    val entity = entities.getJSONObject(i)
                    val table = entity.getString("tableName")
                    db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                    val indices = entity.optJSONArray("indices") ?: continue
                    for (j in 0 until indices.length()) {
                        db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                    }
                }
                val setup = schema.getJSONArray("setupQueries")
                for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        val config = SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(callback).build()
        FrameworkSQLiteOpenHelperFactory().create(config).use { helper -> fill(helper.writableDatabase) }
    }

    private fun SupportSQLiteDatabase.medicine(id: Long, name: String, times: String) = insert(
        "medicines",
        SQLiteDatabase.CONFLICT_ABORT,
        ContentValues().apply {
            put("id", id)
            put("name", name)
            put("dose", "1 tablet")
            put("times", times)
            put("everyDay", 1)
            put("weekdays", 0)
            put("startEpochDay", thursday.plusDays(-10).epochDay)
        },
    )

    private fun SupportSQLiteDatabase.record(medicineId: Long, date: CalendarDate, time: TimeOfDay, taken: Long?, snoozedUntil: Long?) = insert(
        "dose_records",
        SQLiteDatabase.CONFLICT_ABORT,
        ContentValues().apply {
            put("medicineId", medicineId)
            put("epochDay", date.epochDay)
            put("minuteOfDay", time.minutes)
            put("takenAtMillis", taken)
            put("snoozedUntilMillis", snoozedUntil)
        },
    )

    private fun openMigrated(): ReminderDatabase =
        Room.databaseBuilder(context, ReminderDatabase::class.java, name)
            .addMigrations(ReminderDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
            .also { room = it }

    @Test
    fun `taken and snoozed records move to the log with their medicine's name and dose`() = runTest {
        createVersion1 { db ->
            db.medicine(1, "Paracetamol", "480,1200")
            db.record(1, wednesday, eight, taken = 111L, snoozedUntil = null)
            db.record(1, thursday, eight, taken = null, snoozedUntil = 222L)
            // Neither taken nor snoozed (a snooze cleared by an edit): carried nothing.
            db.record(1, wednesday, twenty, taken = null, snoozedUntil = null)
        }
        val dao = openMigrated().reminderDao()

        assertEquals(
            DoseLogEntity(1, wednesday.epochDay, eight.minutes, "Paracetamol", "1 tablet", takenAtMillis = 111L, snoozedUntilMillis = null),
            dao.logEntry(1, wednesday.epochDay, eight.minutes),
        )
        assertEquals(222L, dao.logEntry(1, thursday.epochDay, eight.minutes)?.snoozedUntilMillis)
        assertNull(dao.logEntry(1, wednesday.epochDay, twenty.minutes))
        assertNull(dao.lastLoggedDay())
        // The medicine itself is untouched.
        assertEquals(listOf("Paracetamol"), dao.medicines().map { it.name })
    }

    @Test
    fun `the old records table is replaced by the log tables`() = runTest {
        createVersion1 { db -> db.medicine(1, "Paracetamol", "480") }
        val db = openMigrated()
        db.reminderDao().medicines()
        db.openHelper.readableDatabase.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { cursor ->
            val tables = buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
            assertFalse("dose_records" in tables)
            assertEquals(true, "dose_log" in tables && "dose_log_days" in tables)
        }
    }

    @Test
    fun `after the update the history looks as it did before`() = runTest {
        createVersion1 { db ->
            db.medicine(1, "Paracetamol", "480,1200")
            db.record(1, wednesday, eight, taken = 111L, snoozedUntil = null)
        }
        val dao = openMigrated().reminderDao()
        val now = NepalTime.epochMillis(thursday, TimeOfDay(7 * 60))
        val settings = InMemoryReminderSettings().apply { ownerState.value = "uid" }
        val engine = ReminderEngine(dao, settings, FakeAlarmScheduler(), FakeReminderNotifier(), { "uid" }, { "uid" }, { now })

        engine.rescheduleAll()

        // Version 1 worked every day out from the medicine: the first pass logs them the same way.
        val history = DoseSchedule.history(engine.dosesBetween(thursday.plusDays(-30), wednesday).first(), thursday.plusDays(-30), wednesday, now)
        assertEquals(10, history.size)
        assertEquals(listOf(DoseState.TAKEN, DoseState.MISSED), history.first().second.map { it.state })
        assertEquals(listOf(eight, twenty), engine.dosesOn(thursday).first().map { it.dose.time }.sorted())
        assertEquals(thursday.epochDay, dao.lastLoggedDay())
    }
}
