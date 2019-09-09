package com.acquaintsoft.measureappandroidkotlin.arcore.rendering

import android.content.Context
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView

import com.acquaintsoft.measureappandroidkotlin.R
import com.google.ar.core.Frame
import com.google.ar.core.Session

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class BackgroundRenderer {

    private var quadVertices: FloatBuffer? = null
    private var quadTexCoord: FloatBuffer? = null
    private var quadTexCoordTransformed: FloatBuffer? = null

    private var quadProgram: Int = 0

    private var quadPositionParam: Int = 0
    private var quadTexCoordParam: Int = 0

    var textureId = -1
        private set

    /**
     * Allocates and initializes OpenGL resources needed by the background renderer. Must be called on
     * the OpenGL thread, typically in [GLSurfaceView.Renderer.onSurfaceCreated].
     *
     * @param context Needed to access shader source.
     */
    fun createOnGlThread(context: Context) {
        // Generate the background texture.
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]
        val textureTarget = GLES11Ext.GL_TEXTURE_EXTERNAL_OES
        GLES20.glBindTexture(textureTarget, textureId)
        GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)

        val numVertices = 4
        if (numVertices != QUAD_COORDS.size / COORDS_PER_VERTEX) {
            throw RuntimeException("Unexpected number of vertices in BackgroundRenderer.")
        }

        val bbVertices = ByteBuffer.allocateDirect(QUAD_COORDS.size * FLOAT_SIZE)
        bbVertices.order(ByteOrder.nativeOrder())
        quadVertices = bbVertices.asFloatBuffer()
        quadVertices!!.put(QUAD_COORDS)
        quadVertices!!.position(0)

        val bbTexCoords = ByteBuffer.allocateDirect(numVertices * TEXCOORDS_PER_VERTEX * FLOAT_SIZE)
        bbTexCoords.order(ByteOrder.nativeOrder())
        quadTexCoord = bbTexCoords.asFloatBuffer()
        quadTexCoord!!.put(QUAD_TEXCOORDS)
        quadTexCoord!!.position(0)

        val bbTexCoordsTransformed = ByteBuffer.allocateDirect(numVertices * TEXCOORDS_PER_VERTEX * FLOAT_SIZE)
        bbTexCoordsTransformed.order(ByteOrder.nativeOrder())
        quadTexCoordTransformed = bbTexCoordsTransformed.asFloatBuffer()

        val vertexShader = ShaderUtil.loadGLShader(TAG, context, GLES20.GL_VERTEX_SHADER, R.raw.screenquad_vertex)
        val fragmentShader = ShaderUtil.loadGLShader(
            TAG, context, GLES20.GL_FRAGMENT_SHADER, R.raw.screenquad_fragment_oes
        )

        quadProgram = GLES20.glCreateProgram()
        GLES20.glAttachShader(quadProgram, vertexShader)
        GLES20.glAttachShader(quadProgram, fragmentShader)
        GLES20.glLinkProgram(quadProgram)
        GLES20.glUseProgram(quadProgram)

        ShaderUtil.checkGLError(TAG, "Program creation")

        quadPositionParam = GLES20.glGetAttribLocation(quadProgram, "a_Position")
        quadTexCoordParam = GLES20.glGetAttribLocation(quadProgram, "a_TexCoord")

        ShaderUtil.checkGLError(TAG, "Program parameters")
    }

    /**
     * Draws the AR background image. The image will be drawn such that virtual content rendered with
     * the matrices provided by [com.google.ar.core.Camera.getViewMatrix] and
     * [com.google.ar.core.Camera.getProjectionMatrix] will
     * accurately follow static physical objects. This must be called **before** drawing virtual
     * content.
     *
     * @param frame The last `Frame` returned by [Session.update].
     */
    fun draw(frame: Frame) {
        // If display rotation changed (also includes view size change), we need to re-query the uv
        // coordinates for the screen rect, as they may have changed as well.
        if (frame.hasDisplayGeometryChanged()) {
            frame.transformDisplayUvCoords(quadTexCoord!!, quadTexCoordTransformed!!)
        }

        // No need to test or write depth, the screen quad has arbitrary depth, and is expected
        // to be drawn first.
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthMask(false)

        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)

        GLES20.glUseProgram(quadProgram)

        // Set the vertex positions.
        GLES20.glVertexAttribPointer(
            quadPositionParam, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, 0, quadVertices
        )

        // Set the texture coordinates.
        GLES20.glVertexAttribPointer(
            quadTexCoordParam,
            TEXCOORDS_PER_VERTEX,
            GLES20.GL_FLOAT,
            false,
            0,
            quadTexCoordTransformed
        )

        // Enable vertex arrays
        GLES20.glEnableVertexAttribArray(quadPositionParam)
        GLES20.glEnableVertexAttribArray(quadTexCoordParam)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        // Disable vertex arrays
        GLES20.glDisableVertexAttribArray(quadPositionParam)
        GLES20.glDisableVertexAttribArray(quadTexCoordParam)

        // Restore the depth state for further drawing.
        GLES20.glDepthMask(true)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)

        ShaderUtil.checkGLError(TAG, "Draw")
    }

    companion object {
        private val TAG = BackgroundRenderer::class.java.simpleName

        private val COORDS_PER_VERTEX = 3
        private val TEXCOORDS_PER_VERTEX = 2
        private val FLOAT_SIZE = 4

        private val QUAD_COORDS =
            floatArrayOf(-1.0f, -1.0f, 0.0f, -1.0f, +1.0f, 0.0f, +1.0f, -1.0f, 0.0f, +1.0f, +1.0f, 0.0f)

        private val QUAD_TEXCOORDS = floatArrayOf(0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 0.0f)
    }
}