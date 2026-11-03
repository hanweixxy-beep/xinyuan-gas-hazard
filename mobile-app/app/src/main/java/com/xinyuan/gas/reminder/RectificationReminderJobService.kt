package com.xinyuan.gas.reminder

import android.app.job.JobParameters
import android.app.job.JobService
import com.xinyuan.gas.data.sync.CloudApiException
import com.xinyuan.gas.data.sync.CloudSessionStore
import com.xinyuan.gas.data.sync.HttpCloudSyncClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

class RectificationReminderJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val running = mutableMapOf<Int, kotlinx.coroutines.Job>()

    override fun onStartJob(params: JobParameters): Boolean {
        running[params.jobId] = scope.launch {
            var retry = false
            try {
                val session = CloudSessionStore(this@RectificationReminderJobService).load()
                if (session.isLoggedIn) {
                    val result = withContext(Dispatchers.IO) {
                        HttpCloudSyncClient(applicationContext, session.baseUrl, session.token)
                            .fetchPendingReminderRecords()
                    }
                    ensureActive()
                    result.fold(onSuccess = { records ->
                        RectificationReminder.publish(this@RectificationReminderJobService, session, records)
                    }, onFailure = { error ->
                        retry = error is IOException || (error is CloudApiException && error.statusCode >= 500)
                        // Do not notify from stale local data after a failed/unauthorized request.
                    })
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                retry = error is IOException
            } finally {
                if (kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]?.isActive == true) {
                    running.remove(params.jobId)
                    jobFinished(params, retry)
                }
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        running.remove(params.jobId)?.cancel()
        return true
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
