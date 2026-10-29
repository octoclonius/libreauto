package io.github.octoclonius.libreauto.protocol.input

import io.github.octoclonius.libreauto.protocol.control.DEFAULT_DISPLAY_ID
import io.github.octoclonius.libreauto.protocol.control.ServiceDiscoveryResponse
import java.io.IOException

class InputSource(val channel: Int, val keycodes: List<Int>)

fun inputSource(response: ServiceDiscoveryResponse, displayId: Int): InputSource? {
    val service =
        response.services.firstOrNull {
            val source = it.input_source_service
            source != null && (source.display_id ?: DEFAULT_DISPLAY_ID) == displayId
        } ?: return null
    val channel = service.id ?: return null
    return InputSource(channel, service.input_source_service?.keycodes.orEmpty())
}

fun inputBindingRequest(keycodes: List<Int>): ByteArray =
    InputBindingRequest(keycodes = keycodes).encode()

@Throws(IOException::class)
fun parseInputBindingResponse(body: ByteArray): Int? =
    InputBindingResponse.ADAPTER.decode(body).status

@Throws(IOException::class)
fun parseInputReport(body: ByteArray): InputReport = InputReport.ADAPTER.decode(body)

class ButtonPress(val code: Int, val isPressed: Boolean)

fun buttonPresses(report: InputReport): List<ButtonPress> =
    report.buttons
        .flatMap { it.events }
        .mapNotNull { event ->
            event.code?.let { ButtonPress(it, event.pressed == true) }
        }
