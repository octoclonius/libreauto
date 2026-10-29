package io.github.octoclonius.libreauto.car.rail

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.ui.unit.Dp
import io.github.octoclonius.libreauto.car.apps.loadCarIcon
import io.github.octoclonius.libreauto.session.tasks.CarTask
import io.github.octoclonius.libreauto.session.tasks.CarTasksState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

const val MAX_RAIL_APPS = 4

class RailApp(val task: CarTask, val label: String, val icon: Drawable)

internal fun withoutClosed(state: CarTasksState, closed: Set<Int>): CarTasksState =
    if (closed.isEmpty()) state
    else
        CarTasksState(
            state.tasks.filter { it.taskId !in closed },
            state.frontTaskId?.takeIf { it !in closed },
        )

internal fun nextRailOrder(
    order: List<CarTask>,
    rememberedTaskIds: List<Int>,
    state: CarTasksState,
    shown: Int,
): List<CarTask> {
    val remembered = rememberedTaskIds.mapNotNull { id -> state.tasks.find { it.taskId == id } }
    return railOrder(order.ifEmpty { remembered }, state, shown)
}

internal fun railOrder(order: List<CarTask>, state: CarTasksState, shown: Int): List<CarTask> {
    val stillOpen = order.filter { it in state.tasks }
    val opened = state.tasks.filter { it !in stillOpen }
    val next = opened + stillOpen
    val front = next.indexOfFirst { it.taskId == state.frontTaskId }
    return if (front < shown) next
    else listOf(next[front]) + next.filterIndexed { i, _ -> i != front }
}

internal fun railApps(
    context: Context,
    order: Flow<List<CarTask>>,
    carDensities: Flow<Int>,
    size: Dp,
): Flow<List<RailApp>> = flow {
    val looks = HashMap<Pair<String, Int>, Pair<String, Drawable>>()
    combine(order, carDensities, ::Pair).collect { (tasks, carDensity) ->
        emit(
            tasks.map { task ->
                val (label, icon) =
                    looks.getOrPut(task.packageName to carDensity) {
                        appLook(context, task.packageName, carDensity, size)
                    }
                RailApp(task, label, icon)
            }
        )
    }
}
    .flowOn(Dispatchers.IO)

private fun appLook(
    context: Context,
    packageName: String,
    carDensity: Int,
    size: Dp,
): Pair<String, Drawable> {
    val packageManager = context.packageManager
    val launcher =
        packageManager
            .queryIntentActivities(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setPackage(packageName),
                PackageManager.ResolveInfoFlags.of(0),
            )
            .firstOrNull()
    if (launcher != null) {
        return launcher.loadLabel(packageManager).toString() to
            launcher.loadCarIcon(packageManager, carDensity, size)
    }
    return try {
        val info =
            packageManager.getApplicationInfo(
                packageName,
                PackageManager.ApplicationInfoFlags.of(0),
            )
        info.loadLabel(packageManager).toString() to info.loadIcon(packageManager)
    } catch (e: PackageManager.NameNotFoundException) {
        packageName to packageManager.defaultActivityIcon
    }
}
