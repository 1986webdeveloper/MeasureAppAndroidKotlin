package com.acquaintsoft.measureappandroidkotlin.renderer

import android.opengl.GLES20
import android.opengl.Matrix
import com.acquaintsoft.measureappandroidkotlin.arcore.rendering.ShaderUtil

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer


class RectanglePolygonRenderer {

    private val vertexBuffer: FloatBuffer
    private val drawListBuffer: ShortBuffer
    private val drawOrder = shortArrayOf(
        0,
        1,
        2,
        0,
        2,
        3,
        3,
        2,
        6,
        3,
        6,
        7,
        4,
        5,
        6,
        4,
        6,
        7,
        0,
        1,
        5,
        0,
        5,
        4,
        4,
        0,
        3,
        4,
        3,
        7,
        5,
        1,
        2,
        5,
        2,
        6
    ) // order to draw vertex

    internal var color = floatArrayOf(0.63671875f, 0.76953125f, 0.22265625f, 1.0f)


    private val mProgram: Int

    private val vertexShaderCode = "uniform mat4 uMVPMatrix;" +
            "attribute vec4 vPosition;" +
            "void main() {" +
            "  gl_Position = uMVPMatrix * vPosition;" +
            "}"

    // Use to access and set the view transformation
    private var mMVPMatrixHandle: Int = 0

    private val fragmentShaderCode = "precision mediump float;" +
            "uniform vec4 vColor;" +
            "void main() {" +
            "  gl_FragColor = vColor;" +
            "}"

    private var mPositionHandle: Int = 0
    private var mColorHandle: Int = 0

    private val vertexCount = coords.size / COORDS_PER_VERTEX
    private val vertexStride = COORDS_PER_VERTEX * 4

    // Temporary matrices allocated here to reduce number of allocations for each frame.
    private val mModelMatrix = FloatArray(16)
    private val mModelViewMatrix = FloatArray(16)
    private val mModelViewProjectionMatrix = FloatArray(16)
    internal val TAG = "RectanglePolygon"

    fun setVerts(
        v0: Float, v1: Float, v2: Float,
        v3: Float, v4: Float, v5: Float,
        v6: Float, v7: Float, v8: Float,
        v9: Float, v10: Float, v11: Float,

        v12: Float, v13: Float, v14: Float,
        v15: Float, v16: Float, v17: Float,
        v18: Float, v19: Float, v20: Float,
        v21: Float, v22: Float, v23: Float
    ) {
        coords[0] = v0
        coords[1] = v1
        coords[2] = v2

        coords[3] = v3
        coords[4] = v4
        coords[5] = v5

        coords[6] = v6
        coords[7] = v7
        coords[8] = v8

        coords[9] = v9
        coords[10] = v10
        coords[11] = v11

        coords[12] = v12
        coords[13] = v13
        coords[14] = v14

        coords[15] = v15
        coords[16] = v16
        coords[17] = v17

        coords[18] = v18
        coords[19] = v19
        coords[20] = v20

        coords[21] = v21
        coords[22] = v22
        coords[23] = v23

        vertexBuffer.put(coords)
        // set the buffer to read the first coordinate
        vertexBuffer.position(0)
    }

    fun setColor(red: Float, green: Float, blue: Float, alpha: Float) {
        color[0] = red
        color[1] = green
        color[2] = blue
        color[3] = alpha
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)
        return shader
    }

    init {
        // initialize vertex byte buffer for shape coordinates
        val bb = ByteBuffer.allocateDirect(coords.size * 4)
        bb.order(ByteOrder.nativeOrder())
        vertexBuffer = bb.asFloatBuffer()
        vertexBuffer.put(coords)
        vertexBuffer.position(0)

        // initialize byte buffer for the draw list
        val dlb = ByteBuffer.allocateDirect(drawOrder.size * 2)   // 2 bytes per short
        dlb.order(ByteOrder.nativeOrder())
        drawListBuffer = dlb.asShortBuffer()
        drawListBuffer.put(drawOrder)
        drawListBuffer.position(0)

        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

        // create empty OpenGL ES Program
        mProgram = GLES20.glCreateProgram()

        // add the shader to program
        GLES20.glAttachShader(mProgram, vertexShader)
        GLES20.glAttachShader(mProgram, fragmentShader)

        // create OpenGL ES program executables
        GLES20.glLinkProgram(mProgram)
        Matrix.setIdentityM(mModelMatrix, 0)
    }

    fun draw(cameraView: FloatArray, cameraPerspective: FloatArray) {
        ShaderUtil.checkGLError(TAG, "Before draw")

        // Build the ModelView and ModelViewProjection matrices
        // for calculating object position and light.
        Matrix.multiplyMM(mModelViewMatrix, 0, cameraView, 0, mModelMatrix, 0)
        Matrix.multiplyMM(mModelViewProjectionMatrix, 0, cameraPerspective, 0, mModelViewMatrix, 0)

        // add program to OpenGL ES environment
        GLES20.glUseProgram(mProgram)

        // get handle to vertex shader's vPosition member
        mPositionHandle = GLES20.glGetAttribLocation(mProgram, "vPosition")

        // enable a handle to the triangle vertices
        GLES20.glEnableVertexAttribArray(mPositionHandle)

        // prepare the triangle coordinate data
        GLES20.glVertexAttribPointer(
            mPositionHandle, COORDS_PER_VERTEX,
            GLES20.GL_FLOAT, false,
            vertexStride, vertexBuffer
        )

        // get handle to fragment shader's vColor member
        mColorHandle = GLES20.glGetUniformLocation(mProgram, "vColor")

        // set color for drawing the triangle
        GLES20.glUniform4fv(mColorHandle, 1, color, 0)

        // get handle to shape's transformation matrix
        mMVPMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix")

        // Pass the projection and view transformation to the shader
        GLES20.glUniformMatrix4fv(mMVPMatrixHandle, 1, false, mModelViewProjectionMatrix, 0)

        GLES20.glDrawElements(
            GLES20.GL_TRIANGLES, drawOrder.size,
            GLES20.GL_UNSIGNED_SHORT, drawListBuffer
        )

        // Disable vertex array
        GLES20.glDisableVertexAttribArray(mPositionHandle)
    }

    companion object {

        // number of coordinates pervertex in this array
        internal val COORDS_PER_VERTEX = 3
        internal var coords = floatArrayOf(
            -0.6f, 0.5f, 0.2f, // top left
            -0.4f, -0.5f, 0.2f, // bottom left
            0.5f, -0.5f, 0.2f, // bottom right
            0.5f, 0.5f, 0.2f, // top right

            -0.5f, 0.6f, 0.0f, // top left
            -0.5f, -0.8f, 0.0f, // bottom left
            0.5f, -0.5f, 0.0f, // bottom right
            0.5f, 0.5f, 0.0f   // top right
        )
    }
}