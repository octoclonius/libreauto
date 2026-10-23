package io.github.octoclonius.libreauto.session.video

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Choreographer
import android.view.Surface
import io.github.octoclonius.libreauto.protocol.media.VideoFormat
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

internal class VideoCompositor(
    private val format: VideoFormat,
    private val areas: List<Area>,
    private val decoderDepth: Int,
    private val onFailed: () -> Unit,
) : Closeable {

    companion object {
        private const val TAG = "VideoCompositor"

        private const val VERTEX_SHADER =
            """
            attribute vec4 aPosition;
            attribute vec4 aTextureCoordinate;
            uniform mat4 uTextureMatrix;
            varying vec2 vTextureCoordinate;
            void main() {
                gl_Position = aPosition;
                vTextureCoordinate = (uTextureMatrix * aTextureCoordinate).xy;
            }
            """

        private const val FRAGMENT_SHADER =
            """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTextureCoordinate;
            uniform samplerExternalOES sTexture;
            void main() {
                gl_FragColor = texture2D(sTexture, vTextureCoordinate);
            }
            """

        private val QUAD = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
        private val TEXTURE_QUAD = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f)

        private const val FRAME_TIME_DEVIATION = 0.2
    }

    private class Layer(
        val area: Area,
        val textureId: Int,
        val texture: SurfaceTexture,
        val surface: Surface,
    ) {
        val matrix = FloatArray(16)
    }

    private val thread = HandlerThread("video-compositor").apply { start() }
    private val handler = Handler(thread.looper)
    private val quad = floatBuffer(QUAD)
    private val textureQuad = floatBuffer(TEXTURE_QUAD)
    private val intervalNanos = TimeUnit.SECONDS.toNanos(1) / format.framesPerSecond
    private val deviationNanos = (intervalNanos * FRAME_TIME_DEVIATION).toLong()
    private val onVsync = Choreographer.FrameCallback(::onVsync)
    private lateinit var choreographer: Choreographer

    private var eglDisplay = EGL14.EGL_NO_DISPLAY
    private var eglConfig: EGLConfig? = null
    private var eglContext = EGL14.EGL_NO_CONTEXT
    private var idleSurface = EGL14.EGL_NO_SURFACE
    private var eglSurface = EGL14.EGL_NO_SURFACE
    private var program = 0
    private var layers: List<Layer> = emptyList()
    private var dueNanos = 0L
    private var hasNewPicture = false
    private var repeats = 0
    private var isScheduled = false
    private var isReleased = false
    private var isFailed = false

    val inputs: List<Surface>

    init {
        val setUp = FutureTask(::setUp)
        handler.post(setUp)
        inputs =
            try {
                setUp.get()
            } catch (e: ExecutionException) {
                close()
                throw e.cause ?: e
            }
    }

    fun attach(output: Surface) {
        onThread {
            check(!isReleased && !isFailed) { "The compositor stopped" }
            detachOutput()
            eglSurface =
                EGL14.eglCreateWindowSurface(
                    eglDisplay,
                    eglConfig,
                    output,
                    intArrayOf(EGL14.EGL_NONE),
                    0,
                )
            checkEgl(eglSurface != EGL14.EGL_NO_SURFACE, "eglCreateWindowSurface")
            checkEgl(
                EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext),
                "eglMakeCurrent",
            )
            dueNanos = 0L
            hasNewPicture = true
            schedule()
        }
    }

    fun detach() {
        onThread(::detachOutput)
    }

    override fun close() {
        handler.post(::release)
        thread.quitSafely()
        thread.join()
    }

    private fun <T> onThread(block: () -> T): T {
        val task = FutureTask(block)
        check(handler.post(task)) { "The compositor is closed" }
        return try {
            task.get()
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        }
    }

    private fun detachOutput() {
        if (eglSurface == EGL14.EGL_NO_SURFACE) return
        choreographer.removeFrameCallback(onVsync)
        isScheduled = false
        hasNewPicture = false
        repeats = 0
        EGL14.eglMakeCurrent(eglDisplay, idleSurface, idleSurface, eglContext)
        EGL14.eglDestroySurface(eglDisplay, eglSurface)
        eglSurface = EGL14.EGL_NO_SURFACE
    }

    private fun setUp(): List<Surface> {
        choreographer = Choreographer.getInstance()
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(eglDisplay != EGL14.EGL_NO_DISPLAY) { "No EGL display" }
        val version = IntArray(2)
        checkEgl(EGL14.eglInitialize(eglDisplay, version, 0, version, 1), "eglInitialize")
        val config = chooseConfig(eglDisplay)
        eglConfig = config
        eglContext =
            EGL14.eglCreateContext(
                eglDisplay,
                config,
                EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE),
                0,
            )
        checkEgl(eglContext != EGL14.EGL_NO_CONTEXT, "eglCreateContext")
        idleSurface =
            EGL14.eglCreatePbufferSurface(
                eglDisplay,
                config,
                intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE),
                0,
            )
        checkEgl(idleSurface != EGL14.EGL_NO_SURFACE, "eglCreatePbufferSurface")
        checkEgl(
            EGL14.eglMakeCurrent(eglDisplay, idleSurface, idleSurface, eglContext),
            "eglMakeCurrent",
        )
        program = linkProgram()
        layers = areas.map(::createLayer)
        return layers.map { it.surface }
    }

    private fun createLayer(area: Area): Layer {
        val textureId = IntArray(1).also { GLES20.glGenTextures(1, it, 0) }[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_LINEAR,
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MAG_FILTER,
            GLES20.GL_LINEAR,
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_S,
            GLES20.GL_CLAMP_TO_EDGE,
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_T,
            GLES20.GL_CLAMP_TO_EDGE,
        )
        val texture =
            SurfaceTexture(textureId).apply { setDefaultBufferSize(area.width, area.height) }
        val layer = Layer(area, textureId, texture, Surface(texture))
        texture.setOnFrameAvailableListener({ onFrameAvailable(layer) }, handler)
        return layer
    }

    private fun chooseConfig(display: EGLDisplay): EGLConfig {
        val attributes =
            intArrayOf(
                EGL14.EGL_RED_SIZE,
                8,
                EGL14.EGL_GREEN_SIZE,
                8,
                EGL14.EGL_BLUE_SIZE,
                8,
                EGL14.EGL_RENDERABLE_TYPE,
                EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE,
                EGL14.EGL_WINDOW_BIT or EGL14.EGL_PBUFFER_BIT,
                EGLExt.EGL_RECORDABLE_ANDROID,
                1,
                EGL14.EGL_NONE,
            )
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        checkEgl(
            EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0) && count[0] > 0,
            "eglChooseConfig",
        )
        return checkNotNull(configs[0])
    }

    private fun linkProgram(): Int {
        val linked = GLES20.glCreateProgram()
        GLES20.glAttachShader(linked, compileShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER))
        GLES20.glAttachShader(linked, compileShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER))
        GLES20.glLinkProgram(linked)
        val status = IntArray(1)
        GLES20.glGetProgramiv(linked, GLES20.GL_LINK_STATUS, status, 0)
        check(status[0] == GLES20.GL_TRUE) {
            "Linking the compositor's program failed: ${GLES20.glGetProgramInfoLog(linked)}"
        }
        return linked
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        check(status[0] == GLES20.GL_TRUE) {
            "Compiling the compositor's shader failed: ${GLES20.glGetShaderInfoLog(shader)}"
        }
        return shader
    }

    private fun onFrameAvailable(layer: Layer) {
        if (isReleased || isFailed) return
        guarded {
            layer.texture.updateTexImage()
            layer.texture.getTransformMatrix(layer.matrix)
            hasNewPicture = true
            if (eglSurface != EGL14.EGL_NO_SURFACE) schedule()
        }
    }

    private fun schedule() {
        if (isScheduled) return
        isScheduled = true
        choreographer.postFrameCallback(onVsync)
    }

    private fun onVsync(vsyncNanos: Long) {
        isScheduled = false
        if (isReleased || isFailed || eglSurface == EGL14.EGL_NO_SURFACE) return
        if (!hasNewPicture && repeats == 0) return
        if (vsyncNanos < dueNanos - deviationNanos) {
            schedule()
            return
        }
        val frameNanos = if (vsyncNanos > dueNanos + deviationNanos) vsyncNanos else dueNanos
        dueNanos = frameNanos + intervalNanos
        repeats = if (hasNewPicture) decoderDepth else repeats - 1
        hasNewPicture = false
        guarded {
            drawFrame(vsyncNanos)
            if (repeats > 0) schedule()
        }
    }

    private fun guarded(block: () -> Unit) {
        try {
            block()
        } catch (e: RuntimeException) {
            Log.w(TAG, "Drawing the car display failed", e)
            isFailed = true
            onFailed()
        }
    }

    private fun drawFrame(presentationNanos: Long) {
        GLES20.glViewport(0, 0, format.frameWidth, format.frameHeight)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(program)
        val position = GLES20.glGetAttribLocation(program, "aPosition")
        val textureCoordinate = GLES20.glGetAttribLocation(program, "aTextureCoordinate")
        GLES20.glEnableVertexAttribArray(position)
        GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 0, quad)
        GLES20.glEnableVertexAttribArray(textureCoordinate)
        GLES20.glVertexAttribPointer(textureCoordinate, 2, GLES20.GL_FLOAT, false, 0, textureQuad)
        val matrix = GLES20.glGetUniformLocation(program, "uTextureMatrix")
        for (layer in layers) {
            setViewport(layer.area)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, layer.textureId)
            GLES20.glUniformMatrix4fv(matrix, 1, false, layer.matrix, 0)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        }
        EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, presentationNanos)
        EGL14.eglSwapBuffers(eglDisplay, eglSurface)
    }

    private fun setViewport(area: Area) {
        val left = format.visibleX(area.left)
        val top = format.visibleY(area.top)
        val right = format.visibleX(area.right)
        val bottom = format.visibleY(area.bottom)
        GLES20.glViewport(
            format.margins.left + left,
            format.margins.bottom + format.visibleHeight - bottom,
            right - left,
            bottom - top,
        )
    }

    private fun release() {
        isReleased = true
        choreographer.removeFrameCallback(onVsync)
        layers.forEach {
            it.surface.release()
            it.texture.release()
        }
        layers = emptyList()
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) return
        EGL14.eglMakeCurrent(
            eglDisplay,
            EGL14.EGL_NO_SURFACE,
            EGL14.EGL_NO_SURFACE,
            EGL14.EGL_NO_CONTEXT,
        )
        if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, eglSurface)
        if (idleSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, idleSurface)
        if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(eglDisplay, eglContext)
        EGL14.eglReleaseThread()
        EGL14.eglTerminate(eglDisplay)
        eglSurface = EGL14.EGL_NO_SURFACE
        idleSurface = EGL14.EGL_NO_SURFACE
        eglContext = EGL14.EGL_NO_CONTEXT
        eglDisplay = EGL14.EGL_NO_DISPLAY
    }

    private fun checkEgl(isSuccess: Boolean, call: String) {
        check(isSuccess) { "$call failed with EGL error 0x%x".format(EGL14.eglGetError()) }
    }

    private fun floatBuffer(values: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(values.size * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(values)
                position(0)
            }
}
