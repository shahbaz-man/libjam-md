package com.asdev.libjam.md.glg2d

import com.asdev.libjam.md.layout.FrameDecoration
import com.asdev.libjam.md.layout.LinearLayout
import com.asdev.libjam.md.layout.newLayoutParams
import com.asdev.libjam.md.theme.THEME
import com.asdev.libjam.md.theme.Theme
import com.asdev.libjam.md.thread.*
import com.asdev.libjam.md.util.*
import com.asdev.libjam.md.view.View
import org.jogamp.glg2d.GLG2DCanvas
import java.awt.*
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.awt.event.MouseMotionListener
import javax.swing.JFrame
import javax.swing.JPanel

/**
 * Created by Asdev on 10/26/16. All rights reserved.
 * Unauthorized copying via any medium is stricitly
 * prohibited.
 *
 * Authored by Shahbaz Momi as part of libjam-md
 * under the package com.asdev.libjam.md.layout
 */

/**
 * A RootView that uses the GLG2D Canvas.
 */
class GLG2DRootView(view: View, title: String, dim: Dimension, isUndecorated: Boolean = false): JPanel(), Loopable, MouseListener, MouseMotionListener {

    /**
     * The frame on the screen.
     */
    private val frame: JFrame

    /**
     * The UI looper for this RootView.
     */
    private val looper: Looper

    private val rootView: View

    private var frameDecoration: FrameDecoration? = null

    init {
        frame = JFrame(title)
        frame.isUndecorated = isUndecorated

        if(isUndecorated) {
            frameDecoration = FrameDecoration(title, frame, null)

            val container = LinearLayout()
            container.addChild(frameDecoration!!)
            container.addChild(view)

            rootView = container
        } else {
            rootView = view
        }

        looper = Looper(this)
        looper.name = "UILooper"
        looper.isDaemon = true
        looper.start()

        size = dim

        frame.size = dim

        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.setLocationRelativeTo(null)

        frame.add(GLG2DCanvas(this))

        addMouseListener(this)
        addMouseMotionListener(this)

        setTheme(THEME)
    }

    fun getFrameDecoration() = frameDecoration

    fun setTheme(theme: Theme) {
        looper.postMessage(Message(MESSAGE_TYPE_ROOT_VIEW, MESSAGE_ACTION_THEME_CHANGED).apply { data0 = theme })
    }

    override fun setSize(d: Dimension) {
        super.setSize(d)
        looper.postMessage(Message(MESSAGE_TYPE_ROOT_VIEW, MESSAGE_ACTION_RESIZE).apply { data0 = d })
    }

    /**
     * Actually sets the size. Should be called on the UILooper only!!!
     */
    private fun setSize0(attemptSize: Dimension) {
        if(DEBUG)
            println("[RootView] Resetting the size to: $attemptSize")

        // check if it is within the bounds of the min and max of VG
        val dims = rootView.onMeasure(newLayoutParams())

        var size = FloatDim(attemptSize.width.toFloat(), attemptSize.height.toFloat())

        // does a size check
        if(!(dims.minSize to dims.maxSize fits size)) {
            // if size is smaller than use the min size otherwise use the max size
            var newW = size.w
            var newH = size.h
            if(size.w <= dims.minSize.w && dims.minSize != DIM_UNSET && dims.minSize != DIM_UNLIMITED) {
                // use min w
                newW = dims.minSize.w
            } else if(size.w >= dims.maxSize.w && dims.maxSize != DIM_UNSET && dims.maxSize != DIM_UNLIMITED) {
                newW = dims.maxSize.w
            }

            if(size.h <= dims.minSize.h && dims.minSize != DIM_UNSET && dims.minSize != DIM_UNLIMITED) {
                newH = dims.minSize.h
            } else if(size.h >= dims.maxSize.h && dims.maxSize != DIM_UNSET && dims.maxSize != DIM_UNLIMITED) {
                newH = dims.maxSize.h
            }

            size = FloatDim(newW, newH)
        }

        // we now know are size, so set the frame to that
        frame.contentPane.size = Dimension( size.w.toInt(), size.h.toInt() )

        frame.minimumSize = Dimension(dims.minSize.w.toInt() + 200, dims.minSize.h.toInt() + 200)

        // call on layout of the view group
        reLayout()
    }

    override fun loop() {
        rootView.loop()
    }

    override fun handleMessage(msg: Message) {
        if(DEBUG) {
            println("[RootView] Handling message: $msg")
        }
        if(msg.type == MESSAGE_TYPE_VIEW) {
            if(msg.action == MESSAGE_ACTION_REQUEST_LAYOUT)  {
                reLayout()
            } else if(msg.action == MESSAGE_ACTION_REPAINT) {
                // repaint
                requestPaint()
            }
        } else if(msg.type == MESSAGE_TYPE_ROOT_VIEW) {
            if(msg.action == MESSAGE_ACTION_RESIZE) {
                // resize on the proper thread (this thread)
                setSize0(msg.data0 as Dimension)
            } else if(msg.action == MESSAGE_ACTION_THEME_CHANGED) {
                val theme = msg.data0 as Theme
                // initialize the theme
                theme.init()
                val oldTheme = THEME
                // update the current one
                THEME = theme
                // call onThemeChanged of the view
                rootView.onThemeChange(oldTheme, THEME)
                // call on draw
                requestPaint()
            } else if(msg.action == MESSAGE_ACTION_SET_CURSOR) {
                val cursorInt = msg.data0 as Int
                val cursor = Cursor.getPredefinedCursor(cursorInt)
                // set it
                frame.cursor = cursor
            }
        }
    }

    private fun reLayout() {
        if(DEBUG)
            println("[RootView] Performing a layout pass...")

        val start = System.nanoTime()

        rootView.onLayout(FloatDim(size.width.toFloat(), size.height.toFloat()))

        if(DEBUG)
            println("[RootView] On layout took ${(System.nanoTime() - start) / 1000000.0}ms")

        // repaint because of a layout change
        requestPaint()
    }

    private fun requestPaint() {
        if(DEBUG)
            println("[RootView] Requesting a repaint...")

        if(frame.isVisible)
            repaint()
    }

    override fun loopsPerSecond() = 70

    fun setCursor(cursor: Int) {
        looper.postMessage(Message(MESSAGE_TYPE_ROOT_VIEW, MESSAGE_ACTION_SET_CURSOR).apply { data0 = cursor })
    }

    override fun paintComponent(g: Graphics?) {
        val d = Debug()
        d.startTimer()

        if(g == null || g !is Graphics2D)
            return

        g.clearRect(0, 0, size.width, size.height)

        // enable anti-aliasing
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        // enable text anti-aliasing
        // g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)

        // check for animations TODO implement these animations
//        if(!maxXAnim.hasEnded() || !maxWAnim.hasEnded() ||
//                !maxYAnim.hasEnded() || !maxHAnim.hasEnded()) {
//            if((maxHAnim.getValue() > 0f || maxWAnim.getValue() > 0f) && frame.opacity == 0f) // frame flashing hack
//                frame.opacity = 1f
//
//            // set the clip to the values
//            g.setClip(maxXAnim.getValue().toInt(), maxYAnim.getValue().toInt(), maxWAnim.getValue().toInt(), maxHAnim.getValue().toInt())
//            requestPaint()
//        }
//
//        if(!minYAnim.hasEnded()) {
//            // translate it
//            g.translate(0.0, -minYAnim.getValue().toDouble())
//            requestPaint()
//        }


        // call on draw on the root view group
        rootView.onDraw(g)
        d.stopTimer("[RootView] On draw")
    }

    /**
     * Shows the frame (internal JFrame).
     */
    fun showFrame() {
        if(DEBUG)
            println("[RootView] Showing frame...")

        frame.isVisible = true
    }

    /**
     * Hides the frame (internal JFrame).
     */
    fun hideFrame() {
        if(DEBUG)
            println("[RootView] Hiding frame...")

        frame.isVisible = false
    }

    // mouse events

    override fun mouseEntered(e: MouseEvent?) {
    }

    override fun mouseClicked(e: MouseEvent?) {

    }

    override fun mouseExited(e: MouseEvent?) {

    }

    override fun mouseReleased(e: MouseEvent?) {
        // left click only
        if(e!!.button == MouseEvent.BUTTON1)
            rootView.onMouseRelease(e, e.point)
    }

    override fun mousePressed(e: MouseEvent?) {
        // left click only
        if(e!!.button == MouseEvent.BUTTON1)
            rootView.onMousePress(e, e.point)
    }

    override fun mouseMoved(e: MouseEvent?) {
        rootView.onMouseMoved(e!!, e.point)
    }

    override fun mouseDragged(e: MouseEvent?) {
        rootView.onMouseDragged(e!!, e.point)
    }
}