package com.rescuedroid.rescuedroid.mirror

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaCodecList
import android.os.Bundle
import android.util.Log
import android.view.Surface
import android.view.SurfaceView
import android.view.Gravity
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.cgutman.adblib.AdbStream
import com.rescuedroid.rescuedroid.adb.AdbManager
import com.rescuedroid.rescuedroid.control.RemoteControl
import com.rescuedroid.rescuedroid.tools.ScrcpyTool
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

@AndroidEntryPoint
class MirrorActivity : ComponentActivity() {

    @Inject
    lateinit var scrcpyTool: ScrcpyTool

    @Inject
    lateinit var remoteControl: RemoteControl

    private companion object {
        const val TAG = "MIRROR"
        const val DEFAULT_WIDTH = 720
        const val DEFAULT_HEIGHT = 1280
        const val DEQUEUE_TIMEOUT_US = 10_000L
    }

    private lateinit var surfaceView: SurfaceView
    private lateinit var codecIndicator: TextView
    private lateinit var touchOverlay: FrameLayout
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var currentCodec = "h265" // h265, h264, av1
    
    private var deviceWidth = DEFAULT_WIDTH
    private var deviceHeight = DEFAULT_HEIGHT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val root = FrameLayout(this)
        surfaceView = SurfaceView(this)
        root.addView(surfaceView)

        touchOverlay = FrameLayout(this)
        root.addView(touchOverlay)

        codecIndicator = TextView(this).apply {
            text = "INICIALIZANDO..."
            setTextColor(android.graphics.Color.GREEN)
            setBackgroundColor(android.graphics.Color.parseColor("#80000000"))
            setPadding(20, 10, 20, 10)
            textSize = 12f
            setOnClickListener { alternarCodec() }
        }
        
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            setMargins(0, 40, 40, 0)
        }
        root.addView(codecIndicator, params)

        setupTouchControl()

        setContentView(root)

        surfaceView.holder.addCallback(object : android.view.SurfaceHolder.Callback {
            override fun surfaceCreated(holder: android.view.SurfaceHolder) {
                iniciarDecoder(holder.surface)
            }
            override fun surfaceChanged(h: android.view.SurfaceHolder, f: Int, w: Int, hi: Int) = Unit
            override fun surfaceDestroyed(h: android.view.SurfaceHolder) {
                job?.cancel()
            }
        })
    }

    private fun showTouchFeedback(x: Float, y: Float) {
        val circle = android.view.View(this).apply {
            val size = 80
            layoutParams = FrameLayout.LayoutParams(size, size).apply {
                leftMargin = (x - size / 2).toInt()
                topMargin = (y - size / 2).toInt()
            }
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(android.graphics.Color.argb(100, 0, 255, 255))
                setStroke(2, android.graphics.Color.CYAN)
            }
        }
        touchOverlay.addView(circle)
        circle.animate()
            .scaleX(2f)
            .scaleY(2f)
            .alpha(0f)
            .setDuration(300)
            .withEndAction { touchOverlay.removeView(circle) }
            .start()
    }

    private fun setupTouchControl() {
        var startX = 0f
        var startY = 0f
        var startTime = 0L

        surfaceView.setOnTouchListener { v, event ->
            val serial = scrcpyTool.activeConnection?.toString() ?: "device"
            val scaleX = deviceWidth.toFloat() / v.width
            val scaleY = deviceHeight.toFloat() / v.height
            
            val x = (event.x * scaleX).toInt()
            val y = (event.y * scaleY).toInt()

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    startY = event.y
                    startTime = System.currentTimeMillis()
                    showTouchFeedback(event.x, event.y)
                }
                MotionEvent.ACTION_UP -> {
                    val duration = System.currentTimeMillis() - startTime
                    val dist = Math.hypot((event.x - startX).toDouble(), (event.y - startY).toDouble())
                    
                    if (dist < 20 && duration < 200) {
                        remoteControl.tap(serial, x, y)
                    } else if (dist > 50) {
                        val x1 = (startX * scaleX).toInt()
                        val y1 = (startY * scaleY).toInt()
                        remoteControl.swipe(serial, x1, y1, x, y, duration.toInt())
                        showTouchFeedback(event.x, event.y)
                    }
                }
            }
            true
        }
    }

    private fun isAv1Supported(): Boolean {
        return try {
            val list = MediaCodecList(MediaCodecList.ALL_CODECS)
            list.codecInfos.any { it.supportedTypes.contains("video/av01") }
        } catch (e: Exception) { false }
    }

    private fun alternarCodec() {
        currentCodec = when (currentCodec) {
            "h265" -> "h264"
            "h264" -> if (isAv1Supported()) "av1" else "h265"
            else -> "h265"
        }
        
        codecIndicator.text = "${currentCodec.uppercase()} (Reiniciando...)"
        codecIndicator.setTextColor(android.graphics.Color.YELLOW)
        
        scope.launch {
            job?.cancelAndJoin()
            if (scrcpyTool.restartWithCodec(this@MirrorActivity, currentCodec)) {
                iniciarDecoder(surfaceView.holder.surface)
            }
        }
    }

    override fun onDestroy() {
        job?.cancel()
        scope.cancel()
        scrcpyTool.stopServer()
        super.onDestroy()
    }

    private fun iniciarDecoder(surface: Surface) {
        job?.cancel()
        job = scope.launch(Dispatchers.IO) {
            var stream: AdbStream? = null
            var decoder: MediaCodec? = null
            
            try {
                withTimeout(300_000L) { // Timeout global 5min
                    val conn = scrcpyTool.activeConnection ?: AdbManager.activeConnection ?: return@withTimeout
                    
                    // 1. Resolução Real
                    val wmSize = AdbManager.executeCommand("wm size", target = conn)
                    val match = Regex("""(\d+)x(\d+)""").find(wmSize)
                    deviceWidth = match?.groupValues?.get(1)?.toIntOrNull() ?: DEFAULT_WIDTH
                    deviceHeight = match?.groupValues?.get(2)?.toIntOrNull() ?: DEFAULT_HEIGHT

                    // 2. Conexão Temporal
                    val maxRetryTime = 5000L
                    val startTimeMs = System.currentTimeMillis()
                    while (System.currentTimeMillis() - startTimeMs < maxRetryTime && stream == null) {
                        try {
                            stream = conn.open("localabstract:scrcpy")
                        } catch (e: Exception) {
                            delay(500)
                        }
                    }
                    
                    val activeStream = stream ?: run {
                        Log.e(TAG, "Timeout Scrcpy")
                        return@withTimeout
                    }

                    // 3. Header
                    val header = ByteArray(12)
                    var headerRead = 0
                    while (headerRead < 12 && !activeStream.isClosed) {
                        val chunk = activeStream.read() ?: break
                        val toCopy = minOf(chunk.size, 12 - headerRead)
                        System.arraycopy(chunk, 0, header, headerRead, toCopy)
                        headerRead += toCopy
                    }

                    // 4. Configurar Decoder
                    val mimeType = when(currentCodec) {
                        "h265" -> "video/hevc"
                        "av1" -> "video/av01"
                        else -> "video/avc"
                    }
                    
                    val currentDecoder = MediaCodec.createDecoderByType(mimeType).apply {
                        val format = MediaFormat.createVideoFormat(mimeType, deviceWidth, deviceHeight)
                        if (android.os.Build.VERSION.SDK_INT >= 30) {
                            format.setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
                        }
                        format.setInteger(MediaFormat.KEY_OPERATING_RATE, Short.MAX_VALUE.toInt())
                        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 0)
                        configure(format, surface, null, 0)
                        start()
                    }
                    decoder = currentDecoder

                    val info = MediaCodec.BufferInfo()
                    var frames = 0
                    var lastUpdate = System.currentTimeMillis()
                    val packetBuffer = ByteArray(1024 * 1024)
                    var packetSize = 0
                    val bitrate = "${scrcpyTool.currentQuality.bitRate / 1_000_000}Mbps"

                    while (isActive && !activeStream.isClosed) {
                        val data = try { activeStream.read() } catch (e: Exception) { null } ?: break
                        
                        if (packetSize + data.size <= packetBuffer.size) {
                            System.arraycopy(data, 0, packetBuffer, packetSize, data.size)
                            packetSize += data.size
                        }

                        if (packetSize < 5) continue 

                        val inputIndex = currentDecoder.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                        if (inputIndex >= 0) {
                            currentDecoder.getInputBuffer(inputIndex)?.apply {
                                clear(); put(packetBuffer, 0, packetSize)
                                currentDecoder.queueInputBuffer(inputIndex, 0, packetSize, System.nanoTime()/1000, 0)
                            }
                            packetSize = 0
                        }

                        var outIndex = currentDecoder.dequeueOutputBuffer(info, DEQUEUE_TIMEOUT_US)
                        while (outIndex >= 0) {
                            currentDecoder.releaseOutputBuffer(outIndex, true)
                            frames++
                            outIndex = currentDecoder.dequeueOutputBuffer(info, 0)
                        }

                        val now = System.currentTimeMillis()
                        if (now - lastUpdate >= 1000) {
                            val fps = frames
                            frames = 0; lastUpdate = now
                            runOnUiThread {
                                codecIndicator.text = "${currentCodec.uppercase()} | $fps FPS | $bitrate"
                            }
                        }
                    }
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Timeout Mirror")
            } catch (e: Exception) {
                Log.e(TAG, "Erro Mirror: ${e.message}")
            } finally {
                runCatching { stream?.close() }
                runCatching { 
                    decoder?.stop()
                    decoder?.release() 
                }
            }
        }
    }
}
