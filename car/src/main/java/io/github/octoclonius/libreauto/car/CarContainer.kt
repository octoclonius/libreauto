package io.github.octoclonius.libreauto.car

import android.content.Context
import io.github.octoclonius.libreauto.session.CarSessionHolder

class CarContainer(context: Context, carSessionHolder: CarSessionHolder) {

    val carUiHolder = CarUiHolder(context, carSessionHolder)

    interface Provider {
        val carContainer: CarContainer
    }
}

val Context.carContainer: CarContainer
    get() = (applicationContext as CarContainer.Provider).carContainer
