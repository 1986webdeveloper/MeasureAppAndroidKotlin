package com.acquaintsoft.measureappandroidkotlin.renderer

import android.opengl.GLES20
import android.opengl.Matrix
import com.acquaintsoft.measureappandroidkotlin.arcore.rendering.ShaderUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer


class LineRenderer {

    private val mProgram: Int

    private val vertexShaderCode = // This matrix member variable provides a hook to manipulate
        // the coordinates of the objects that use this vertex shader
        "uniform mat4 uMVPMatrix;" +
                "attribute vec4 vPosition;" +
                "void main() {" +
                // the matrix must be included as a modifier of gl_Position
                // Note that the uMVPMatrix factor *must be first* in order
                // for the matrix multiplication product to be correct.
                "  gl_Position = uMVPMatrix * vPosition;" +
                "}"

    // Use to access and set the view transformation
    private var mMVPMatrixHandle: Int = 0

    private val fragmentShaderCode = "precision mediump float;" +
            "uniform vec4 vColor;" +
            "void main() {" +
            "  gl_FragColor = vColor;" +
            "}"


    private val vertexBuffer: FloatBuffer

    internal var color = floatArrayOf(0.63671875f, 0.76953125f, 0.22265625f, 1.0f)

    private var mPositionHandle: Int = 0
    private var mColorHandle: Int = 0

    private val vertexCount = coordinates.size / COORDS_PER_VERTEX
    private val vertexStride = COORDS_PER_VERTEX * 4

    // Temporary matrices allocated here to reduce number of allocations for each frame.
    private val mModelMatrix = FloatArray(16)
    private val mModelViewMatrix = FloatArray(16)
    private val mModelViewProjectionMatrix = FloatArray(16)
    internal val TAG = "Line"

    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)
        return shader
    }

    init {
        // initialize vertex byte buffer for shape coordinates
        // number ofr coordinate values * 4 bytes per float
        val bb = ByteBuffer.allocateDirect(coordinates.size * 4)
        bb.order(ByteOrder.nativeOrder())  // use the device hardware's native byte order
        vertexBuffer = bb.asFloatBuffer()  // create a floating point buffer from the ByteBuffer
        vertexBuffer.put(coordinates)  // add the coordinate to the FloatBuffer
        vertexBuffer.position(0)   // set the buffer to read the first coordinate

        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

        mProgram = GLES20.glCreateProgram()    // create empty OpenGL ES Program
        GLES20.glAttachShader(mProgram, vertexShader)  // add the shader to program
        GLES20.glAttachShader(mProgram, fragmentShader)
        GLES20.glLinkProgram(mProgram) // create OpenGL ES program executables
        Matrix.setIdentityM(mModelMatrix, 0)
    }

    fun setVerts(v0: Float, v1: Float, v2: Float, v3: Float, v4: Float, v5: Float) {
        coordinates[0] = v0
        coordinates[1] = v1
        coordinates[2] = v2
        coordinates[3] = v3
        coordinates[4] = v4
        coordinates[5] = v5

        vertexBuffer.put(coordinates)
        // set the buffer to read the first coordinate
        vertexBuffer.position(0)
    }

    fun setColor(red: Float, green: Float, blue: Float, alpha: Float) {
        color[0] = red
        color[1] = green
        color[2] = blue
        color[3] = alpha
    }

    fun draw(cameraView: FloatArray, cameraPerspective: FloatArray) {
        ShaderUtil.checkGLError(TAG, "Before draw")

        // Build the ModelView and ModelViewProjection matrices
        // for calculating object position and light.
        Matrix.multiplyMM(mModelViewMatrix, 0, cameraView, 0, mModelMatrix, 0)
        Matrix.multiplyMM(mModelViewProjectionMatrix, 0, cameraPerspective, 0, mModelViewMatrix, 0)

        // add program to OpenGL ES environment
        GLES20.glUseProgram(mProgram)
        ShaderUtil.checkGLError(TAG, "After glBindBuffer")

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        ShaderUtil.checkGLError(TAG, "After glBindBuffer")

        // get handle to vertex shader's vPosition member
        mPositionHandle = GLES20.glGetAttribLocation(mProgram, "vPosition")
        ShaderUtil.checkGLError(TAG, "After glGetAttribLocation")

        // enable a handle to the vertices
        GLES20.glEnableVertexAttribArray(mPositionHandle)
        ShaderUtil.checkGLError(TAG, "After glEnableVertexAttribArray")

        // prepare the coordinate data
        GLES20.glVertexAttribPointer(
            mPositionHandle, COORDS_PER_VERTEX,
            GLES20.GL_FLOAT, false,
            vertexStride, vertexBuffer
        )
        ShaderUtil.checkGLError(TAG, "After glVertexAttribPointer")

        // get handle to fragment shader's vColor member
        mColorHandle = GLES20.glGetUniformLocation(mProgram, "vColor")

        // set color for drawing the triangle
        GLES20.glUniform4fv(mColorHandle, 1, color, 0)
        ShaderUtil.checkGLError(TAG, "After glUniform4fv")

        // get handle to shape's transformation matrix
        mMVPMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix")

        // Pass the projection and view transformation to the shader
        GLES20.glUniformMatrix4fv(mMVPMatrixHandle, 1, false, mModelViewProjectionMatrix, 0)
        ShaderUtil.checkGLError(TAG, "After glUniformMatrix4fv")

        // Draw the line
        GLES20.glDrawArrays(GLES20.GL_LINES, 0, vertexCount)
        ShaderUtil.checkGLError(TAG, "After glDrawArrays")

        // Disable vertex array
        GLES20.glDisableVertexAttribArray(mPositionHandle)
        ShaderUtil.checkGLError(TAG, "After draw")
    }

    companion object {

        internal val COORDS_PER_VERTEX = 3
        internal var coordinates = floatArrayOf(// in counterclockwise order:
            0.0f, 0.0f, 0.0f, // point 1
            1.0f, 0.0f, 0.0f
        )// point 2
    }


}