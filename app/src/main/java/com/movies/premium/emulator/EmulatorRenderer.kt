package com.movies.premium.emulator

import android.opengl.EGL14
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.Log
import com.app.mlounge.emulator.LibretroCore
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * OpenGL front-end for the libretro core. Mirrors the original renderer:
 * a simple textured quad, with a hardware-render blit path for PPSSPP.
 */
class EmulatorRenderer(
    private val core: LibretroCore,
    private val inputProvider: () -> Int,
    private val onFrameComplete: () -> Unit,
) : GLSurfaceView.Renderer {

    private companion object {
        const val TAG = "EmulatorRenderer"

        /**
         * `GL_TEXTURE_2D` is 3553 (0x0DE1). The near-identical 0x0CE1 is
         * `GL_TEXTURE`, the OpenGL ES 1.0 enum that ES 2/3 drivers reject with
         * `GL_INVALID_ENUM` — which silently leaves the game texture empty.
         */
        const val GL_TEXTURE_2D = 0x0DE1
        const val GL_UNSIGNED_BYTE = 0x1401
        const val GL_RGBA = 0x1908
        const val GL_RGBA8 = 0x8058
        const val GL_FRAMEBUFFER = 0x8D40
        const val GL_ARRAY_BUFFER = 0x8892
        const val GL_ELEMENT_ARRAY_BUFFER = 0x8893
        const val GL_DEPTH_TEST = 0x0B71
        const val GL_STENCIL_TEST = 0x0B90
        const val GL_CULL_FACE = 0x0B44
        const val GL_BLEND = 0x0BE2
        const val GL_TEXTURE0 = 0x84C0
        const val GL_COLOR_BUFFER_BIT = 0x00004000
        const val GL_TEXTURE_MIN_FILTER = 0x2801
        const val GL_TEXTURE_MAG_FILTER = 0x2800
        const val GL_TEXTURE_WRAP_S = 0x2802
        const val GL_TEXTURE_WRAP_T = 0x2803
        const val GL_NEAREST = 0x2600
        const val GL_CLAMP_TO_EDGE = 0x812F
        const val GL_FLOAT = 0x1406
        const val GL_TRIANGLE_STRIP = 5

        /**
         * Verbose GL diagnostics. The emulator works without them, so they are
         * off in the shipped build; flip to `true` to get the GL error trail
         * and the frame readback in logcat again.
         */
        const val DEBUG_GL = false

        val VERTICES = floatArrayOf(
            -1.0f, -1.0f,
            1.0f, -1.0f,
            -1.0f, 1.0f,
            1.0f, 1.0f,
        )
        val TEX_COORDS = floatArrayOf(
            0.0f, 1.0f,
            1.0f, 1.0f,
            0.0f, 0.0f,
            1.0f, 0.0f,
        )
        val HW_TEX_COORDS = floatArrayOf(
            0.0f, 0.0f,
            1.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 1.0f,
        )

        const val VERTEX_SHADER = """
            attribute vec4 a_Position;
            attribute vec2 a_TexCoord;
            varying vec2 v_TexCoord;
            void main() {
                gl_Position = a_Position;
                v_TexCoord = a_TexCoord;
            }
        """

        const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform sampler2D u_Texture;
            varying vec2 v_TexCoord;
            void main() {
                gl_FragColor = texture2D(u_Texture, v_TexCoord);
            }
        """
    }

    private var programId = 0
    private var textureId = 0
    private var positionHandle = 0
    private var texCoordHandle = 0
    private var textureHandle = 0
    private var hwRenderTextureId = 0

    private val vertexBuffer: FloatBuffer = directFloatBuffer(VERTICES)
    private val texCoordBuffer: FloatBuffer = directFloatBuffer(TEX_COORDS)
    private val hwTexCoordBuffer: FloatBuffer = directFloatBuffer(HW_TEX_COORDS)

    /**
     * The frame the core hands us, in the exact byte order the original used:
     * one packed 32-bit pixel per texel. The buffer has to be *direct* — the
     * GLES binding uploads from the buffer's base address, and a heap buffer
     * is not accepted by the driver.
     */
    private var texturePixels: ByteBuffer =
        ByteBuffer.allocateDirect(LibretroBridge.SCREEN_WIDTH * LibretroBridge.SCREEN_HEIGHT * 4)
            .order(ByteOrder.nativeOrder())

    private var textureWidth = LibretroBridge.SCREEN_WIDTH
    private var textureHeight = LibretroBridge.SCREEN_HEIGHT
    private var screenWidth = 0
    private var screenHeight = 0
    private var frameCount = 0

    /**
     * The sized internal format of the game texture. `GL_RGBA` (unsized) is
     * legal in ES 2 but was dropped in ES 3, where the driver answers it with
     * `GL_INVALID_ENUM`; the sized `GL_RGBA8` is valid in both.
     */
    private var internalFormat = GL_RGBA

    /** Set when `glTexSubImage2D` is rejected and the texture is re-created. */
    private var useTexImage = false

    var aspectRatioMode: String = "fit"

    private fun directFloatBuffer(values: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(values.size * 4).order(ByteOrder.nativeOrder())
            .asFloatBuffer().apply {
                put(values)
                position(0)
            }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] != 0) return shader
        Log.e(TAG, "Compile error: ${GLES20.glGetShaderInfoLog(shader)}")
        GLES20.glDeleteShader(shader)
        return 0
    }

    private fun createProgram(vertSrc: String, fragSrc: String): Int {
        val vert = compileShader(GLES20.GL_VERTEX_SHADER, vertSrc)
        if (vert == 0) return 0
        val frag = compileShader(GLES20.GL_FRAGMENT_SHADER, fragSrc)
        if (frag == 0) {
            GLES20.glDeleteShader(vert)
            return 0
        }
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vert)
        GLES20.glAttachShader(program, frag)
        GLES20.glLinkProgram(program)
        val status = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
        if (status[0] == 1) {
            GLES20.glDeleteShader(vert)
            GLES20.glDeleteShader(frag)
            return program
        }
        Log.e(TAG, "Link error: ${GLES20.glGetProgramInfoLog(program)}")
        GLES20.glDeleteProgram(program)
        return 0
    }

    private fun prepareFrontendBlitState() {
        GLES20.glBindFramebuffer(GL_FRAMEBUFFER, 0)
        GLES20.glBindBuffer(GL_ARRAY_BUFFER, 0)
        GLES20.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0)
        GLES20.glDisable(GL_DEPTH_TEST)
        GLES20.glDisable(GL_STENCIL_TEST)
        GLES20.glDisable(GL_CULL_FACE)
        GLES20.glDisable(GL_BLEND)
        GLES20.glColorMask(true, true, true, true)
        updateViewport()
    }

    private fun updateViewport() {
        if (screenWidth == 0 || screenHeight == 0) return
        when (aspectRatioMode) {
            "stretch" -> {
                GLES20.glViewport(0, 0, screenWidth, screenHeight)
                return
            }

            "4:3" -> letterbox(4.0f / 3.0f)
            "16:9" -> letterbox(16.0f / 9.0f)
            else -> {
                val aspect = textureWidth.toFloat() / textureHeight.toFloat()
                letterbox(aspect)
            }
        }
    }

    private fun letterbox(aspect: Float) {
        var w = screenWidth
        var h = screenHeight
        var x = 0
        var y = 0
        if (w.toFloat() / h > aspect) {
            val scaled = (h * aspect).toInt()
            x = (w - scaled) / 2
            w = scaled
        } else {
            val scaled = (w / aspect).toInt()
            y = (h - scaled) / 2
            h = scaled
        }
        GLES20.glViewport(x, y, w, h)
    }

    /** Logs the pending GL error. Only active while `DEBUG_GL` is on. */
    private fun chk(label: String) {
        if (!DEBUG_GL) return
        val e = GLES20.glGetError()
        if (e != GLES20.GL_NO_ERROR && frameCount <= 5) {
            Log.e(TAG, "GL error 0x${Integer.toHexString(e)} after $label")
        }
    }

    /**
     * Allocates the game texture.
     *
     * The internal format has to match the context: ES 2 only knows the
     * unsized `GL_RGBA`, ES 3 only the sized `GL_RGBA8`. Whichever the driver
     * rejects is detected here and the other one is used instead.
     */
    private fun createTextureStorage(width: Int, height: Int) {
        GLES20.glBindTexture(GL_TEXTURE_2D, textureId)
        GLES20.glTexImage2D(
            GL_TEXTURE_2D, 0, internalFormat, width, height, 0,
            GL_RGBA, GL_UNSIGNED_BYTE, null,
        )
        var err = GLES20.glGetError()
        if (err != GLES20.GL_NO_ERROR) {
            val alternative = if (internalFormat == GL_RGBA8) GL_RGBA else GL_RGBA8
            Log.w(
                TAG,
                "internal format 0x${Integer.toHexString(internalFormat)} rejected " +
                    "(0x${Integer.toHexString(err)}), using 0x${Integer.toHexString(alternative)}",
            )
            internalFormat = alternative
            GLES20.glTexImage2D(
                GL_TEXTURE_2D, 0, internalFormat, width, height, 0,
                GL_RGBA, GL_UNSIGNED_BYTE, null,
            )
            err = GLES20.glGetError()
            if (err != GLES20.GL_NO_ERROR) {
                Log.e(TAG, "glTexImage2D failed: 0x${Integer.toHexString(err)}")
            }
        }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // The GL version string is not trustworthy — some drivers report their
        // maximum version even for an ES 2 context — so the EGL attribute
        // decides which internal format the texture is created with.
        val clientVersion = IntArray(1)
        try {
            EGL14.eglQueryContext(
                EGL14.eglGetCurrentDisplay(),
                EGL14.eglGetCurrentContext(),
                EGL14.EGL_CONTEXT_CLIENT_VERSION,
                clientVersion,
                0,
            )
        } catch (e: Exception) {
            Log.e(TAG, "eglQueryContext failed: ${e.message}")
        }
        internalFormat = if (clientVersion[0] >= 3) GL_RGBA8 else GL_RGBA
        if (DEBUG_GL) {
            Log.i(
                TAG,
                "GL version=${GLES20.glGetString(GLES20.GL_VERSION)} " +
                    "renderer=${GLES20.glGetString(GLES20.GL_RENDERER)} " +
                    "EGL client version=${clientVersion[0]} ctx=${ctxTag()}",
            )
        }

        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        programId = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        if (programId == 0) {
            Log.e(TAG, "Failed to create shader program")
            return
        }
        positionHandle = GLES20.glGetAttribLocation(programId, "a_Position")
        texCoordHandle = GLES20.glGetAttribLocation(programId, "a_TexCoord")
        textureHandle = GLES20.glGetUniformLocation(programId, "u_Texture")

        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        textureId = ids[0]
        GLES20.glBindTexture(GL_TEXTURE_2D, textureId)
        GLES20.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST)
        GLES20.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST)
        GLES20.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
        createTextureStorage(textureWidth, textureHeight)

        core.onGlContextReady()
        hwRenderTextureId = core.getHwTextureId()
        if (hwRenderTextureId != 0) {
            Log.i(TAG, "HW render active: PPSSPP texture id=$hwRenderTextureId")
        }
        Log.i(
            TAG,
            "surface created: program=$programId texture=$textureId pos=$positionHandle " +
                "tex=$texCoordHandle uni=$textureHandle internalFormat=0x" +
                Integer.toHexString(internalFormat),
        )
    }

    /** Identifies the EGL context that is current on the GL thread. */
    private fun ctxTag(): String =
        "${System.identityHashCode(EGL14.eglGetCurrentContext())}/" +
            System.identityHashCode(EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW))

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        screenWidth = width
        screenHeight = height
        updateViewport()
    }

    override fun onDrawFrame(gl: GL10?) {
        core.setInput(inputProvider())
        core.runFrame()
        onFrameComplete()
        frameCount++

        if (core.isHwRender() && core.isHwFrameReady() && hwRenderTextureId != 0) {
            prepareFrontendBlitState()
            GLES20.glClear(GL_COLOR_BUFFER_BIT)
            GLES20.glUseProgram(programId)
            GLES20.glEnableVertexAttribArray(positionHandle)
            GLES20.glVertexAttribPointer(positionHandle, 2, GL_FLOAT, false, 0, vertexBuffer)
            GLES20.glEnableVertexAttribArray(texCoordHandle)
            GLES20.glVertexAttribPointer(texCoordHandle, 2, GL_FLOAT, false, 0, hwTexCoordBuffer)
            GLES20.glActiveTexture(GL_TEXTURE0)
            GLES20.glBindTexture(GL_TEXTURE_2D, hwRenderTextureId)
            GLES20.glUniform1i(textureHandle, 0)
            GLES20.glDrawArrays(GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glDisableVertexAttribArray(positionHandle)
            GLES20.glDisableVertexAttribArray(texCoordHandle)
            return
        }

        prepareFrontendBlitState()
        GLES20.glClear(GL_COLOR_BUFFER_BIT)
        if (!core.getGameLoaded()) return

        val videoBuffer = core.getVideoBuffer()
        val videoWidth = core.getVideoWidth()
        val videoHeight = core.getVideoHeight()
        if (videoWidth != textureWidth || videoHeight != textureHeight) {
            textureWidth = videoWidth
            textureHeight = videoHeight
            texturePixels = ByteBuffer
                .allocateDirect(textureWidth * textureHeight * 4)
                .order(ByteOrder.nativeOrder())
            GLES20.glBindTexture(GL_TEXTURE_2D, textureId)
            createTextureStorage(textureWidth, textureHeight)
            updateViewport()
        }

        // The core hands the frame over as packed 32-bit pixels; copy them into
        // the direct upload buffer in the byte order the driver expects.
        val count = minOf(videoBuffer.size, textureWidth * textureHeight)
        val pixels = texturePixels.asIntBuffer()
        pixels.clear()
        pixels.put(videoBuffer, 0, count)
        pixels.position(0)

        GLES20.glBindTexture(GL_TEXTURE_2D, textureId)
        chk("bindTexture")
        if (useTexImage) {
            GLES20.glTexImage2D(
                GL_TEXTURE_2D, 0, internalFormat, textureWidth, textureHeight, 0,
                GL_RGBA, GL_UNSIGNED_BYTE, texturePixels,
            )
            chk("texImage2D upload")
        } else {
            GLES20.glTexSubImage2D(
                GL_TEXTURE_2D, 0, 0, 0, textureWidth, textureHeight,
                GL_RGBA, GL_UNSIGNED_BYTE, texturePixels,
            )
            if (GLES20.glGetError() != GLES20.GL_NO_ERROR) {
                // The driver rejected the sub-image update — re-create the
                // whole texture from the frame instead.
                Log.w(TAG, "glTexSubImage2D rejected, falling back to glTexImage2D")
                useTexImage = true
                GLES20.glTexImage2D(
                    GL_TEXTURE_2D, 0, internalFormat, textureWidth, textureHeight, 0,
                    GL_RGBA, GL_UNSIGNED_BYTE, texturePixels,
                )
                chk("texImage2D upload")
            }
        }
        GLES20.glUseProgram(programId)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 2, GL_FLOAT, false, 0, vertexBuffer)
        GLES20.glEnableVertexAttribArray(texCoordHandle)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GL_FLOAT, false, 0, texCoordBuffer)
        chk("attribs")
        GLES20.glActiveTexture(GL_TEXTURE0)
        GLES20.glBindTexture(GL_TEXTURE_2D, textureId)
        GLES20.glUniform1i(textureHandle, 0)
        chk("uniform")
        GLES20.glDrawArrays(GL_TRIANGLE_STRIP, 0, 4)
        chk("drawArrays")
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)

        if (DEBUG_GL && frameCount == 3) {
            val vp = IntArray(4)
            GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, vp, 0)
            val px = java.nio.ByteBuffer.allocateDirect(4)
            GLES20.glReadPixels(
                vp[0] + vp[2] / 2, vp[1] + vp[3] / 2, 1, 1,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, px,
            )
            px.position(0)
            val b = ByteArray(4)
            px.get(b)
            val center = (videoBuffer.size / 2).coerceAtMost(videoBuffer.size - 1)
            Log.i(
                TAG,
                "readback vp=${vp.joinToString(",")} px=" +
                    b.joinToString(",") { String.format("%02X", it) } +
                    " tex=${textureWidth}x$textureHeight buf=${videoBuffer.size}" +
                    " centerPixel=${String.format("%08X", videoBuffer[center])}" +
                    " isProgram=${GLES20.glIsProgram(programId)} isTexture=${GLES20.glIsTexture(textureId)}",
            )
        }
    }

    fun onDestroy() {
        if (programId != 0) {
            GLES20.glDeleteProgram(programId)
            programId = 0
        }
        if (textureId != 0) {
            GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
            textureId = 0
        }
    }
}
