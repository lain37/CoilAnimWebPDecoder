package com.github.skgmn.webpdecoder

import android.graphics.Bitmap
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.request.premultipliedAlpha
import com.github.skgmn.webpdecoder.libwebp.LibWebPAnimatedDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.BufferedSource
import java.nio.ByteBuffer

class AnimatedWebPDecoder private constructor(
    private val source: ImageSource,
    private val options: Options
) : Decoder {
    @Suppress("BlockingMethodInNonBlockingContext")
    override suspend fun decode(): DecodeResult {
        val drawable = withContext(Dispatchers.IO) {
            val bytes = source.source().readByteArray()
            // really wanted to avoid whole bytes copying but it's inevitable
            // unless the size of source is provided in advance
            val byteBuffer = ByteBuffer.allocateDirect(bytes.size).put(bytes)
            val decoder = LibWebPAnimatedDecoder.create(byteBuffer, options.premultipliedAlpha)
            val bitmapPool = FrameBitmapPool()
            val firstFrame = if (decoder.hasNextFrame()) {
                val reuseBitmap = bitmapPool.getDirtyOrNull(
                    decoder.width,
                    decoder.height,
                    Bitmap.Config.ARGB_8888
                )
                decoder.decodeNextFrame(reuseBitmap)
            } else {
                null
            }
            AnimatedWebPDrawable(decoder, bitmapPool, firstFrame)
        }
        return DecodeResult(drawable.asImage(), false)
    }

    class Factory : Decoder.Factory {
        override fun create(
            result: SourceFetchResult,
            options: Options,
            imageLoader: ImageLoader
        ): Decoder? {
            return if (handles(result.source.source())) {
                AnimatedWebPDecoder(result.source, options)
            } else {
                null
            }
        }

        private fun handles(source: BufferedSource): Boolean {
            val peek = source.peek()
            if (!peek.request(WebPSupportStatus.HEADER_SIZE)) return false

            val headerBytes = peek.readByteArray(WebPSupportStatus.HEADER_SIZE)
            return WebPSupportStatus.isWebpHeader(headerBytes, 0, headerBytes.size) &&
                    WebPSupportStatus.isAnimatedWebpHeader(headerBytes, 0)
        }
    }
}
