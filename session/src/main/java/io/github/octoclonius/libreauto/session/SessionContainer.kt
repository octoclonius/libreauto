package io.github.octoclonius.libreauto.session

import android.content.ComponentName
import android.content.Context
import io.github.octoclonius.libreauto.session.calls.CallRepository
import io.github.octoclonius.libreauto.session.certificate.CertificateRepository
import io.github.octoclonius.libreauto.session.display.CarHome
import io.github.octoclonius.libreauto.session.media.MediaResumption
import io.github.octoclonius.libreauto.session.privileged.PrivilegedServiceConnection
import io.github.octoclonius.libreauto.session.settings.PhoneSettingsRepository
import io.github.octoclonius.libreauto.session.tasks.OpenCarTasksRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class SessionContainer(
    private val context: Context,
    applicationScope: CoroutineScope,
    privilegedService: ComponentName,
    carHome: ComponentName,
) {

    val privilegedServiceConnection = PrivilegedServiceConnection(context, privilegedService)
    val carSessionHolder = CarSessionHolder()
    val callRepository = CallRepository(context, privilegedServiceConnection, applicationScope)
    val certificateRepository = CertificateRepository(context, applicationScope)
    val phoneSettingsRepository = PhoneSettingsRepository(context, privilegedServiceConnection)
    private val carHome = CarHome(context, carHome)
    private val openCarTasksRepository = OpenCarTasksRepository(context, applicationScope)
    private val mediaResumption = MediaResumption(context, applicationScope)

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
                openCarTasksRepository,
                mediaResumption,
                Dispatchers.IO.limitedParallelism(1),
            )
        }

    interface Provider {
        val sessionContainer: SessionContainer
    }
}

val Context.sessionContainer: SessionContainer
    get() = (applicationContext as SessionContainer.Provider).sessionContainer
