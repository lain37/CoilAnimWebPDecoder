package com.github.skgmn.webpdecoder

import android.graphics.*
import android.graphics.drawable.Drawable
import android.os.SystemClock
import androidx.annotation.GuardedBy
import androidx.vectordrawable.graphics.drawable.Animatable2Compat
import com.github.skgmn.webpdecoder.libwebp.LibWebPAnimatedDecoder
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ClosedSendChannelException

@OptIn(
    DelicateCoroutinesApi::class,
    ExperimentalCoroutinesApi::class
)
internal class AnimatedWebPDrawable(
    private val decoder: LibWebPAnimatedDecoder,
    @GuardedBy("bitmapPool")
    private val bitmapPool: FrameBitmapPool,
    firstFrame: LibWebPAnimatedDecoder.DecodeFrameResult? = null,
    private val repeatCount: Int = REPEAT_INFINITE
) : Drawable(), Animatable2Compat {
    private val paint by lazy(LazyThreadSafetyMode.NONE) { Paint(Paint.FILTER_BITMAP_FLAG) }
    private var decodeChannel: Channel<LibWebPAnimatedDecoder.DecodeFrameResult>? = null
    private var decodeJob: Job? = null
    private var frameWaitingJob: Job? = null
    private var pendingDecodeResult: LibWebPAnimatedDecoder.DecodeFrameResult? = null
    private var nextFrame = false
    private var isRunning = false
    private val callbacks = mutableListOf<Animatable2Compat.AnimationCallback>()

    // currentBitmap should be set right after Canvas.drawBitmap() is called
    // since it returns the existing value to the frame bitmap pool.
    private var currentDecodingResult = firstFrame
        set(value) {
            if (field !== value) {
                field?.bitmap?.let {
                    // put the bitmap to the pool after it is detached from RenderNode
                    // simply by using handler
                    // unless this spam log may be appeared:
                    //   Called reconfigure on a bitmap that is in use! This may cause graphical corruption!
                    scheduleSelf({
                        synchronized(bitmapPool) {
                            bitmapPool.put(it)
                        }
                    }, 0)
                }
                field = value
            }
        }

    private var queueTime = -1L
    private var queueDelay = INITIAL_QUEUE_DELAY_HEURISTIC
    private var queueDelayWindow = ArrayDeque(listOf(INITIAL_QUEUE_DELAY_HEURISTIC))
    private var queueDelaySum = INITIAL_QUEUE_DELAY_HEURISTIC

    private val nextFrameScheduler = {
        nextFrame = true
        queueTime = SystemClock.uptimeMillis()
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        val time = SystemClock.uptimeMillis()
        if (queueTime >= 0) {
            val currentDelay = time - queueTime
            addQueueDelay(currentDelay)
            queueTime = -1
        }

        val channel = decodeChannel
        if (!isRunning || !nextFrame || channel == null) {
            currentDecodingResult?.bitmap?.let {
                canvas.drawBitmap(it, null, bounds, paint)
            }
            return
        }

        nextFrame = false
        val decodeFrameResult = pendingDecodeResult?.also {
            pendingDecodeResult = null
        } ?: channel.tryReceive().getOrNull()
        if (decodeFrameResult == null) {
            currentDecodingResult?.bitmap?.let {
                canvas.drawBitmap(it, null, bounds, paint)
            }
            if (decodeJob?.isActive != true && channel.isEmpty) {
                stop()
            } else if (frameWaitingJob?.isActive != true) {
                frameWaitingJob = GlobalScope.launch(Dispatchers.Main.immediate) {
                    try {
                        pendingDecodeResult = channel.receive()
                        nextFrame = true
                        queueTime = SystemClock.uptimeMillis()
                        invalidateSelf()
                    } catch (e: ClosedReceiveChannelException) {
                        // failed to receive next frame
                    } finally {
                        frameWaitingJob = null
                    }
                }
            }
        } else {
            canvas.drawBitmap(decodeFrameResult.bitmap, null, bounds, paint)
            currentDecodingResult = decodeFrameResult
            if (decodeJob?.isActive != true && channel.isEmpty) {
                stop()
            } else {
                scheduleSelf(
                    nextFrameScheduler,
                    time + (decodeFrameResult.frameLengthMs - queueDelay).coerceAtLeast(0)
                )
            }
        }
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int {
        return PixelFormat.TRANSLUCENT
    }

    override fun getIntrinsicWidth(): Int {
        return decoder.width
    }

    override fun getIntrinsicHeight(): Int {
        return decoder.height
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun start() {
        if (isRunning) return
        isRunning = true

        callbacks.forEach { it.onAnimationStart(this) }

        val channel = Channel<LibWebPAnimatedDecoder.DecodeFrameResult>(
            capacity = 1,
            onUndeliveredElement = {
                synchronized(bitmapPool) { bitmapPool.put(it.bitmap) }
            }
        ).also {
            decodeChannel = it
        }
        nextFrame = true
        invalidateSelf()
        decodeJob = GlobalScope.launch(Dispatchers.Default) {
            val playCount = when (repeatCount) {
                ENCODED_LOOP_COUNT -> decoder.loopCount.toLong()
                REPEAT_INFINITE -> 0L
                else -> repeatCount.toLong() + 1L
            }
            var playedCount = 0L
            while (isActive && (playCount == 0L || playedCount < playCount)) {
                decoder.reset()
                while (isActive && decoder.hasNextFrame()) {
                    val reuseBitmap = synchronized(bitmapPool) {
                        bitmapPool.getDirtyOrNull(
                            decoder.width,
                            decoder.height,
                            Bitmap.Config.ARGB_8888
                        )
                    }
                    val result = decoder.decodeNextFrame(reuseBitmap)
                    if (result == null || result.bitmap !== reuseBitmap) {
                        reuseBitmap?.let {
                            synchronized(bitmapPool) { bitmapPool.put(it) }
                        }
                    }
                    if (!isActive) {
                        break
                    }
                    if (result == null) {
                        continue
                    }
                    try {
                        channel.send(result)
                    } catch (e: ClosedSendChannelException) {
                        synchronized(bitmapPool) {
                            bitmapPool.put(result.bitmap)
                        }
                        break
                    }
                }
                ++playedCount
            }
        }
    }

    override fun stop() {
        if (!isRunning) return
        isRunning = false

        decodeJob?.cancel()
        decodeJob = null

        decodeChannel?.close()
        decodeChannel = null

        frameWaitingJob?.cancel()
        frameWaitingJob = null

        nextFrame = false
        unscheduleSelf(nextFrameScheduler)

        callbacks.forEach { it.onAnimationEnd(this) }
    }

    override fun isRunning(): Boolean {
        return isRunning
    }

    override fun registerAnimationCallback(callback: Animatable2Compat.AnimationCallback) {
        if (callback !in callbacks) {
            callbacks += callback
        }
    }

    override fun unregisterAnimationCallback(callback: Animatable2Compat.AnimationCallback): Boolean {
        return if (callback in callbacks) {
            callbacks -= callback
            true
        } else {
            false
        }
    }

    override fun clearAnimationCallbacks() {
        callbacks.clear()
    }

    private fun addQueueDelay(delay: Long) {
        val coercedDelay = delay.coerceAtMost(MAX_QUEUE_DELAY_HEURISTIC)
        queueDelayWindow.addLast(coercedDelay)
        queueDelaySum += coercedDelay
        while (queueDelayWindow.size > QUEUE_DELAY_WINDOW_COUNT) {
            queueDelaySum -= queueDelayWindow.removeFirst()
        }
        queueDelay = (queueDelaySum / queueDelayWindow.size).coerceAtMost(MAX_QUEUE_DELAY_HEURISTIC)
    }

    companion object {
        private const val ENCODED_LOOP_COUNT = -2
        private const val REPEAT_INFINITE = -1
        private const val INITIAL_QUEUE_DELAY_HEURISTIC = 11L
        private const val MAX_QUEUE_DELAY_HEURISTIC = 21L
        private const val QUEUE_DELAY_WINDOW_COUNT = 20
    }
}

internal class FrameBitmapPool {
    private val bitmaps = ArrayDeque<Bitmap>()

    fun getDirtyOrNull(width: Int, height: Int, config: Bitmap.Config): Bitmap? {
        while (bitmaps.isNotEmpty()) {
            val bitmap = bitmaps.removeFirst()
            if (!bitmap.isRecycled &&
                bitmap.width == width &&
                bitmap.height == height &&
                bitmap.config == config
            ) {
                return bitmap
            }
            if (!bitmap.isRecycled) bitmap.recycle()
        }
        return null
    }

    fun put(bitmap: Bitmap) {
        if (bitmap.isRecycled) return
        if (bitmaps.size < MAX_SIZE) {
            bitmaps.addLast(bitmap)
        } else {
            bitmap.recycle()
        }
    }

    private companion object {
        const val MAX_SIZE = 3
    }
}
