package io.github.octoclonius.libreauto.projection

import android.content.ComponentName
import android.content.Context
import io.github.octoclonius.libreauto.projection.calls.CallRepository
import io.github.octoclonius.libreauto.projection.certificate.CertificateRepository
import io.github.octoclonius.libreauto.projection.display.CarHome
import io.github.octoclonius.libreauto.projection.media.MediaResumption
import io.github.octoclonius.libreauto.projection.privileged.PrivilegedServiceConnection
import io.github.octoclonius.libreauto.projection.settings.PhoneSettingsRepository
import io.github.octoclonius.libreauto.projection.tasks.OpenCarTasksRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class ProjectionContainer(
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
        val projectionContainer: ProjectionContainer
    }
}

val Context.projectionContainer: ProjectionContainer
    get() = (applicationContext as ProjectionContainer.Provider).projectionContainer
