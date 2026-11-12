package com.deskpilot

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import com.freerdp.freerdpcore.application.GlobalApp
import com.freerdp.freerdpcore.application.SessionState
import com.freerdp.freerdpcore.services.LibFreeRDP
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.roundToInt

enum class PointerMode { TOUCHPAD, DIRECT }

class RdpSurfaceView(context: Context) : View(context), LibFreeRDP.UIEventListener {
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val worker = Executors.newSingleThreadExecutor()
    private var session: SessionState? = null
    private var frame: Bitmap? = null
    private var connected = false
    private var mode = PointerMode.TOUCHPAD
    private var cursorScale = 1f
    private var lastX = 0f
    private var lastY = 0f
    private var remoteWidth = 1280
    private var remoteHeight = 720
    private var statusListener: ((String) -> Unit)? = null

    // FreeRDP PTR_FLAGS_* values from the Android client input path.
    private val ptrMove = 0x0800
    private val ptrButton1 = 0x1000
    private val ptrButton2 = 0x2000
    private val ptrButton3 = 0x4000
    private val ptrDown = 0x8000
    private val ptrWheel = 0x0200

    fun setStatusListener(listener: (String) -> Unit) {
        statusListener = listener
    }

    fun setPointerMode(value: PointerMode) { mode = value }
    fun setCursorScale(value: Float) { cursorScale = value.coerceIn(0.5f, 3f) }

    fun connect(host: String, username: String, password: String, port: Int,
                width: Int, height: Int) {
        disconnect()
        val uri = Uri.Builder()
            .scheme("freerdp")
            .encodedAuthority(
                buildString {
                    if (username.isNotBlank()) append(Uri.encode(username)).append("@")
                    append(host.trim())
                    if (port != 3389) append(":").append(port)
                }
            )
            .appendPath("connect")
            .appendQueryParameter("p", password)
            .apply {
                if (width > 0 && height > 0) {
                    appendQueryParameter("w", width.toString())
                    appendQueryParameter("h", height.toString())
                }
                appendQueryParameter("bpp", "32")
            }
            .build()

        statusListener?.invoke("Connecting…")
        worker.execute {
            try {
                val created = GlobalApp.createSession(uri, context.applicationContext)
                session = created
                created.setUIEventListener(this)
                created.connect(context.applicationContext)
            } catch (t: Throwable) {
                postStatus("Connection failed: ${t.message ?: t.javaClass.simpleName}")
            }
        }
    }

    fun disconnect() {
        val old = session ?: return
        session = null
        connected = false
        worker.execute {
            runCatching { LibFreeRDP.disconnect(old.instance) }
            runCatching { GlobalApp.freeSession(old.instance) }
        }
        postStatus("Disconnected")
    }

    private fun postStatus(value: String) {
        post { statusListener?.invoke(value) }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = frame
        if (bmp == null || bmp.isRecycled) {
            paint.color = 0xFF111827.toInt()
            canvas.drawColor(paint.color)
            return
        }
        val src = Rect(0, 0, bmp.width, bmp.height)
        val scale = minOf(width.toFloat() / bmp.width, height.toFloat() / bmp.height)
        val dstW = (bmp.width * scale).roundToInt()
        val dstH = (bmp.height * scale).roundToInt()
        val left = (width - dstW) / 2
        val top = (height - dstH) / 2
        canvas.drawBitmap(bmp, src, Rect(left, top, left + dstW, top + dstH), paint)
    }

    private fun mapPoint(x: Float, y: Float): Pair<Int, Int> {
        val bmp = frame
        val rw = bmp?.width ?: remoteWidth
        val rh = bmp?.height ?: remoteHeight
        val scale = minOf(width.toFloat() / max(1, rw), height.toFloat() / max(1, rh))
        val shownW = rw * scale
        val shownH = rh * scale
        val offsetX = (width - shownW) / 2f
        val offsetY = (height - shownH) / 2f
        val rx = ((x - offsetX) / scale).toInt().coerceIn(0, rw - 1)
        val ry = ((y - offsetY) / scale).toInt().coerceIn(0, rh - 1)
        return rx to ry
    }

    private fun sendPointer(x: Int, y: Int, flags: Int) {
        session?.let { LibFreeRDP.sendCursorEvent(it.instance, x, y, flags) }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (session == null) return false
        val (rx, ry) = mapPoint(event.x, event.y)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x; lastY = event.y
                if (mode == PointerMode.DIRECT) sendPointer(rx, ry, ptrButton1 or ptrDown)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (mode == PointerMode.DIRECT) {
                    sendPointer(rx, ry, ptrMove)
                } else {
                    val dx = event.x - lastX
                    val dy = event.y - lastY
                    val (cx, cy) = mapPoint(width / 2f, height / 2f)
                    val factor = (1f / cursorScale).coerceIn(0.33f, 2f)
                    sendPointer((cx + dx * factor).toInt().coerceIn(0, remoteWidth - 1),
                        (cy + dy * factor).toInt().coerceIn(0, remoteHeight - 1), ptrMove)
                }
                lastX = event.x; lastY = event.y
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (mode == PointerMode.DIRECT) sendPointer(rx, ry, ptrButton1)
                else sendPointer(rx, ry, ptrMove)
                return true
            }
            else -> return true
        }
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_SCROLL) {
            val (x, y) = mapPoint(event.x, event.y)
            val v = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
            sendPointer(x, y, ptrWheel or if (v > 0) 0x0078 else 0xFF88)
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val s = session ?: return super.onKeyDown(keyCode, event)
        return LibFreeRDP.sendKeyEvent(s.instance, keyCode, true)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        val s = session ?: return super.onKeyUp(keyCode, event)
        return LibFreeRDP.sendKeyEvent(s.instance, keyCode, false)
    }

    override fun OnSettingsChanged(width: Int, height: Int, bpp: Int) {
        remoteWidth = width; remoteHeight = height
        val config = if (bpp > 16) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565
        val bmp = Bitmap.createBitmap(width, height, config)
        frame = bmp
        session?.setSurface(BitmapDrawable(resources, bmp))
        post { invalidate() }
    }

    override fun OnGraphicsUpdate(x: Int, y: Int, width: Int, height: Int) {
        val s = session ?: return
        val bmp = frame ?: s.surface?.bitmap ?: return
        LibFreeRDP.updateGraphics(s.instance, bmp, x, y, width, height)
        post { invalidate(Rect(x, y, x + width, y + height)) }
    }

    override fun OnGraphicsResize(width: Int, height: Int, bpp: Int) {
        remoteWidth = width; remoteHeight = height
        val config = if (bpp > 16) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565
        val bmp = Bitmap.createBitmap(width, height, config)
        frame = bmp
        session?.setSurface(BitmapDrawable(resources, bmp))
        post { invalidate() }
    }

    override fun OnAuthenticate(username: StringBuilder, domain: StringBuilder, password: StringBuilder): Boolean = true
    override fun OnGatewayAuthenticate(username: StringBuilder, domain: StringBuilder, password: StringBuilder): Boolean = true
    override fun OnVerifiyCertificateEx(host: String, port: Long, commonName: String, subject: String,
        issuer: String, fingerprint: String, flags: Long): Int = 0
    override fun OnVerifyChangedCertificateEx(host: String, port: Long, commonName: String, subject: String,
        issuer: String, fingerprint: String, oldSubject: String, oldIssuer: String,
        oldFingerprint: String, flags: Long): Int = 0
    override fun OnExperimentalFeature(feature: Int): Boolean = true
    override fun OnRemoteClipboardChanged(data: String) {}
    override fun OnRemoteClipboardImageChanged(data: ByteArray) {}
    override fun OnPointerSet(pixels: IntArray, width: Int, height: Int, hotX: Int, hotY: Int) {}
    override fun OnPointerSetNull() {}
    override fun OnPointerSetDefault() {}
    override fun OnRailWindowUpdate(windowId: Long, width: Int, height: Int, pixels: IntArray) {}
    override fun OnRailWindowMove(windowId: Long, x: Int, y: Int, w: Int, h: Int) {}
    override fun OnRailWindowHide(windowId: Long) {}
    override fun OnRailWindowDestroy(windowId: Long) {}
    override fun OnRailSessionEnd() {}
    override fun OnRailMonitoredDesktop(windowIds: LongArray, activeWindowId: Long) {}

    fun close() {
        disconnect()
        worker.shutdown()
    }
}
