package com.worstalarm.clock.data

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.worstalarm.clock.data.entity.AlarmEntity
import com.worstalarm.clock.data.entity.AwakeCheckEntity
import com.worstalarm.clock.data.entity.BarcodeEntity
import com.worstalarm.clock.data.entity.RoutineStepEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Room/DAO coverage (**C3**), hosted by Robolectric so it runs in the fast `testDebugUnitTest`
 * lane with no emulator. The headline case is **B4**: `saveAlarm` is now one transaction, so an
 * alarm and its steps commit together — a failure part-way through rolls the whole thing back
 * instead of leaving an alarm with no steps (which `handleRing` would silently disable).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AlarmPersistenceTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: Repository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = Repository(db.alarmDao(), db.barcodeDao(), db.awakeCheckDao())
    }

    @After
    fun tearDown() = db.close()

    private suspend fun barcode(name: String): Long =
        db.barcodeDao().insert(BarcodeEntity(name = name, rawValue = "raw-$name", format = 256))

    private fun alarm(label: String, enabled: Boolean = true) =
        AlarmEntity(label = label, hour = 7, minute = 0, daysMask = 0, enabled = enabled)

    private fun step(barcodeId: Long, location: String, id: Long = 0) =
        RoutineStepEntity(
            id = id, alarmId = 0, stepIndex = 0, locationLabel = location,
            barcodeId = barcodeId, timeToNextRingSeconds = 0
        )

    @Test
    fun `saveAlarm persists the alarm with its steps in order`() = runBlocking {
        val bath = barcode("bath")
        val kitchen = barcode("kitchen")
        val id = repo.saveAlarm(
            alarm("morning"),
            listOf(step(bath, "Bathroom"), step(kitchen, "Kitchen"))
        )

        val loaded = repo.getAlarm(id)!!
        assertEquals(2, loaded.orderedSteps.size)
        assertEquals(listOf(0, 1), loaded.orderedSteps.map { it.step.stepIndex })
        assertEquals(listOf("Bathroom", "Kitchen"), loaded.orderedSteps.map { it.step.locationLabel })
        // Steps were re-pointed at the generated alarm id inside the save.
        assertTrue(loaded.orderedSteps.all { it.step.alarmId == id })
    }

    @Test
    fun `re-saving an alarm replaces its steps instead of duplicating them`() = runBlocking {
        val bath = barcode("bath")
        val kitchen = barcode("kitchen")
        val id = repo.saveAlarm(alarm("morning"), listOf(step(bath, "Bathroom"), step(kitchen, "Kitchen")))

        // Save again over the same id with a single step.
        repo.saveAlarm(alarm("morning").copy(id = id), listOf(step(bath, "Bathroom only")))

        val loaded = repo.getAlarm(id)!!
        assertEquals(1, loaded.orderedSteps.size)
        assertEquals("Bathroom only", loaded.orderedSteps.single().step.locationLabel)
    }

    @Test
    fun `saveAlarm is atomic - a failing step rolls the whole alarm back (B4)`() = runBlocking {
        val bath = barcode("bath")
        // Two steps forced to share a primary key → the second insert aborts with a UNIQUE
        // constraint. Because the upsert + step writes are one transaction, the alarm row must
        // roll back too. (The pre-fix code inserted the alarm first, in its own transaction, and
        // would have left an orphan alarm with no steps.)
        val clashing = listOf(
            step(bath, "A", id = 5L),
            step(bath, "B", id = 5L)
        )
        try {
            repo.saveAlarm(alarm("atomic"), clashing)
            fail("expected the duplicate-key step to abort the save")
        } catch (_: Exception) {
            // expected
        }

        val all = db.alarmDao().observeAllWithSteps().first()
        assertTrue("the alarm must not have been committed", all.none { it.alarm.label == "atomic" })
        assertEquals("no orphan steps either", 0, db.barcodeDao().usageCount(bath))
    }

    @Test
    fun `deleting an alarm cascades its steps and its awake-check row`() = runBlocking {
        val bath = barcode("bath")
        val id = repo.saveAlarm(alarm("morning"), listOf(step(bath, "Bathroom")))
        repo.saveAwakeCheck(
            AwakeCheckEntity(alarmId = id, dismissedCount = 0, nextCheckAtMs = 123L, popupDeadlineAtMs = 0L)
        )
        assertEquals(1, db.barcodeDao().usageCount(bath))

        repo.deleteAlarm(id)

        assertNull(repo.getAlarm(id))
        assertNull("awake-check row should cascade away", repo.getAwakeCheck(id))
        assertEquals("steps should cascade away", 0, db.barcodeDao().usageCount(bath))
    }

    @Test
    fun `a barcode in use cannot be deleted, an unused one can`() = runBlocking {
        val inUse = barcode("in-use")
        val free = barcode("free")
        repo.saveAlarm(alarm("morning"), listOf(step(inUse, "Bathroom")))

        assertFalse("in-use barcode is protected", repo.deleteBarcode(db.barcodeDao().getById(inUse)!!))
        assertTrue("unused barcode deletes", repo.deleteBarcode(db.barcodeDao().getById(free)!!))
        assertNull(repo.getBarcode(free))
    }
}
