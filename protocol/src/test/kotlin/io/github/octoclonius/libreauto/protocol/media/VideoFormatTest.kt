package io.github.octoclonius.libreauto.protocol.media

import io.github.octoclonius.libreauto.protocol.control.ProtocolVersion
import io.github.octoclonius.libreauto.protocol.control.UiConfig
import io.github.octoclonius.libreauto.protocol.control.UiInsets
import io.github.octoclonius.libreauto.protocol.control.VideoConfig
import io.github.octoclonius.libreauto.protocol.control.parseServiceDiscoveryResponse
import java.net.ProtocolException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class VideoFormatTest {

    @Test
    fun theDhusConfigIs800x480At30FpsAnd160Dpi() {
        assertEquals(
            VideoFormat(800, 480, 30, 160),
            format(varint(1, 1), varint(2, 2), varint(5, 160)),
        )
    }

    @Test
    fun theResolutionIsField1() {
        val sizes =
            listOf(
                800 to 480,
                1280 to 720,
                1920 to 1080,
                2560 to 1440,
                3840 to 2160,
                720 to 1280,
                1080 to 1920,
                1440 to 2560,
                2160 to 3840,
            )
        sizes.forEachIndexed { index, (width, height) ->
            assertEquals(
                VideoFormat(width, height, 30, 160),
                format(varint(1, index + 1), varint(2, 2), varint(5, 160)),
            )
        }
    }

    @Test
    fun frameRate1Is60Fps() {
        assertEquals(
            VideoFormat(1280, 720, 60, 160),
            format(varint(1, 2), varint(2, 1), varint(5, 160)),
        )
    }

    @Test
    fun aMissingResolutionIs800x480() {
        assertEquals(VideoFormat(800, 480, 30, 160), format(varint(2, 2), varint(5, 160)))
    }

    @Test
    fun anUnknownResolutionIs800x480() {
        assertEquals(
            VideoFormat(800, 480, 30, 160),
            format(varint(1, 0), varint(2, 2), varint(5, 160)),
        )
        assertEquals(
            VideoFormat(800, 480, 30, 160),
            format(varint(1, 10), varint(2, 2), varint(5, 160)),
        )
    }

    @Test
    fun aMissingFrameRateIs60Fps() {
        assertEquals(VideoFormat(1920, 1080, 60, 160), format(varint(1, 3), varint(5, 160)))
    }

    @Test
    fun anUnknownFrameRateIs60Fps() {
        assertEquals(
            VideoFormat(1920, 1080, 60, 160),
            format(varint(1, 3), varint(2, 0), varint(5, 160)),
        )
        assertEquals(
            VideoFormat(1920, 1080, 60, 160),
            format(varint(1, 3), varint(2, 3), varint(5, 160)),
        )
    }

    @Test
    fun theDensityIsField5() {
        assertEquals(
            VideoFormat(1920, 1080, 60, 320),
            format(varint(1, 3), varint(2, 1), varint(5, 320)),
        )
    }

    @Test
    fun aMissingDensityIsAProtocolError() {
        assertThrows(ProtocolException::class.java) { format(varint(1, 1), varint(2, 2)) }
    }

    @Test
    fun aZeroDensityIsAProtocolError() {
        assertThrows(ProtocolException::class.java) {
            format(varint(1, 1), varint(2, 2), varint(5, 0))
        }
    }

    @Test
    fun aNegativeDensityIsAProtocolError() {
        assertThrows(ProtocolException::class.java) {
            format(varint(1, 1), varint(2, 2), varint(5, -160))
        }
    }

    @Test
    fun theWidthAndHeightMarginsSplitWithTheOddPixelRightAndTop() {
        val format = format(varint(1, 2), varint(3, 81), varint(4, 41), varint(5, 160))
        assertEquals(CarInsets(top = 21, bottom = 20, left = 40, right = 41), format?.margins)
        assertEquals(1199, format?.visibleWidth)
        assertEquals(679, format?.visibleHeight)
    }

    @Test
    fun evenMarginsSplitEvenly() {
        assertEquals(
            CarInsets(top = 0, bottom = 0, left = 420, right = 420),
            format(varint(1, 3), varint(3, 840), varint(5, 240))?.margins,
        )
    }

    @Test
    fun theUiConfigsMarginsAreBySide() {
        val format =
            format(
                varint(1, 3),
                varint(4, 400),
                varint(5, 200),
                message(11, sides(1, top = 0, bottom = 400, left = 0, right = 0)),
            )
        assertEquals(CarInsets(top = 0, bottom = 400, left = 0, right = 0), format?.margins)
        assertEquals(1920, format?.visibleWidth)
        assertEquals(680, format?.visibleHeight)
    }

    @Test
    fun aUiConfigWithoutMarginsHasNoneWhateverTheWidthAndHeightMargins() {
        assertEquals(
            CarInsets.NONE,
            format(varint(1, 2), varint(3, 80), varint(4, 40), varint(5, 160), message(11))
                ?.margins,
        )
    }

    @Test
    fun theContentInsetsAreUiConfigField2() {
        val format =
            format(
                varint(1, 3),
                varint(3, 840),
                varint(5, 240),
                message(
                    11,
                    sides(1, top = 0, bottom = 0, left = 420, right = 420),
                    sides(2, top = 280, bottom = 300, left = 160, right = 160),
                ),
            )
        assertEquals(
            CarInsets(top = 280, bottom = 300, left = 160, right = 160),
            format?.contentInsets,
        )
        assertEquals(1080, format?.visibleWidth)
        assertEquals(1080, format?.visibleHeight)
        assertEquals(true, format?.areContentInsetsUsable)
    }

    @Test
    fun theStableInsetsAreUiConfigField3() {
        val format =
            format(
                varint(1, 1),
                varint(5, 160),
                message(
                    11,
                    sides(2, top = 30, bottom = 0, left = 0, right = 0),
                    sides(3, top = 10, bottom = 0, left = 0, right = 0),
                ),
            )
        assertEquals(CarInsets(top = 30, bottom = 0, left = 0, right = 0), format?.contentInsets)
        assertEquals(CarInsets(top = 10, bottom = 0, left = 0, right = 0), format?.stableInsets)
    }

    @Test
    fun onlyContentInsetsAreTheStableInsetsToo() {
        val format =
            format(
                varint(1, 1),
                varint(5, 160),
                message(11, sides(2, top = 30, bottom = 0, left = 0, right = 0)),
            )
        assertEquals(CarInsets(top = 30, bottom = 0, left = 0, right = 0), format?.stableInsets)
    }

    @Test
    fun onlyStableInsetsAreTheContentInsetsToo() {
        val format =
            format(
                varint(1, 1),
                varint(5, 160),
                message(11, sides(3, top = 10, bottom = 0, left = 0, right = 0)),
            )
        assertEquals(CarInsets(top = 10, bottom = 0, left = 0, right = 0), format?.contentInsets)
    }

    @Test
    fun stableInsetsThatLeaveNoContentAreaAreIgnored() {
        val format =
            format(
                varint(1, 1),
                varint(5, 160),
                message(11, sides(3, top = 0, bottom = 0, left = 400, right = 400)),
            )
        assertEquals(false, format?.areStableInsetsUsable)
        assertEquals(CarInsets.NONE, format?.displayStableInsets)
    }

    @Test
    fun theDisplaysStableInsetsAreScaledLikeItsSize() {
        val format =
            format(
                varint(1, 1),
                varint(5, 160),
                varint(8, 20000),
                message(11, sides(3, top = 30, bottom = 30, left = 40, right = 200)),
            )
        assertEquals(
            CarInsets(top = 30, bottom = 30, left = 80, right = 400),
            format?.displayStableInsets,
        )
    }

    @Test
    fun anEmptyContentInsetsMessageIsNoInsets() {
        assertEquals(
            CarInsets.NONE,
            format(varint(1, 7), varint(5, 240), message(11, message(1), message(2)))
                ?.contentInsets,
        )
    }

    @Test
    fun contentInsetsThatLeaveNoContentAreaAreIgnored() {
        val wide = CarInsets(top = 0, bottom = 0, left = 400, right = 400)
        val tall = CarInsets(top = 300, bottom = 180, left = 0, right = 0)
        val negative = CarInsets(top = -1, bottom = 0, left = 0, right = 0)
        for (insets in listOf(wide, tall, negative)) {
            val format =
                format(
                    varint(1, 1),
                    varint(5, 160),
                    message(
                        11,
                        sides(2, insets.top, insets.bottom, insets.left, insets.right),
                    ),
                )
            assertEquals(insets, format?.contentInsets)
            assertEquals(false, format?.areContentInsetsUsable)
            assertEquals(CarInsets.NONE, format?.displayContentInsets)
        }
    }

    @Test
    fun marginsThatLeaveNoVisibleAreaAreAProtocolError() {
        assertThrows(ProtocolException::class.java) {
            format(varint(1, 1), varint(3, 800), varint(5, 160))
        }
        assertThrows(ProtocolException::class.java) {
            format(varint(1, 1), varint(4, 480), varint(5, 160))
        }
        assertThrows(ProtocolException::class.java) {
            format(
                varint(1, 1),
                varint(5, 160),
                message(11, sides(1, top = 0, bottom = 0, left = 400, right = 400)),
            )
        }
    }

    @Test
    fun negativeMarginsAreAProtocolError() {
        assertThrows(ProtocolException::class.java) {
            format(varint(1, 1), varint(3, -2), varint(5, 160))
        }
        assertThrows(ProtocolException::class.java) {
            format(
                varint(1, 1),
                varint(5, 160),
                message(11, sides(1, top = -1, bottom = 0, left = 0, right = 0)),
            )
        }
    }

    @Test
    fun thePixelAspectRatioIsField8Times10000() {
        assertEquals(
            15000,
            format(varint(1, 2), varint(5, 160), varint(8, 15000))?.pixelAspectRatioE4,
        )
    }

    @Test
    fun aMissingPixelAspectRatioIsSquare() {
        val format = format(varint(1, 2), varint(3, 80), varint(4, 40), varint(5, 160))
        assertEquals(10000, format?.pixelAspectRatioE4)
        assertEquals(1200, format?.displayWidth)
        assertEquals(680, format?.displayHeight)
    }

    @Test
    fun aZeroOrNegativePixelAspectRatioIsAProtocolError() {
        for (value in listOf(0, -10000)) {
            assertThrows(ProtocolException::class.java) {
                format(varint(1, 2), varint(5, 160), varint(8, value))
            }
        }
    }

    @Test
    fun widePixelsWidenTheDisplay() {
        val format =
            format(varint(1, 2), varint(3, 80), varint(4, 40), varint(5, 160), varint(8, 15000))
        assertEquals(1800, format?.displayWidth)
        assertEquals(680, format?.displayHeight)
    }

    @Test
    fun tallPixelsHeightenTheDisplay() {
        val format = format(varint(1, 2), varint(5, 160), varint(8, 7500))
        assertEquals(1280, format?.displayWidth)
        assertEquals(960, format?.displayHeight)
    }

    @Test
    fun theDisplaysContentInsetsAreScaledLikeItsSize() {
        val format =
            format(
                varint(1, 1),
                varint(5, 160),
                varint(8, 20000),
                message(11, sides(2, top = 30, bottom = 30, left = 40, right = 200)),
            )
        assertEquals(
            CarInsets(top = 30, bottom = 30, left = 80, right = 400),
            format?.displayContentInsets,
        )
    }

    @Test
    fun theCutoutsAreUiConfigField7WithTheirBoundsAsField1() {
        val format =
            format(
                varint(1, 1),
                varint(5, 160),
                message(11, message(7, sides(1, top = 0, bottom = 40, left = 300, right = 500))),
            )
        assertEquals(
            listOf(CarRect(left = 300, top = 0, right = 500, bottom = 40)),
            format?.cutouts,
        )
    }

    @Test
    fun aCutoutWithoutBoundsIsSkipped() {
        assertEquals(
            emptyList<CarRect>(),
            format(varint(1, 1), varint(5, 160), message(11, message(7)))?.cutouts,
        )
    }

    @Test
    fun cutoutsAreTakenOutOfTheVisiblePixels() {
        val format =
            VideoFormat(
                1280,
                720,
                30,
                160,
                margins = CarInsets(top = 0, bottom = 0, left = 40, right = 40),
                cutouts = listOf(CarRect(left = 300, top = 0, right = 500, bottom = 40)),
            )
        assertEquals(1200 * 720 - 200 * 40, format.pixelsOutsideCutouts)
    }

    @Test
    fun emptyOrInvertedCutoutsTakeNothingOut() {
        val format =
            VideoFormat(
                800,
                480,
                30,
                160,
                cutouts =
                    listOf(
                        CarRect(left = 300, top = 0, right = 300, bottom = 40),
                        CarRect(left = 500, top = 0, right = 300, bottom = 40),
                        CarRect(left = 300, top = 40, right = 500, bottom = 0),
                    ),
            )
        assertEquals(800 * 480, format.pixelsOutsideCutouts)
    }

    @Test
    fun cutoutsCoveringMoreThanTheVisibleAreaLeaveNoPixels() {
        val format =
            VideoFormat(
                800,
                480,
                30,
                160,
                cutouts = listOf(CarRect(left = 0, top = 0, right = 2000, bottom = 2000)),
            )
        assertEquals(0, format.pixelsOutsideCutouts)
    }

    @Test
    fun aCutoutAtTheLeftEdgeIsAColumnOnTheLeft() {
        val format = VideoFormat(1280, 720, 30, 160, cutouts = listOf(CarRect(0, 100, 300, 200)))
        assertEquals(
            CarInsets(top = 0, bottom = 0, left = 300, right = 0),
            format.displayCutoutInsets,
        )
    }

    @Test
    fun anyOtherCutoutIsAColumnOfItsWidthOnTheRight() {
        val format = VideoFormat(1280, 720, 30, 160, cutouts = listOf(CarRect(500, 300, 700, 400)))
        assertEquals(
            CarInsets(top = 0, bottom = 0, left = 0, right = 200),
            format.displayCutoutInsets,
        )
    }

    @Test
    fun onlyTheFirstCutoutMakesAColumn() {
        val format =
            VideoFormat(
                1280,
                720,
                30,
                160,
                cutouts = listOf(CarRect(1180, 0, 1280, 100), CarRect(0, 0, 400, 100)),
            )
        assertEquals(
            CarInsets(top = 0, bottom = 0, left = 0, right = 100),
            format.displayCutoutInsets,
        )
    }

    @Test
    fun theCutoutColumnScalesLikeTheDisplay() {
        val format =
            VideoFormat(
                1280,
                720,
                30,
                160,
                pixelAspectRatioE4 = 15000,
                cutouts = listOf(CarRect(0, 0, 200, 100)),
            )
        assertEquals(
            CarInsets(top = 0, bottom = 0, left = 300, right = 0),
            format.displayCutoutInsets,
        )
    }

    @Test
    fun aCutoutAsWideAsTheDisplayOrEmptyMakesNoColumn() {
        for (cutout in listOf(CarRect(0, 0, 1280, 100), CarRect(300, 0, 300, 100))) {
            assertEquals(
                CarInsets.NONE,
                VideoFormat(1280, 720, 30, 160, cutouts = listOf(cutout)).displayCutoutInsets,
            )
        }
    }

    @Test
    fun aNewUiConfigReplacesTheInsetsAndCutoutsButKeepsTheMargins() {
        val format =
            VideoFormat(
                1280,
                720,
                30,
                160,
                margins = CarInsets(top = 0, bottom = 0, left = 40, right = 40),
                contentInsets = CarInsets(top = 10, bottom = 0, left = 0, right = 0),
                cutouts = listOf(CarRect(0, 0, 100, 50)),
            )
        val updated =
            format.withUiConfig(
                UiConfig(
                    margins = UiInsets(top = 0, bottom = 0, left = 0, right = 0),
                    content_insets = UiInsets(top = 30, bottom = 0, left = 0, right = 0),
                )
            )
        assertEquals(format.margins, updated.margins)
        assertEquals(CarInsets(top = 30, bottom = 0, left = 0, right = 0), updated.contentInsets)
        assertEquals(CarInsets(top = 30, bottom = 0, left = 0, right = 0), updated.stableInsets)
        assertEquals(emptyList<CarRect>(), updated.cutouts)
    }

    @Test
    fun displayPointsScaleBackToTheVisibleArea() {
        val format = VideoFormat(1280, 720, 30, 160, pixelAspectRatioE4 = 15000)
        assertEquals(640, format.visibleX(960))
        assertEquals(1280, format.visibleX(1920))
        assertEquals(360, format.visibleY(360))
    }

    @Test
    fun theHighestAcceptedResolutionIsPreferred() {
        val configs =
            listOf(
                VideoConfig(resolution = 1),
                VideoConfig(resolution = 3),
                VideoConfig(resolution = 2),
            )
        assertEquals(1, preferredConfigIndex(configs, listOf(0, 2, 1), VideoCodec.H264, ::any))
        assertEquals(2, preferredConfigIndex(configs, listOf(0, 2), VideoCodec.H264, ::any))
    }

    @Test
    fun anUnknownResolutionCountsAs800x480() {
        val configs = listOf(VideoConfig(resolution = 10), VideoConfig(resolution = 2))
        assertEquals(1, preferredConfigIndex(configs, listOf(0, 1), VideoCodec.H264, ::any))
    }

    @Test
    fun aHigherFrameRateWinsAtTheSameResolution() {
        val configs =
            listOf(
                VideoConfig(resolution = 3, frame_rate = 2),
                VideoConfig(resolution = 3, frame_rate = 1),
            )
        assertEquals(1, preferredConfigIndex(configs, listOf(0, 1), VideoCodec.H264, ::any))
    }

    @Test
    fun equalConfigsKeepTheCarsOrder() {
        val configs = listOf(VideoConfig(resolution = 3), VideoConfig(resolution = 7))
        assertEquals(0, preferredConfigIndex(configs, listOf(0, 1), VideoCodec.H264, ::any))
        assertEquals(1, preferredConfigIndex(configs, listOf(1, 0), VideoCodec.H264, ::any))
    }

    @Test
    fun theCodecIsField10() {
        assertEquals(
            VideoMode(VideoCodec.H265, 1920, 1080, 60),
            config(varint(1, 3), varint(5, 160), varint(10, 7))?.let(::videoMode),
        )
    }

    @Test
    fun aConfigWithoutACodecIsH264() {
        assertEquals(
            VideoMode(VideoCodec.H264, 1920, 1080, 60),
            config(varint(1, 3), varint(5, 160))?.let(::videoMode),
        )
        assertEquals(
            VideoCodec.H264,
            videoMode(VideoConfig(resolution = 3, video_codec_type = 3))?.codec,
        )
    }

    @Test
    fun aConfigWithAnUnknownCodecIsNeverUsed() {
        val configs =
            listOf(VideoConfig(resolution = 4, video_codec_type = 99), VideoConfig(resolution = 2))
        assertNull(videoMode(configs[0]))
        assertEquals(VideoCodec.H264, preferredVideoCodec(configs, ::any))
        assertEquals(1, preferredConfigIndex(configs, listOf(0, 1), VideoCodec.H264, ::any))
    }

    @Test
    fun theResolutionWinsOverTheCodecAndTheFrameRate() {
        val configs =
            listOf(
                VideoConfig(resolution = 3, frame_rate = 1, video_codec_type = 7),
                VideoConfig(resolution = 4, frame_rate = 2, video_codec_type = 3),
            )
        assertEquals(VideoCodec.H264, preferredVideoCodec(configs, ::any))
    }

    @Test
    fun h265WinsOverH264AndTheFrameRateAtTheSameResolution() {
        val configs =
            listOf(
                VideoConfig(resolution = 3, frame_rate = 1, video_codec_type = 3),
                VideoConfig(resolution = 3, frame_rate = 2, video_codec_type = 7),
            )
        assertEquals(VideoCodec.H265, preferredVideoCodec(configs, ::any))
    }

    @Test
    fun codec5IsVp9() {
        assertEquals(
            VideoCodec.VP9,
            videoMode(VideoConfig(resolution = 3, video_codec_type = 5))?.codec,
        )
    }

    @Test
    fun vp9WinsOverH264AtTheSameResolution() {
        val configs =
            listOf(
                VideoConfig(resolution = 3, frame_rate = 1, video_codec_type = 3),
                VideoConfig(resolution = 3, frame_rate = 2, video_codec_type = 5),
            )
        assertEquals(VideoCodec.VP9, preferredVideoCodec(configs, ::any))
    }

    @Test
    fun h265WinsOverVp9AtTheSameResolution() {
        val configs =
            listOf(
                VideoConfig(resolution = 3, frame_rate = 1, video_codec_type = 5),
                VideoConfig(resolution = 3, frame_rate = 2, video_codec_type = 7),
            )
        assertEquals(VideoCodec.H265, preferredVideoCodec(configs, ::any))
        assertEquals(VideoCodec.H265, preferredVideoCodec(configs.reversed(), ::any))
    }

    @Test
    fun theCodecOfAConfigThatCantBeEncodedIsNotChosen() {
        val configs =
            listOf(
                VideoConfig(resolution = 4, video_codec_type = 7),
                VideoConfig(resolution = 3, video_codec_type = 3),
            )
        assertEquals(
            VideoCodec.H264,
            preferredVideoCodec(configs) { it.codec == VideoCodec.H264 },
        )
    }

    @Test
    fun noEncodableConfigHasNoCodec() {
        assertNull(preferredVideoCodec(listOf(VideoConfig(resolution = 1))) { false })
    }

    @Test
    fun onlyConfigsWithTheChosenCodecAreUsed() {
        val configs =
            listOf(
                VideoConfig(resolution = 4, video_codec_type = 3),
                VideoConfig(resolution = 3, video_codec_type = 7),
                VideoConfig(resolution = 2, video_codec_type = 3),
            )
        assertEquals(1, preferredConfigIndex(configs, listOf(0, 1, 2), VideoCodec.H265, ::any))
        assertEquals(0, preferredConfigIndex(configs, listOf(0, 1, 2), VideoCodec.H264, ::any))
    }

    @Test
    fun noAcceptedConfigWithTheChosenCodecIsNull() {
        val configs =
            listOf(VideoConfig(resolution = 3, video_codec_type = 7), VideoConfig(resolution = 2))
        assertNull(preferredConfigIndex(configs, listOf(1), VideoCodec.H265, ::any))
    }

    @Test
    fun noAcceptedConfigIsAProtocolError() {
        assertThrows(ProtocolException::class.java) {
            preferredConfigIndex(
                listOf(VideoConfig(resolution = 1)),
                emptyList(),
                VideoCodec.H264,
                ::any,
            )
        }
    }

    @Test
    fun anAcceptedIndexWithoutAConfigIsAProtocolError() {
        val configs = listOf(VideoConfig(resolution = 1), VideoConfig(resolution = 3))
        for (indexes in listOf(listOf(2), listOf(1, 2), listOf(-1, 0))) {
            assertThrows(ProtocolException::class.java) {
                preferredConfigIndex(configs, indexes, VideoCodec.H264, ::any)
            }
        }
    }

    @Test
    fun aResolutionThatCantBeEncodedIsSkipped() {
        val configs =
            listOf(
                VideoConfig(resolution = 2),
                VideoConfig(resolution = 5),
                VideoConfig(resolution = 3),
            )
        assertEquals(
            2,
            preferredConfigIndex(configs, listOf(0, 1, 2), VideoCodec.H264) {
                it.width <= 1920 && it.height <= 1080
            },
        )
    }

    @Test
    fun theFrameRateIsCheckedToo() {
        val configs =
            listOf(
                VideoConfig(resolution = 3, frame_rate = 1),
                VideoConfig(resolution = 3, frame_rate = 2),
            )
        assertEquals(
            1,
            preferredConfigIndex(configs, listOf(0, 1), VideoCodec.H264) {
                it.framesPerSecond == 30
            },
        )
    }

    @Test
    fun noEncodableConfigIsNull() {
        assertNull(
            preferredConfigIndex(listOf(VideoConfig(resolution = 1)), listOf(0), VideoCodec.H264) {
                false
            }
        )
    }

    @Test
    fun theDecoderDepthIsField6() {
        assertEquals(2, depth(ProtocolVersion(1, 7), varint(1, 1), varint(6, 2)))
        assertEquals(0, depth(ProtocolVersion(1, 7), varint(1, 1), varint(6, 0)))
    }

    @Test
    fun aCarWithoutADecoderDepthHasADepthOf4() {
        assertEquals(4, depth(ProtocolVersion(1, 7), varint(1, 1)))
    }

    @Test
    fun aNegativeDecoderDepthIsNone() {
        assertEquals(0, depth(ProtocolVersion(1, 7), varint(1, 1), varint(6, -1)))
    }

    @Test
    fun carsFromProtocol6HaveNoDecoderDepth() {
        assertEquals(3, depth(ProtocolVersion(5, 9), varint(1, 1), varint(6, 3)))
        assertEquals(0, depth(ProtocolVersion(6, 0), varint(1, 1), varint(6, 3)))
        assertEquals(0, depth(ProtocolVersion(6, 1), varint(1, 1)))
    }

    private fun any(mode: VideoMode): Boolean = true

    private fun format(vararg fields: ByteArray): VideoFormat? = config(*fields)?.let(::videoFormat)

    private fun depth(car: ProtocolVersion, vararg fields: ByteArray): Int =
        decoderDepth(checkNotNull(config(*fields)), car)

    private fun config(vararg fields: ByteArray): VideoConfig? =
        videoSink(
                parseServiceDiscoveryResponse(
                    message(1, varint(1, 3), message(3, message(4, *fields)))
                )
            )
            ?.configs
            ?.single()

    private fun sides(number: Int, top: Int, bottom: Int, left: Int, right: Int): ByteArray =
        message(number, varint(1, top), varint(2, bottom), varint(3, left), varint(4, right))

    private fun varint(number: Int, value: Int): ByteArray {
        val bytes = mutableListOf((number shl 3).toByte())
        var rest = value.toLong()
        while (rest and 0x7fL.inv() != 0L) {
            bytes += (rest and 0x7f or 0x80).toByte()
            rest = rest ushr 7
        }
        bytes += rest.toByte()
        return bytes.toByteArray()
    }

    private fun message(number: Int, vararg fields: ByteArray): ByteArray {
        val body = fields.fold(ByteArray(0), ByteArray::plus)
        return byteArrayOf((number shl 3 or 2).toByte(), body.size.toByte()) + body
    }
}
