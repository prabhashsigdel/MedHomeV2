package com.medhome.nepal

import android.app.Application
import android.util.Log
import com.medhome.nepal.reminders.ReminderChannels
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class MedHomeApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.sessionManager.start()
        ReminderChannels.ensure(this)
        // A force stop cancels every alarm, and an inexact one may still be waiting: set them all
        // again from the database at every start (it keeps a dose whose alarm hasn't fired yet).
        container.backgroundScope.launch {
            try {
                container.reminderEngine.rescheduleAll()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Rescheduling reminders failed: ${e.javaClass.simpleName}")
            }
        }
        container.appointmentReminderSync.start()
    }

    private companion object {
        const val TAG = "MedHomeApplication"
    }
}
