package com.medhome.nepal.ui.reminders

import com.medhome.nepal.reminders.ReminderSetupItem
import com.medhome.nepal.data.ReminderPrefs
import com.medhome.nepal.domain.AppointmentReminder
import com.medhome.nepal.domain.CalendarDate
import com.medhome.nepal.domain.DoseState
import com.medhome.nepal.domain.NepalTime
import com.medhome.nepal.domain.TimeOfDay
import com.medhome.nepal.fakes.FakeReminderRepository
import com.medhome.nepal.fakes.medicine
import com.medhome.nepal.ui.common.TICK_MS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReminderViewModelsTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val thursday = CalendarDate(2026, 10, 8)
    private val eight = TimeOfDay(8 * 60)
    private val twenty = TimeOfDay(20 * 60)
    private var now = NepalTime.epochMillis(thursday, TimeOfDay(7 * 60))
    private val repo = FakeReminderRepository()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun <T> TestScope.collecting(flow: StateFlow<T>): StateFlow<T> {
        backgroundScope.launch(dispatcher) { flow.collect {} }
        return flow
    }

    // Settings switches

    @Test
    fun `the switches turn each kind of reminder on and off`() = runTest(dispatcher) {
        val vm = ReminderSettingsViewModel(repo)
        val prefs = collecting(vm.prefs)
        assertEquals(ReminderPrefs(), prefs.value)

        vm.setMedicineReminders(false)
        vm.setAppointmentReminders(false)
        assertEquals(ReminderPrefs(medicineReminders = false, appointmentReminders = false), prefs.value)
        vm.setMedicineReminders(true)
        assertEquals(listOf("medicineReminders:false", "appointmentReminders:false", "medicineReminders:true"), repo.calls)
    }

    @Test
    fun `a failed switch says so and keeps the old value`() = runTest(dispatcher) {
        val vm = ReminderSettingsViewModel(repo)
        val prefs = collecting(vm.prefs)
        repo.failNext = true
        vm.setMedicineReminders(false)
        assertTrue(vm.changeFailed.value)
        assertEquals(true, prefs.value?.medicineReminders)
        vm.dismissError()
        assertFalse(vm.changeFailed.value)
    }

    // Today's doses

    @Test
    fun `today's doses show their states and a tap marks one taken, a second tap undoes it`() = runTest(dispatcher) {
        repo.medicineState.value = listOf(medicine(times = listOf(eight, twenty), startDate = thursday))
        now = NepalTime.epochMillis(thursday, TimeOfDay(9 * 60 + 30))
        val vm = TodayRemindersViewModel(repo) { now }
        val state = collecting(vm.state)
        assertEquals(listOf(DoseState.MISSED, DoseState.UPCOMING), state.value.doses.map { it.state })
        assertEquals(1, state.value.missed.size)

        vm.toggleTaken(state.value.doses.first())
        assertEquals(listOf(DoseState.TAKEN, DoseState.UPCOMING), state.value.doses.map { it.state })
        vm.toggleTaken(state.value.doses.first())
        assertEquals(DoseState.MISSED, state.value.doses.first().state)
    }

    @Test
    fun `an upcoming dose becomes missed an hour after its time as the clock moves`() = runTest(dispatcher) {
        repo.medicineState.value = listOf(medicine(times = listOf(eight), startDate = thursday))
        now = NepalTime.epochMillis(thursday, TimeOfDay(8 * 60 + 30))
        val vm = TodayRemindersViewModel(repo) { now }
        val state = collecting(vm.state)
        assertEquals(DoseState.UPCOMING, state.value.doses.single().state)
        now += 31 * NepalTime.MILLIS_PER_MINUTE
        advanceTimeBy(TICK_MS + 1)
        assertEquals(DoseState.MISSED, state.value.doses.single().state)
    }

    @Test
    fun `only today's appointments that haven't started are listed`() = runTest(dispatcher) {
        val later = NepalTime.epochMillis(thursday, TimeOfDay(15 * 60))
        val past = NepalTime.epochMillis(thursday, TimeOfDay(6 * 60))
        val tomorrow = NepalTime.epochMillis(thursday.plusDays(1), TimeOfDay(9 * 60))
        val repo = FakeReminderRepository(
            appointments = listOf(AppointmentReminder("a", later, "Dr A"), AppointmentReminder("b", past, ""), AppointmentReminder("c", tomorrow, "")),
        )
        val state = collecting(TodayRemindersViewModel(repo) { now }.state)
        assertEquals(listOf("a"), state.value.appointments.map { it.bookingId })
        assertFalse(state.value.hasMedicines)
    }

    // The form

    @Test
    fun `saving a new medicine closes the form`() = runTest(dispatcher) {
        val first = MedicineFormViewModel(MedicineFormViewModel.NEW, repo) { now }
        first.onNameChange("Paracetamol")
        first.onDoseChange("1 tablet")
        first.save()
        assertEquals(FormResult.Saved, first.uiState.value.result)

        val second = MedicineFormViewModel(MedicineFormViewModel.NEW, repo) { now }
        second.onNameChange("Vitamin D")
        second.onDoseChange("1 capsule")
        second.save()
        assertEquals(FormResult.Saved, second.uiState.value.result)
        assertEquals(2, repo.medicineState.value.size)
    }

    // The reminder setup dialog, after a save

    @Test
    fun `the setup dialog offers what is missing, each item once ever`() = runTest(dispatcher) {
        val vm = MedicinesViewModel(repo)
        val items = collecting(vm.setupItems)
        vm.checkSetup(setOf(ReminderSetupItem.NOTIFICATIONS, ReminderSetupItem.BACKGROUND))
        assertEquals(setOf(ReminderSetupItem.NOTIFICATIONS, ReminderSetupItem.BACKGROUND), items.value)
        vm.dismissSetup()
        assertTrue(items.value.isEmpty())

        // Still missing after the next save: not offered again. A newly missing one is.
        vm.checkSetup(setOf(ReminderSetupItem.NOTIFICATIONS, ReminderSetupItem.BACKGROUND, ReminderSetupItem.EXACT_ALARMS))
        assertEquals(setOf(ReminderSetupItem.EXACT_ALARMS), items.value)
        vm.dismissSetup()
        vm.checkSetup(ReminderSetupItem.entries.toSet())
        assertTrue(items.value.isEmpty())
    }

    @Test
    fun `nothing missing shows no dialog and offers nothing`() = runTest(dispatcher) {
        val vm = MedicinesViewModel(repo)
        val items = collecting(vm.setupItems)
        vm.checkSetup(emptySet())
        assertTrue(items.value.isEmpty())
        assertTrue(repo.prefsState.value.setupItemsShown.isEmpty())
    }

    @Test
    fun `a failed check shows no dialog`() = runTest(dispatcher) {
        val vm = MedicinesViewModel(repo)
        val items = collecting(vm.setupItems)
        repo.failNext = true
        vm.checkSetup(setOf(ReminderSetupItem.BACKGROUND))
        assertTrue(items.value.isEmpty())
    }

    @Test
    fun `an invalid form shows its errors and saves nothing`() = runTest(dispatcher) {
        val vm = MedicineFormViewModel(MedicineFormViewModel.NEW, repo) { now }
        assertTrue(vm.uiState.value.errors.isEmpty)
        vm.save()
        assertFalse(vm.uiState.value.errors.isEmpty)
        assertNull(vm.uiState.value.result)
        assertTrue(repo.calls.isEmpty())
    }

    @Test
    fun `editing loads the medicine and deleting closes the form`() = runTest(dispatcher) {
        repo.medicineState.value = listOf(medicine(id = 4, name = "Aspirin", startDate = thursday))
        val vm = MedicineFormViewModel(4, repo) { now }
        assertFalse(vm.uiState.value.isNew)
        assertEquals("Aspirin", vm.uiState.value.fields.name)
        vm.askToDelete()
        assertTrue(vm.uiState.value.confirmingDelete)
        vm.confirmDelete()
        assertEquals(FormResult.Deleted, vm.uiState.value.result)
        assertEquals(listOf("delete:4"), repo.calls)
    }

    @Test
    fun `a medicine that is gone shows not found`() = runTest(dispatcher) {
        val vm = MedicineFormViewModel(99, repo) { now }
        assertTrue(vm.uiState.value.notFound)
        assertFalse(vm.uiState.value.canEdit)
    }

    @Test
    fun `a failed save can be retried`() = runTest(dispatcher) {
        val vm = MedicineFormViewModel(MedicineFormViewModel.NEW, repo) { now }
        vm.onNameChange("Paracetamol")
        vm.onDoseChange("1 tablet")
        repo.failNext = true
        vm.save()
        assertTrue(vm.uiState.value.changeFailed)
        assertNull(vm.uiState.value.result)
        vm.save()
        assertTrue(vm.uiState.value.result is FormResult.Saved)
    }
}
