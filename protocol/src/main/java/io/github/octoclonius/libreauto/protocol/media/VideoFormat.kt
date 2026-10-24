package io.github.octoclonius.libreauto.protocol.media

import io.github.octoclonius.libreauto.protocol.control.ProtocolVersion
import io.github.octoclonius.libreauto.protocol.control.UiInsets
import io.github.octoclonius.libreauto.protocol.control.UiRect
import io.github.octoclonius.libreauto.protocol.control.VideoConfig
import java.net.ProtocolException
import kotlin.math.roundToInt

private const val VIDEO_1280X720 = 2
private const val VIDEO_1920X1080 = 3
private const val VIDEO_2560X1440 = 4
private const val VIDEO_3840X2160 = 5
private const val VIDEO_720X1280 = 6
private const val VIDEO_1080X1920 = 7
private const val VIDEO_1440X2560 = 8
private const val VIDEO_2160X3840 = 9

private const val VIDEO_FPS_30 = 2

private const val SQUARE_PIXEL_ASPECT_RATIO_E4 = 10_000

private const val DEFAULT_DECODER_DEPTH = 4
private const val FIRST_MAJOR_WITHOUT_DECODER_DEPTH = 6

data class CarInsets(val top: Int, val bottom: Int, val left: Int, val right: Int) {
    companion object {
        val NONE = CarInsets(0, 0, 0, 0)
    }
}

data class CarRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val area: Long
        get() =
            if (right > left && bottom > top) (right.toLong() - left) * (bottom.toLong() - top)
            else 0
}

data class VideoFormat(
    val frameWidth: Int,
    val frameHeight: Int,
    val framesPerSecond: Int,
    val density: Int,
    val margins: CarInsets = CarInsets.NONE,
    val contentInsets: CarInsets = CarInsets.NONE,
    val stableInsets: CarInsets = CarInsets.NONE,
    val pixelAspectRatioE4: Int = SQUARE_PIXEL_ASPECT_RATIO_E4,
    val cutouts: List<CarRect> = emptyList(),
) {
    val visibleWidth: Int
        get() = frameWidth - margins.left - margins.right

    val visibleHeight: Int
        get() = frameHeight - margins.top - margins.bottom

    val pixelsOutsideCutouts: Int
        get() =
            (visibleWidth.toLong() * visibleHeight - cutouts.sumOf { it.area })
                .coerceAtLeast(0)
                .toInt()

    val displayWidth: Int
        get() =
            if (pixelAspectRatioE4 > SQUARE_PIXEL_ASPECT_RATIO_E4) {
                (visibleWidth.toDouble() * pixelAspectRatioE4 / SQUARE_PIXEL_ASPECT_RATIO_E4)
                    .roundToInt()
            } else {
                visibleWidth
            }

    val displayHeight: Int
        get() =
            if (pixelAspectRatioE4 < SQUARE_PIXEL_ASPECT_RATIO_E4) {
                (visibleHeight.toDouble() * SQUARE_PIXEL_ASPECT_RATIO_E4 / pixelAspectRatioE4)
                    .roundToInt()
            } else {
                visibleHeight
            }

    val areContentInsetsUsable: Boolean
        get() = contentInsets.leavesArea(visibleWidth, visibleHeight)

    val areStableInsetsUsable: Boolean
        get() = stableInsets.leavesArea(visibleWidth, visibleHeight)

    val displayContentInsets: CarInsets
        get() = if (areContentInsetsUsable) displayInsets(contentInsets) else CarInsets.NONE

    val displayStableInsets: CarInsets
        get() = if (areStableInsetsUsable) displayInsets(stableInsets) else CarInsets.NONE

    val displayCutoutInsets: CarInsets
        get() {
            val cutout = cutouts.firstOrNull() ?: return CarInsets.NONE
            val width = displayX(cutout.right) - displayX(cutout.left)
            if (width <= 0 || width >= displayWidth) return CarInsets.NONE
            return if (cutout.left == 0) {
                CarInsets(top = 0, bottom = 0, left = width, right = 0)
            } else {
                CarInsets(top = 0, bottom = 0, left = 0, right = width)
            }
        }

    private fun displayInsets(insets: CarInsets): CarInsets =
        CarInsets(
            top = displayY(insets.top),
            bottom = displayY(insets.bottom),
            left = displayX(insets.left),
            right = displayX(insets.right),
        )

    fun displayX(visibleX: Int): Int =
        (visibleX.toDouble() * displayWidth / visibleWidth).roundToInt()

    fun displayY(visibleY: Int): Int =
        (visibleY.toDouble() * displayHeight / visibleHeight).roundToInt()

    fun visibleX(displayX: Int): Int =
        (displayX.toDouble() * visibleWidth / displayWidth).roundToInt()

    fun visibleY(displayY: Int): Int =
        (displayY.toDouble() * visibleHeight / displayHeight).roundToInt()
}

@Throws(ProtocolException::class)
fun preferredConfigIndex(
    configs: List<VideoConfig>,
    accepted: List<Int>,
    isEncodable: (width: Int, height: Int, framesPerSecond: Int) -> Boolean,
): Int? {
    if (accepted.isEmpty()) throw ProtocolException("The car accepted no video config")
    accepted
        .firstOrNull { it !in configs.indices }
        ?.let { throw ProtocolException("The car accepted video config $it of ${configs.size}") }
    return accepted
        .sortedByDescending { index -> frameSize(configs[index]).let { (w, h) -> w * h } }
        .firstOrNull { index ->
            val (width, height) = frameSize(configs[index])
            isEncodable(width, height, framesPerSecond(configs[index]))
        }
}

@Throws(ProtocolException::class)
fun videoFormat(config: VideoConfig): VideoFormat {
    val (width, height) = frameSize(config)
    val framesPerSecond = framesPerSecond(config)
    val density = config.density
    if (density == null || density <= 0) {
        throw ProtocolException("The car's video config has density $density")
    }
    val pixelAspectRatioE4 = config.pixel_aspect_ratio_e4 ?: SQUARE_PIXEL_ASPECT_RATIO_E4
    if (pixelAspectRatioE4 <= 0) {
        throw ProtocolException(
            "The car's video config has pixel aspect ratio $pixelAspectRatioE4 / 10000"
        )
    }
    val margins = margins(config)
    val contentInsets = config.ui_config?.content_insets?.let(::insets)
    val stableInsets = config.ui_config?.stable_insets?.let(::insets)
    if (!margins.leavesArea(width, height)) {
        throw ProtocolException(
            "The car's video config has margins $margins for a ${width}x$height frame"
        )
    }
    return VideoFormat(
        width,
        height,
        framesPerSecond,
        density,
        margins,
        contentInsets ?: stableInsets ?: CarInsets.NONE,
        stableInsets ?: contentInsets ?: CarInsets.NONE,
        pixelAspectRatioE4,
        config.ui_config?.cutouts.orEmpty().mapNotNull { it.bounds?.let(::rect) },
    )
}

fun decoderDepth(config: VideoConfig, car: ProtocolVersion): Int =
    if (car.major >= FIRST_MAJOR_WITHOUT_DECODER_DEPTH) {
        0
    } else {
        (config.decoder_additional_depth ?: DEFAULT_DECODER_DEPTH).coerceAtLeast(0)
    }

private fun frameSize(config: VideoConfig): Pair<Int, Int> =
    when (config.resolution) {
        VIDEO_1280X720 -> 1280 to 720
        VIDEO_1920X1080 -> 1920 to 1080
        VIDEO_2560X1440 -> 2560 to 1440
        VIDEO_3840X2160 -> 3840 to 2160
        VIDEO_720X1280 -> 720 to 1280
        VIDEO_1080X1920 -> 1080 to 1920
        VIDEO_1440X2560 -> 1440 to 2560
        VIDEO_2160X3840 -> 2160 to 3840
        else -> 800 to 480
    }

private fun framesPerSecond(config: VideoConfig): Int =
    if (config.frame_rate == VIDEO_FPS_30) 30 else 60

private fun margins(config: VideoConfig): CarInsets {
    val ui = config.ui_config
    if (ui != null) return ui.margins?.let(::insets) ?: CarInsets.NONE
    val width = config.width_margin ?: 0
    val height = config.height_margin ?: 0
    return CarInsets(
        top = height - height / 2,
        bottom = height / 2,
        left = width / 2,
        right = width - width / 2,
    )
}

private fun insets(sides: UiInsets): CarInsets =
    CarInsets(
        top = sides.top ?: 0,
        bottom = sides.bottom ?: 0,
        left = sides.left ?: 0,
        right = sides.right ?: 0,
    )

private fun rect(bounds: UiRect): CarRect =
    CarRect(
        left = bounds.left ?: 0,
        top = bounds.top ?: 0,
        right = bounds.right ?: 0,
        bottom = bounds.bottom ?: 0,
    )

private fun CarInsets.leavesArea(width: Int, height: Int): Boolean =
    minOf(top, bottom, left, right) >= 0 &&
        left.toLong() + right < width &&
        top.toLong() + bottom < height
