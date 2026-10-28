package io.github.octoclonius.libreauto

import android.content.Context
import io.github.octoclonius.libreauto.car.CarContainer
import io.github.octoclonius.libreauto.session.SessionContainer

class AppContainer(context: Context) {

    val sessionContainer = SessionContainer(context)
    val carContainer = CarContainer(context, sessionContainer.carSessionHolder)
}
