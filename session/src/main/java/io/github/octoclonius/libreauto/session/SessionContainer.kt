package io.github.octoclonius.libreauto.session

import android.content.ComponentName
import android.content.Context
import io.github.octoclonius.libreauto.session.calls.CallRepository
import io.github.octoclonius.libreauto.session.certificate.CertificateRepository
import io.github.octoclonius.libreauto.session.privileged.PrivilegedServiceConnection
import io.github.octoclonius.libreauto.session.settings.PhoneSettingsRepository
import io.github.octoclonius.libreauto.session.video.CarHome

class SessionContainer(
    private val context: Context,
    privilegedService: ComponentName,
    carHome: ComponentName,
) {

    val privilegedServiceConnection = PrivilegedServiceConnection(context, privilegedService)
    val carSessionHolder = CarSessionHolder()
    val callRepository = CallRepository(context, privilegedServiceConnection)
    val certificateRepository = CertificateRepository(context)
    val phoneSettingsRepository = PhoneSettingsRepository(context, privilegedServiceConnection)
    private val carHome = CarHome(context, carHome)

    internal fun carConnection(): CarConnection =
        CarConnection(carSessionHolder, certificateRepository) { link, control, version, car ->
            CarSession(
                context,
                link,
                control,
                version,
                car,
                callRepository,
                privilegedServiceConnection,
                carHome,
            )
        }

    interface Provider {
        val sessionContainer: SessionContainer
    }
}

val Context.sessionContainer: SessionContainer
    get() = (applicationContext as SessionContainer.Provider).sessionContainer
