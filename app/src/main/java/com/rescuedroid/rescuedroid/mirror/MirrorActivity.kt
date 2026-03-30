package com.rescuedroid.rescuedroid.mirror

import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Bundle
import android.view.Surface
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import com.rescuedroid.rescuedroid.adb.AdbManager
import kotlinx.coroutines.*
import android.util.Log

class MirrorActivity : ComponentActivity() {

    private lateinit var surfaceView: SurfaceView
    private var job: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        surfaceView = SurfaceView(this)
        setContentView(surfaceView)

        surfaceView.holder.addCallback(object : android.view.SurfaceHolder.Callback {
            override fun surfaceCreated(holder: android.view.SurfaceHolder) {
                iniciarDecoder(holder.surface)
            }
            override fun surfaceChanged(holder: android.view.SurfaceHolder, f: Int, w: Int, h: Int) {}
            override fun surfaceDestroyed(holder: android.view.SurfaceHolder) {
                job?.cancel()
            }
        })
    }

    private fun iniciarDecoder(surface: Surface) {
        job = CoroutineScope(Dispatchers.IO).launch {
            val conn = AdbManager.connection ?: return@launch
            try {
                // Abre o socket local do scrcpy no celular remoto
                val stream = conn.open("localabstract:scrcpy")
                
                // Configura o decodificador de vídeo H.264 (AVC)
                val decoder = MediaCodec.createDecoderByType("video/avc")
                val format = MediaFormat.createVideoFormat("video/avc", 1080, 1920)
                decoder.configure(format, surface, null, 0)
                decoder.start()

                val info = MediaCodec.BufferInfo()
                
                while (isActive && !stream.isClosed) {
                    val data = stream.read()
                    if (data.isEmpty()) continue

                    val inputIndex = decoder.dequeueInputBuffer(10000)
                    if (inputIndex >= 0) {
                        val buffer = decoder.getInputBuffer(inputIndex)
                        buffer?.clear()
                        buffer?.put(data)
                        decoder.queueInputBuffer(inputIndex, 0, data.size, System.nanoTime() / 1000, 0)
                    }

                    val outputIndex = decoder.dequeueOutputBuffer(info, 10000)
                    if (outputIndex >= 0) {
                        decoder.releaseOutputBuffer(outputIndex, true)
                    }
                }
            } catch (e: Exception) {
                Log.e("MIRROR", "Erro no stream: ${e.message}")
            }
        }
    }
}
