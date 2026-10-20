package io.github.octoclonius.libreauto.car.home

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import io.github.octoclonius.libreauto.car.CarHomeActivity
import io.github.octoclonius.libreauto.session.video.CarDisplay

fun onAppsButton(context: Context) {
    if (HomeState.isHomeShown.value) {
        HomeState.toggleDrawer()
        return
    }
    val display = CarDisplay.state.value ?: return
    val intent =
        Intent(context, CarHomeActivity::class.java)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
    context.startActivity(
        intent,
        ActivityOptions.makeBasic().setLaunchDisplayId(display.id).toBundle(),
    )
}
