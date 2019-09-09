package com.acquaintsoft.measureappandroidkotlin.arcore

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import android.view.WindowManager
import androidx.annotation.RequiresApi
import com.google.ar.core.Session


class DisplayRotationHelper
/**
 * Constructs the DisplayRotationHelper but does not register the listener yet.
 *
 * @param context the Android [Context].
 */
@RequiresApi(api = Build.VERSION_CODES.M)
constructor(private val context: Context) : DisplayManager.DisplayListener {
    private var viewportChanged: Boolean = false
    private var viewportWidth: Int = 0
    private var viewportHeight: Int = 0
    private val display: Display

    /**
     * Returns the current rotation state of android display. Same as [Display.getRotation].
     */
    val rotation: Int
        get() = display.rotation

    init {
        display = context.getSystemService(WindowManager::class.java)!!.defaultDisplay
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    fun onResume() {
        context.getSystemService(DisplayManager::class.java)!!.registerDisplayListener(this, null)
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    fun onPause() {
        context.getSystemService(DisplayManager::class.java)!!.unregisterDisplayListener(this)
    }

    /**
     * Records a change in surface dimensions. This will be later used by [ ][.updateSessionIfNeeded]. Should be called from [ ].
     *
     * @param width the updated width of the surface.
     * @param height the updated height of the surface.
     */
    fun onSurfaceChanged(width: Int, height: Int) {
        viewportWidth = width
        viewportHeight = height
        viewportChanged = true
    }

    /**
     * Updates the session display geometry if a change was posted either by [ ][.onSurfaceChanged] call or by [.onDisplayChanged] system callback. This
     * function should be called explicitly before each call to [Session.update]. This
     * function will also clear the 'pending update' (viewportChanged) flag.
     *
     * @param session the [Session] object to update if display geometry changed.
     */
    fun updateSessionIfNeeded(session: Session) {
        if (viewportChanged) {
            val displayRotation = display.rotation
            session.setDisplayGeometry(displayRotation, viewportWidth, viewportHeight)
            viewportChanged = false
        }
    }

    override fun onDisplayAdded(displayId: Int) {}

    override fun onDisplayRemoved(displayId: Int) {}

    override fun onDisplayChanged(displayId: Int) {
        viewportChanged = true
    }
}