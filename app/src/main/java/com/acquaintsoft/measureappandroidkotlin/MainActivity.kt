package com.acquaintsoft.measureappandroidkotlin


import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.acquaintsoft.measureappandroidkotlin.arcore.CameraPermissionHelper
import com.acquaintsoft.measureappandroidkotlin.arcore.DisplayRotationHelper
import com.acquaintsoft.measureappandroidkotlin.arcore.rendering.BackgroundRenderer
import com.acquaintsoft.measureappandroidkotlin.arcore.rendering.ObjectRenderer
import com.acquaintsoft.measureappandroidkotlin.arcore.rendering.PlaneRenderer
import com.acquaintsoft.measureappandroidkotlin.arcore.rendering.PointCloudRenderer
import com.acquaintsoft.measureappandroidkotlin.renderer.RectanglePolygonRenderer
import com.crashlytics.android.Crashlytics
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.google.ar.core.*
import com.google.ar.core.exceptions.*
import io.fabric.sdk.android.Fabric
import java.io.IOException
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class MainActivity : AppCompatActivity() {

    // Rendering. The Renderers are created here, and initialized when the GL surface is created.
    private var surfaceView: GLSurfaceView? = null

    private var installRequested: Boolean = false

    private var session: Session? = null
    private var gestureDetector: GestureDetector? = null
    private var messageSnackbar: Snackbar? = null
    private var displayRotationHelper: DisplayRotationHelper? = null

    private val backgroundRenderer = BackgroundRenderer()
    private val planeRenderer = PlaneRenderer()
    private val pointCloud = PointCloudRenderer()

    private val cube = ObjectRenderer()
    private val cubeSelected = ObjectRenderer()
    private var rectRenderer: RectanglePolygonRenderer? = null

    // Temporary matrix allocated here to reduce number of allocations for each frame.
    private val anchorMatrix = FloatArray(MAX_CUBE_COUNT)
    private val ivCubeIconList = arrayOfNulls<ImageView>(MAX_CUBE_COUNT)
    private val cubeIconIdArray = intArrayOf(
        R.id.iv_cube1,
        R.id.iv_cube2,
        R.id.iv_cube3,
        R.id.iv_cube4,
        R.id.iv_cube5,
        R.id.iv_cube6,
        R.id.iv_cube7,
        R.id.iv_cube8,
        R.id.iv_cube9,
        R.id.iv_cube10,
        R.id.iv_cube11,
        R.id.iv_cube12,
        R.id.iv_cube13,
        R.id.iv_cube14,
        R.id.iv_cube15,
        R.id.iv_cube16
    )

    // Tap handling and UI.
    private val queuedSingleTaps = ArrayBlockingQueue<MotionEvent>(MAX_CUBE_COUNT)
    private val queuedLongPress = ArrayBlockingQueue<MotionEvent>(MAX_CUBE_COUNT)
    private val anchors = ArrayList<Anchor>()
    private val showingTapPointX = ArrayList<Float>()
    private val showingTapPointY = ArrayList<Float>()

    private val queuedScrollDx = ArrayBlockingQueue<Float>(MAX_CUBE_COUNT)
    private val queuedScrollDy = ArrayBlockingQueue<Float>(MAX_CUBE_COUNT)

    //    OverlayView overlayViewForTest;
    private var tv_result: TextView? = null
    private var fab: FloatingActionButton? = null

    private var glSerfaceRenderer: GLSurfaceRenderer? = null
    private val gestureDetectorListener = object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            // Queue tap if there is space. Tap is lost if queue is full.
            queuedSingleTaps.offer(e)
            //            log(TAG, "onSingleTapUp, e=" + e.getRawX() + ", " + e.getRawY());
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            queuedLongPress.offer(e)
        }

        override fun onDown(e: MotionEvent): Boolean {
            return true
        }

        override fun onScroll(
            e1: MotionEvent, e2: MotionEvent,
            distanceX: Float, distanceY: Float
        ): Boolean {
            //            log(TAG, "onScroll, dx=" + distanceX + " dy=" + distanceY);
            queuedScrollDx.offer(distanceX)
            queuedScrollDy.offer(distanceY)
            return true
        }
    }
    private var isVerticalMode = false
    private var popupWindow: PopupWindow? = null

    private fun log(tag: String, log: String) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, log)
        }
    }

    private fun log(e: Exception) {
        try {
            Crashlytics.logException(e)
            if (BuildConfig.DEBUG) {
                e.printStackTrace()
            }
        } catch (ex: Exception) {
            if (BuildConfig.DEBUG) {
                ex.printStackTrace()
            }
        }

    }

    private fun logStatus(msg: String) {
        try {
            Crashlytics.log(msg)
        } catch (e: Exception) {
            log(e)
        }

    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Fabric.with(this, Crashlytics())
        setContentView(R.layout.activity_main)

        tv_result = findViewById(R.id.tv_result)
        fab = findViewById(R.id.fab)

        for (i in cubeIconIdArray.indices) {
            ivCubeIconList[i] = findViewById(cubeIconIdArray[i])
            ivCubeIconList[i]?.setTag(i)
            ivCubeIconList[i]?.setOnClickListener(View.OnClickListener { view ->
                try {
                    val index = Integer.valueOf(view.tag.toString())
                    logStatus("click index cube: $index")
                    glSerfaceRenderer!!.setNowTouchingPointIndex(index)
                    glSerfaceRenderer!!.showMoreAction()
                } catch (e: Exception) {
                    log(e)
                }
            })
        }

        fab!!.setOnClickListener { v ->
            logStatus("click fab")
            val popUp = getPopupWindow()
            //                popUp.showAsDropDown(v, 0, 0); // show popup like dropdown list
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val screenWidth = resources.displayMetrics.widthPixels.toFloat()
                val screenHeight = resources.displayMetrics.heightPixels.toFloat()
                popUp.showAtLocation(v, Gravity.NO_GRAVITY, screenWidth.toInt() / 2, screenHeight.toInt() / 2)
            } else {
                popUp.showAsDropDown(v)
            }
        }
        fab!!.hide()


        displayRotationHelper = DisplayRotationHelper(/*context=*/this)

        if (CameraPermissionHelper.hasCameraPermission(this)) {
            setupRenderer()
        }

        installRequested = false
    }

    private fun setupRenderer() {
        if (surfaceView != null) {
            return
        }
        surfaceView = findViewById(R.id.surfaceview)

        // Set up tap listener.
        gestureDetector = GestureDetector(this, gestureDetectorListener)
        surfaceView!!.setOnTouchListener { v, event -> gestureDetector!!.onTouchEvent(event) }
        glSerfaceRenderer = GLSurfaceRenderer(this)
        surfaceView!!.preserveEGLContextOnPause = true
        surfaceView!!.setEGLContextClientVersion(2)
        surfaceView!!.setEGLConfigChooser(8, 8, 8, 8, 16, 0) // Alpha used for plane blending.
        surfaceView!!.setRenderer(glSerfaceRenderer)
        surfaceView!!.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    override fun onResume() {
        super.onResume()
        logStatus("onResume()")
        if (session == null) {
            var exception: Exception? = null
            var message: String? = null
            try {
                when (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
                    ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                        installRequested = true
                        return
                    }
                    ArCoreApk.InstallStatus.INSTALLED -> {
                    }
                }

                // ARCore requires camera permissions to operate. If we did not yet obtain runtime
                // permission on Android M and above, now is a good time to ask the user for it.
                if (!CameraPermissionHelper.hasCameraPermission(this)) {
                    CameraPermissionHelper.requestCameraPermission(this)
                    return
                }

                session = Session(/* context= */this)
            } catch (e: UnavailableArcoreNotInstalledException) {
                message = "Please install ARCore"
                exception = e
            } catch (e: UnavailableUserDeclinedInstallationException) {
                message = "Please install ARCore"
                exception = e
            } catch (e: UnavailableApkTooOldException) {
                message = "Please update ARCore"
                exception = e
            } catch (e: UnavailableSdkTooOldException) {
                message = "Please update this app"
                exception = e
            } catch (e: Exception) {
                message = "This device does not support AR"
                exception = e
            }

            if (message != null) {
                showSnackbarMessage(message, true)
                Log.e(TAG, "Exception creating session", exception)
                return
            }

            // Create default config and check if supported.
            val config = Config(session!!)
            if (!session!!.isSupported(config)) {
                showSnackbarMessage("This device does not support AR", true)
            }
            session!!.configure(config)

            setupRenderer()
        }

        showLoadingMessage()
        // Note that order matters - see the note in onPause(), the reverse applies here.
        try {
            session!!.resume()
        } catch (e: CameraNotAvailableException) {
            e.printStackTrace()
        }

        surfaceView!!.onResume()
        displayRotationHelper!!.onResume()
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public override fun onPause() {
        super.onPause()
        logStatus("onPause()")
        if (session != null) {
            // Note that the order matters - GLSurfaceView is paused first so that it does not try
            // to query the session. If Session is paused before GLSurfaceView, GLSurfaceView may
            // still call session.update() and get a SessionPausedException.
            displayRotationHelper!!.onPause()
            surfaceView!!.onPause()
            session!!.pause()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, results: IntArray) {
        logStatus("onRequestPermissionsResult()")
        Toast.makeText(this, R.string.need_permission, Toast.LENGTH_LONG)
            .show()
        if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
            // Permission denied with checking "Do not ask again".
            CameraPermissionHelper.launchPermissionSettings(this)
        }
        finish()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        logStatus("onWindowFocusChanged()")
        if (hasFocus) {
            // Standard Android full-screen functionality.
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun showLoadingMessage() {
        runOnUiThread {
            messageSnackbar = Snackbar.make(
                this@MainActivity.findViewById(android.R.id.content),
                "Searching for surfaces...", Snackbar.LENGTH_INDEFINITE
            )
            messageSnackbar!!.view.setBackgroundColor(-0x40cdcdce)
            messageSnackbar!!.show()
        }
    }

    private fun hideLoadingMessage() {
        runOnUiThread {
            if (messageSnackbar != null) {
                messageSnackbar!!.dismiss()
            }
            messageSnackbar = null
        }
    }

    private fun showSnackbarMessage(message: String, finishOnDismiss: Boolean) {
        messageSnackbar = Snackbar.make(
            this@MainActivity.findViewById(android.R.id.content),
            message,
            Snackbar.LENGTH_INDEFINITE
        )
        messageSnackbar!!.view.setBackgroundColor(-0x40cdcdce)
        if (finishOnDismiss) {
            messageSnackbar!!.setAction(
                "Dismiss"
            ) { messageSnackbar!!.dismiss() }
            messageSnackbar!!.addCallback(
                object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
                    override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                        super.onDismissed(transientBottomBar, event)
                        finish()
                    }
                })
        }
        messageSnackbar!!.show()
    }

    private fun toast(stringResId: Int) {
        Toast.makeText(this, stringResId, Toast.LENGTH_SHORT).show()
    }

    private fun getPopupWindow(): PopupWindow {

        // initialize a pop up window type
        popupWindow = PopupWindow(this)

        val sortList = ArrayList<String>()
        sortList.add(getString(R.string.action_1))
        sortList.add(getString(R.string.action_2))
        sortList.add(getString(R.string.action_3))
        sortList.add(getString(R.string.action_4))

        val adapter = ArrayAdapter(
            this, android.R.layout.simple_dropdown_item_1line,
            sortList
        )
        // the drop down list is a list view
        val listViewSort = ListView(this)
        // set our adapter and pass our pop up window contents
        listViewSort.adapter = adapter
        listViewSort.onItemLongClickListener =
            AdapterView.OnItemLongClickListener { parent, view, position, id ->
                when (position) {
                    3// move vertical axis
                    -> toast(R.string.action_4_toast)
                    0// delete
                    -> toast(R.string.action_1_toast)
                    1// set as first
                    -> toast(R.string.action_2_toast)
                    2// move horizontal axis
                    -> toast(R.string.action_3_toast)
                    else -> toast(R.string.action_3_toast)
                }
                true
            }
        // set on item selected
        listViewSort.onItemClickListener = AdapterView.OnItemClickListener { parent, view, position, id ->
            when (position) {
                3// move vertical axis
                -> {
                    isVerticalMode = true
                    popupWindow!!.dismiss()
                }
                0// delete
                -> {
                    glSerfaceRenderer!!.deleteNowSelection()
                    popupWindow!!.dismiss()
                    fab!!.hide()
                }
                1// set as first
                -> {
                    glSerfaceRenderer!!.setNowSelectionAsFirst()
                    popupWindow!!.dismiss()
                    fab!!.hide()
                }
                2// move horizontal axis
                -> {
                    isVerticalMode = false
                    popupWindow!!.dismiss()
                }
                else -> {
                    isVerticalMode = false
                    popupWindow!!.dismiss()
                }
            }
        }
        // some other visual settings for popup window
        popupWindow!!.isFocusable = true
        popupWindow!!.width = (resources.displayMetrics.widthPixels * 0.4f).toInt()
        // popupWindow.setBackgroundDrawable(getResources().getDrawable(R.drawable.white));
        popupWindow!!.height = WindowManager.LayoutParams.WRAP_CONTENT
        // set the listview as popup content
        popupWindow!!.contentView = listViewSort
        return popupWindow as PopupWindow
    }

    private inner class GLSurfaceRenderer(private val context: Context) : GLSurfaceView.Renderer {
        private val DEFAULT_VALUE = -1
        private var nowTouchingPointIndex = DEFAULT_VALUE
        private var viewWidth = 0
        private var viewHeight = 0
        // according to cube.obj, cube diameter = 0.02f
        private val cubeHitAreaRadius = 0.08f
        private val centerVertexOfCube = floatArrayOf(0f, 0f, 0f, 1f)
        private val vertexResult = FloatArray(4)

        private val tempTranslation = FloatArray(3)
        private val tempRotation = FloatArray(4)
        private val projmtx = FloatArray(16)
        private val viewmtx = FloatArray(16)

        private val mPoseTranslation = FloatArray(3)
        private val mPoseRotation = FloatArray(4)

        override fun onSurfaceCreated(gl: GL10, config: EGLConfig) {
            logStatus("onSurfaceCreated()")
            GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f)

            // Create the texture and pass it to ARCore session to be filled during update().
            backgroundRenderer.createOnGlThread(context)
            if (session != null) {
                session!!.setCameraTextureName(backgroundRenderer.textureId)
            }

            // Prepare the other rendering objects.
            try {
                rectRenderer = RectanglePolygonRenderer()
                cube.createOnGlThread(context, ASSET_NAME_CUBE_OBJ, ASSET_NAME_CUBE)
                cube.setMaterialProperties(0.0f, 3.5f, 1.0f, 6.0f)
                cubeSelected.createOnGlThread(context, ASSET_NAME_CUBE_OBJ, ASSET_NAME_CUBE_SELECTED)
                cubeSelected.setMaterialProperties(0.0f, 3.5f, 1.0f, 6.0f)
            } catch (e: IOException) {
                log(TAG, "Failed to read obj file")
            }

            try {
                planeRenderer.createOnGlThread(context, "trigrid.png")
            } catch (e: IOException) {
                log(TAG, "Failed to read plane texture")
            }

            pointCloud.createOnGlThread(context)
        }

        override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
            if (width <= 0 || height <= 0) {
                logStatus("onSurfaceChanged(), <= 0")
                return
            }
            logStatus("onSurfaceChanged()")

            displayRotationHelper!!.onSurfaceChanged(width, height)
            GLES20.glViewport(0, 0, width, height)
            viewWidth = width
            viewHeight = height
            setNowTouchingPointIndex(DEFAULT_VALUE)
        }

        fun deleteNowSelection() {
            logStatus("deleteNowSelection()")
            val index = nowTouchingPointIndex
            if (index > -1) {
                if (index < anchors.size) {
                    anchors.removeAt(index).detach()
                }
                if (index < showingTapPointX.size) {
                    showingTapPointX.removeAt(index)
                }
                if (index < showingTapPointY.size) {
                    showingTapPointY.removeAt(index)
                }
            }
            setNowTouchingPointIndex(DEFAULT_VALUE)
        }

        fun setNowSelectionAsFirst() {
            logStatus("setNowSelectionAsFirst()")
            val index = nowTouchingPointIndex
            if (index > -1 && index < anchors.size) {
                if (index < anchors.size) {
                    for (i in 0 until index) {
                        anchors.add(anchors.removeAt(0))
                    }
                }
                if (index < showingTapPointX.size) {
                    for (i in 0 until index) {
                        showingTapPointX.add(showingTapPointX.removeAt(0))
                    }
                }
                if (index < showingTapPointY.size) {
                    for (i in 0 until index) {
                        showingTapPointY.add(showingTapPointY.removeAt(0))
                    }
                }
            }
            setNowTouchingPointIndex(DEFAULT_VALUE)
        }

        fun getNowTouchingPointIndex(): Int {
            return nowTouchingPointIndex
        }

        fun setNowTouchingPointIndex(index: Int) {
            nowTouchingPointIndex = index
            showCubeStatus()
        }

        override fun onDrawFrame(gl: GL10) {
            //            log(TAG, "onDrawFrame(), mTouches.size=" + mTouches.size());
            // Clear screen to notify driver it should not load any pixels from previous frame.
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
            if (viewWidth == 0 || viewWidth == 0) {
                return
            }
            if (session == null) {
                return
            }
            // Notify ARCore session that the view size changed so that the perspective matrix and
            // the video background can be properly adjusted.
            displayRotationHelper!!.updateSessionIfNeeded(session!!)

            try {
                session!!.setCameraTextureName(backgroundRenderer.textureId)

                // Obtain the current frame from ARSession. When the configuration is set to
                // UpdateMode.BLOCKING (it is by default), this will throttle the rendering to the
                // camera framerate.
                val frame = session!!.update()
                val camera = frame.camera
                // Draw background.
                backgroundRenderer.draw(frame)

                // If not tracking, don't draw 3d objects.
                if (camera.trackingState == TrackingState.PAUSED) {
                    return
                }

                // Get projection matrix.
                camera.getProjectionMatrix(projmtx, 0, 0.1f, 100.0f)

                // Get camera matrix and draw.
                camera.getViewMatrix(viewmtx, 0)

                // Compute lighting from average intensity of the image.
                val lightIntensity = frame.lightEstimate.pixelIntensity

                // Visualize tracked points.
                val pointCloud = frame.acquirePointCloud()
                this@MainActivity.pointCloud.update(pointCloud)
                this@MainActivity.pointCloud.draw(viewmtx, projmtx)

                // Application is responsible for releasing the point cloud resources after
                // using it.
                pointCloud.release()

                // Check if we detected at least one plane. If so, hide the loading message.
                if (messageSnackbar != null) {
                    for (plane in session!!.getAllTrackables(Plane::class.java)) {
                        if (plane.type == com.google.ar.core.Plane.Type.HORIZONTAL_UPWARD_FACING && plane.trackingState == TrackingState.TRACKING) {
                            hideLoadingMessage()
                            break
                        }
                    }
                }

                // Visualize planes.
                planeRenderer.drawPlanes(
                    session!!.getAllTrackables(Plane::class.java),
                    camera.displayOrientedPose,
                    projmtx
                )

                // draw cube & line from last frame
                if (anchors.size < 1) {
                    // no point
                    showResult("")
                } else {
                    // draw selected cube
                    if (nowTouchingPointIndex != DEFAULT_VALUE) {
                        drawObj(getPose(anchors[nowTouchingPointIndex]), cubeSelected, viewmtx, projmtx, lightIntensity)
                        checkIfHit(cubeSelected, nowTouchingPointIndex)
                    }
                    val sb = StringBuilder()
                    var total = 0.0
                    var point1: Pose
                    // draw first cube
                    var point0 = getPose(anchors[0])
                    drawObj(point0, cube, viewmtx, projmtx, lightIntensity)
                    checkIfHit(cube, 0)
                    // draw the rest cube
                    for (i in 1 until anchors.size) {
                        point1 = getPose(anchors[i])
                        log("onDrawFrame()", "before drawObj()")
                        drawObj(point1, cube, viewmtx, projmtx, lightIntensity)
                        checkIfHit(cube, i)
                        log("onDrawFrame()", "before drawLine()")
                        drawLine(point0, point1, viewmtx, projmtx)

                        val distanceCm = (getDistance(point0, point1) * 1000).toInt() / 10.0f
                        total += distanceCm.toDouble()
                        sb.append(" + ").append(distanceCm)

                        point0 = point1
                    }

                    // show result
                    val result =
                        sb.toString().replaceFirst("[+]".toRegex(), "") + " = " + (total * 10f).toInt() / 10f + "cm"
                    showResult(result)
                }

                // check if there is any touch event
                val tap = queuedSingleTaps.poll()
                if (tap != null && camera.trackingState == TrackingState.TRACKING) {
                    for (hit in frame.hitTest(tap)) {
                        // Check if any plane was hit, and if it was hit inside the plane polygon.j
                        val trackable = hit.trackable
                        // Creates an anchor if a plane or an oriented point was hit.
                        if (trackable is Plane && trackable.isPoseInPolygon(hit.hitPose) || trackable is Point && trackable.orientationMode == Point.OrientationMode.ESTIMATED_SURFACE_NORMAL) {
                            // Cap the number of objects created. This avoids overloading both the
                            // rendering system and ARCore.
                            if (anchors.size >= 16) {
                                anchors[0].detach()
                                anchors.removeAt(0)

                                showingTapPointX.removeAt(0)
                                showingTapPointY.removeAt(0)
                            }

                            // Adding an Anchor tells ARCore that it should track this position in
                            // space. This anchor will be used in PlaneAttachment to place the 3d model
                            // in the correct position relative both to the world and to the plane.
                            anchors.add(hit.createAnchor())

                            showingTapPointX.add(tap.x)
                            showingTapPointY.add(tap.y)
                            nowTouchingPointIndex = anchors.size - 1

                            showMoreAction()
                            showCubeStatus()
                            break
                        }
                    }
                } else {
                    handleMoveEvent(nowTouchingPointIndex)
                }
            } catch (t: Throwable) {
                // Avoid crashing the application due to unhandled exceptions.
                Log.e(TAG, "Exception on the OpenGL thread", t)
            }

        }

        private fun handleMoveEvent(nowSelectedIndex: Int) {
            try {
                if (showingTapPointX.size < 1 || queuedScrollDx.size < 2) {
                    // no action, don't move
                    return
                }
                if (nowTouchingPointIndex == DEFAULT_VALUE) {
                    // no selected cube, don't move
                    return
                }
                if (nowSelectedIndex >= showingTapPointX.size) {
                    // wrong index, don't move.
                    return
                }
                var scrollDx = 0f
                var scrollDy = 0f
                val scrollQueueSize = queuedScrollDx.size
                for (i in 0 until scrollQueueSize) {
                    scrollDx += queuedScrollDx.poll()
                    scrollDy += queuedScrollDy.poll()
                }

                if (isVerticalMode) {
                    val anchor = anchors.removeAt(nowSelectedIndex)
                    anchor.detach()
                    setPoseDataToTempArray(getPose(anchor))
                    //                        log(TAG, "point[" + nowSelectedIndex + "] move vertical "+ (scrollDy / viewHeight) + ", tY=" + tempTranslation[1]
                    //                                + ", new tY=" + (tempTranslation[1] += (scrollDy / viewHeight)));
                    tempTranslation[1] += scrollDy / viewHeight
                    anchors.add(
                        nowSelectedIndex,
                        session!!.createAnchor(Pose(tempTranslation, tempRotation))
                    )
                } else {
                    val toX = showingTapPointX[nowSelectedIndex] - scrollDx
                    showingTapPointX.removeAt(nowSelectedIndex)
                    showingTapPointX.add(nowSelectedIndex, toX)

                    val toY = showingTapPointY[nowSelectedIndex] - scrollDy
                    showingTapPointY.removeAt(nowSelectedIndex)
                    showingTapPointY.add(nowSelectedIndex, toY)

                    if (anchors.size > nowSelectedIndex) {
                        val anchor = anchors.removeAt(nowSelectedIndex)
                        anchor.detach()
                        // remove duplicated anchor
                        setPoseDataToTempArray(getPose(anchor))
                        tempTranslation[0] -= scrollDx / viewWidth
                        tempTranslation[2] -= scrollDy / viewHeight
                        anchors.add(
                            nowSelectedIndex,
                            session!!.createAnchor(Pose(tempTranslation, tempRotation))
                        )
                    }
                }
            } catch (e: NotTrackingException) {
                e.printStackTrace()
            }

        }

        private fun getPose(anchor: Anchor): Pose {
            val pose = anchor.pose
            pose.getTranslation(mPoseTranslation, 0)
            pose.getRotationQuaternion(mPoseRotation, 0)
            return Pose(mPoseTranslation, mPoseRotation)
        }

        private fun setPoseDataToTempArray(pose: Pose) {
            pose.getTranslation(tempTranslation, 0)
            pose.getRotationQuaternion(tempRotation, 0)
        }

        private fun drawLine(pose0: Pose, pose1: Pose, viewmtx: FloatArray, projmtx: FloatArray) {
            val lineWidth = 0.002f
            val lineWidthH = lineWidth / viewHeight * viewWidth
            rectRenderer!!.setVerts(
                pose0.tx() - lineWidth, pose0.ty() + lineWidthH, pose0.tz() - lineWidth,
                pose0.tx() + lineWidth, pose0.ty() + lineWidthH, pose0.tz() + lineWidth,
                pose1.tx() + lineWidth, pose1.ty() + lineWidthH, pose1.tz() + lineWidth,
                pose1.tx() - lineWidth, pose1.ty() + lineWidthH, pose1.tz() - lineWidth,
                pose0.tx() - lineWidth, pose0.ty() - lineWidthH, pose0.tz() - lineWidth,
                pose0.tx() + lineWidth, pose0.ty() - lineWidthH, pose0.tz() + lineWidth,
                pose1.tx() + lineWidth, pose1.ty() - lineWidthH, pose1.tz() + lineWidth,
                pose1.tx() - lineWidth, pose1.ty() - lineWidthH, pose1.tz() - lineWidth
            )

            rectRenderer!!.draw(viewmtx, projmtx)
        }

        private fun drawObj(
            pose: Pose,
            renderer: ObjectRenderer,
            cameraView: FloatArray,
            cameraPerspective: FloatArray,
            lightIntensity: Float
        ) {
            pose.toMatrix(anchorMatrix, 0)
            renderer.updateModelMatrix(anchorMatrix, 1F)
            renderer.draw(cameraView, cameraPerspective, lightIntensity)
        }

        private fun checkIfHit(renderer: ObjectRenderer, cubeIndex: Int) {
            if (isMVPMatrixHitMotionEvent(renderer.modelViewProjectionMatrix, queuedLongPress.peek())) {
                // long press hit a cube, show context menu for the cube
                nowTouchingPointIndex = cubeIndex
                queuedLongPress.poll()
                showMoreAction()
                showCubeStatus()
                runOnUiThread { fab!!.performClick() }
            } else if (isMVPMatrixHitMotionEvent(renderer.modelViewProjectionMatrix, queuedSingleTaps.peek())) {
                nowTouchingPointIndex = cubeIndex
                queuedSingleTaps.poll()
                showMoreAction()
                showCubeStatus()
            }
        }

        private fun isMVPMatrixHitMotionEvent(ModelViewProjectionMatrix: FloatArray, event: MotionEvent?): Boolean {
            if (event == null) {
                return false
            }
            Matrix.multiplyMV(vertexResult, 0, ModelViewProjectionMatrix, 0, centerVertexOfCube, 0)
            /**
             * vertexResult = [x, y, z, w]
             *
             * coordinates in View
             * ┌─────────────────────────────────────────┐╮
             * │[0, 0]                     [viewWidth, 0]│
             * │       [viewWidth/2, viewHeight/2]       │view height
             * │[0, viewHeight]   [viewWidth, viewHeight]│
             * └─────────────────────────────────────────┘╯
             * ╰                view width               ╯
             *
             * coordinates in GLSurfaceView frame
             * ┌─────────────────────────────────────────┐╮
             * │[-1.0,  1.0]                  [1.0,  1.0]│
             * │                 [0, 0]                  │view height
             * │[-1.0, -1.0]                  [1.0, -1.0]│
             * └─────────────────────────────────────────┘╯
             * ╰                view width               ╯
             */
            // circle hit test
            val radius = viewWidth / 2 * (cubeHitAreaRadius / vertexResult[3])
            val dx = event.x - viewWidth / 2 * (1 + vertexResult[0] / vertexResult[3])
            val dy = event.y - viewHeight / 2 * (1 - vertexResult[1] / vertexResult[3])
            val distance = Math.sqrt((dx * dx + dy * dy).toDouble())
            //            // for debug
            //            overlayViewForTest.setPoint("cubeCenter", screenX, screenY);
            //            overlayViewForTest.postInvalidate();
            return distance < radius
        }

        private fun getDistance(pose0: Pose, pose1: Pose): Double {
            val dx = pose0.tx() - pose1.tx()
            val dy = pose0.ty() - pose1.ty()
            val dz = pose0.tz() - pose1.tz()
            return Math.sqrt((dx * dx + dz * dz + dy * dy).toDouble())
        }

        private fun showResult(result: String) {
            runOnUiThread { tv_result!!.text = result }
        }

        fun showMoreAction() {
            runOnUiThread { fab!!.show() }
        }

        private fun hideMoreAction() {
            runOnUiThread { fab!!.hide() }
        }

        private fun showCubeStatus() {
            runOnUiThread(object : Runnable {
                override fun run() {
                    val nowSelectIndex = glSerfaceRenderer!!.getNowTouchingPointIndex()
                    run {
                        var i = 0
                        while (i < ivCubeIconList.size && i < anchors.size) {
                            ivCubeIconList[i]?.setEnabled(true)
                            ivCubeIconList[i]?.setActivated(i == nowSelectIndex)
                            i++
                        }
                    }
                    for (i in anchors.size until ivCubeIconList.size) {
                        ivCubeIconList[i]?.setEnabled(false)
                    }
                }
            })
        }



    }

    companion object {

        private val TAG = MainActivity::class.java.simpleName
        private val ASSET_NAME_CUBE_OBJ = "cube.obj"
        private val ASSET_NAME_CUBE = "cube_green.png"
        private val ASSET_NAME_CUBE_SELECTED = "cube_cyan.png"

        private val MAX_CUBE_COUNT = 16
    }
}
