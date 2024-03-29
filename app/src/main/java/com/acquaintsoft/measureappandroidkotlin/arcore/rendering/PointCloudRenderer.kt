package com.acquaintsoft.measureappandroidkotlin.arcore.rendering

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix

import com.acquaintsoft.measureappandroidkotlin.R
import com.google.ar.core.PointCloud


import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Renders a point cloud.
 */
class PointCloudRenderer {

    private var vbo: Int = 0
    private var vboSize: Int = 0

    private var programName: Int = 0
    private var positionAttribute: Int = 0
    private var modelViewProjectionUniform: Int = 0
    private var colorUniform: Int = 0
    private var pointSizeUniform: Int = 0

    private var numPoints = 0

    // Keep track of the last point cloud rendered to avoid updating the VBO if point cloud
    // was not changed.
    private var lastPointCloud: PointCloud? = null

    /**
     * Allocates and initializes OpenGL resources needed by the plane renderer. Must be called on the
     * OpenGL thread, typically in [GLSurfaceView.Renderer.onSurfaceCreated].
     *
     * @param context Needed to access shader source.
     */
    fun createOnGlThread(context: Context) {
        ShaderUtil.checkGLError(TAG, "before create")

        val buffers = IntArray(1)
        GLES20.glGenBuffers(1, buffers, 0)
        vbo = buffers[0]
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)

        vboSize = INITIAL_BUFFER_POINTS * BYTES_PER_POINT
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vboSize, null, GLES20.GL_DYNAMIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)

        ShaderUtil.checkGLError(TAG, "buffer alloc")

        val vertexShader = ShaderUtil.loadGLShader(TAG, context, GLES20.GL_VERTEX_SHADER, R.raw.point_cloud_vertex)
        val passthroughShader = ShaderUtil.loadGLShader(
            TAG, context, GLES20.GL_FRAGMENT_SHADER, R.raw.passthrough_fragment
        )

        programName = GLES20.glCreateProgram()
        GLES20.glAttachShader(programName, vertexShader)
        GLES20.glAttachShader(programName, passthroughShader)
        GLES20.glLinkProgram(programName)
        GLES20.glUseProgram(programName)

        ShaderUtil.checkGLError(TAG, "program")

        positionAttribute = GLES20.glGetAttribLocation(programName, "a_Position")
        colorUniform = GLES20.glGetUniformLocation(programName, "u_Color")
        modelViewProjectionUniform = GLES20.glGetUniformLocation(programName, "u_ModelViewProjection")
        pointSizeUniform = GLES20.glGetUniformLocation(programName, "u_PointSize")

        ShaderUtil.checkGLError(TAG, "program  params")
    }

    /**
     * Updates the OpenGL buffer contents to the provided point. Repeated calls with the same point
     * cloud will be ignored.
     */
    fun update(cloud: PointCloud) {
        if (lastPointCloud === cloud) {
            // Redundant call.
            return
        }

        ShaderUtil.checkGLError(TAG, "before update")

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        lastPointCloud = cloud

        // If the VBO is not large enough to fit the new point cloud, resize it.
        numPoints = lastPointCloud!!.points.remaining() / FLOATS_PER_POINT
        if (numPoints * BYTES_PER_POINT > vboSize) {
            while (numPoints * BYTES_PER_POINT > vboSize) {
                vboSize *= 2
            }
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vboSize, null, GLES20.GL_DYNAMIC_DRAW)
        }
        GLES20.glBufferSubData(
            GLES20.GL_ARRAY_BUFFER, 0, numPoints * BYTES_PER_POINT, lastPointCloud!!.points
        )
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)

        ShaderUtil.checkGLError(TAG, "after update")
    }

    /**
     * Renders the point cloud. ArCore point cloud is given in world space.
     *
     * @param cameraView the camera view matrix for this frame, typically from [     ][com.google.ar.core.Camera.getViewMatrix].
     * @param cameraPerspective the camera projection matrix for this frame, typically from [     ][com.google.ar.core.Camera.getProjectionMatrix].
     */
    fun draw(cameraView: FloatArray, cameraPerspective: FloatArray) {
        val modelViewProjection = FloatArray(16)
        Matrix.multiplyMM(modelViewProjection, 0, cameraPerspective, 0, cameraView, 0)

        ShaderUtil.checkGLError(TAG, "Before draw")

        GLES20.glUseProgram(programName)
        GLES20.glEnableVertexAttribArray(positionAttribute)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glVertexAttribPointer(positionAttribute, 4, GLES20.GL_FLOAT, false, BYTES_PER_POINT, 0)
        GLES20.glUniform4f(colorUniform, 31.0f / 255.0f, 188.0f / 255.0f, 210.0f / 255.0f, 1.0f)
        GLES20.glUniformMatrix4fv(modelViewProjectionUniform, 1, false, modelViewProjection, 0)
        GLES20.glUniform1f(pointSizeUniform, 5.0f)

        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, numPoints)
        GLES20.glDisableVertexAttribArray(positionAttribute)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)

        ShaderUtil.checkGLError(TAG, "Draw")
    }

    companion object {
        private val TAG = PointCloud::class.java.simpleName

        private val BYTES_PER_FLOAT = java.lang.Float.SIZE / 8
        private val FLOATS_PER_POINT = 4 // X,Y,Z,confidence.
        private val BYTES_PER_POINT = BYTES_PER_FLOAT * FLOATS_PER_POINT
        private val INITIAL_BUFFER_POINTS = 1000
    }
}