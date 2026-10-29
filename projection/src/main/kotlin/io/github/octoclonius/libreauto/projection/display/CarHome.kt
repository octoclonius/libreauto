package io.github.octoclonius.libreauto.projection.display

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent

internal class CarHome(context: Context, val component: ComponentName) {

    val intent: PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setComponent(component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
}
