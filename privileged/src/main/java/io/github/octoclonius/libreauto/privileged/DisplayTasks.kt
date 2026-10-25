package io.github.octoclonius.libreauto.privileged

import android.app.ActivityManager
import android.app.ActivityOptions
import android.app.TaskStackListener
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.RemoteException
import android.util.Log
import java.io.Closeable

internal class DisplayTasks(
    private val context: Context,
    private val displayId: Int,
    private val listener: IDisplayTaskListener,
) : Closeable {

    companion object {
        private const val TAG = "DisplayTasks"
    }

    private val taskManager = SystemServices.activityTaskManager()
    private val thread = HandlerThread("display-tasks").apply { start() }
    private val handler = Handler(thread.looper)
    private var reported: List<DisplayTask>? = null

    @Volatile
    var taskIds: Set<Int> = emptySet()
        private set

    private val stackListener =
        object : TaskStackListener() {
            override fun onTaskStackChanged() {
                handler.post(::report)
            }

            override fun onTaskCreated(taskId: Int, componentName: ComponentName) {
                handler.post(::report)
            }

            override fun onTaskRemoved(taskId: Int) {
                handler.post(::report)
            }

            override fun onTaskMovedToFront(taskInfo: ActivityManager.RunningTaskInfo) {
                handler.post(::report)
            }

            override fun onTaskMovedToBack(taskInfo: ActivityManager.RunningTaskInfo) {
                handler.post(::report)
            }

            override fun onTaskDisplayChanged(taskId: Int, newDisplayId: Int) {
                handler.post(::report)
            }
        }

    fun start() {
        taskManager.registerTaskStackListener(stackListener)
        handler.post(::report)
    }

    fun show(taskId: Int) {
        val options =
            ActivityOptions.makeCustomAnimation(context, 0, 0).setLaunchDisplayId(displayId)
        ActivityOptions::class
            .java
            .getMethod("setOverrideTaskTransition", Boolean::class.javaPrimitiveType)
            .invoke(options, true)
        taskManager.startActivityFromRecents(taskId, options.toBundle())
        Log.i(TAG, "Showed the task $taskId on the display $displayId")
    }

    fun close(taskId: Int) {
        if (taskManager.removeTask(taskId)) {
            Log.i(TAG, "Closed the task $taskId on the display $displayId")
        } else {
            Log.w(TAG, "The task $taskId on the display $displayId was already gone")
        }
    }

    override fun close() {
        try {
            taskManager.unregisterTaskStackListener(stackListener)
        } finally {
            thread.quitSafely()
            thread.join()
        }
    }

    private fun report() {
        val tasks =
            try {
                taskManager.getTasks(Int.MAX_VALUE, false, false, displayId).mapNotNull {
                    val packageName =
                        it.baseActivity?.packageName
                            ?: it.baseIntent.component?.packageName
                            ?: return@mapNotNull null
                    DisplayTask(it.taskId, packageName)
                }
            } catch (e: RemoteException) {
                Log.e(TAG, "Listing the tasks on the display $displayId failed", e)
                return
            }
        taskIds = tasks.mapTo(HashSet()) { it.taskId }
        if (tasks == reported) return
        reported = tasks
        try {
            listener.onTasks(tasks.toTypedArray())
        } catch (e: RemoteException) {
            Log.w(TAG, "The app is gone; dropped the tasks on the display $displayId", e)
        }
    }
}
