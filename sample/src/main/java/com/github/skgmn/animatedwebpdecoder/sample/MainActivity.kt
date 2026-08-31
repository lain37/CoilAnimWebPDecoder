package com.github.skgmn.animatedwebpdecoder.sample

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.gif.onAnimationEnd
import coil3.gif.onAnimationStart
import coil3.gif.repeatCount
import coil3.request.ImageRequest
import com.github.skgmn.animatedwebpdecoder.sample.ui.theme.AnimatedWebpDecoderTheme
import com.github.skgmn.webpdecoder.AnimatedWebPDecoder

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val imageLoader = remember {
                ImageLoader.Builder(this)
                    .components {
                        add(AnimatedWebPDecoder.Factory())
                    }
                    .build()
            }
            val playOnceRequest = remember {
                ImageRequest.Builder(this)
                    .data(R.drawable.animated_webp_sample)
                    .repeatCount(0) //仅播放一次
                    .onAnimationStart {
                        Log.d(TAG, "Animation started")
                    }
                    .onAnimationEnd {
                        Log.d(TAG, "Animation ended")
                    }
                    .build()
            }
            val playThreeTimesRequest = remember {
                ImageRequest.Builder(this)
                    .data(R.drawable.animated_webp_sample_2)
                    .repeatCount(2)//播放3次
                    .build()
            }
            val repeatForeverRequest = remember {
                ImageRequest.Builder(this)
                    .data(R.drawable.animated_webp_sample_3)
                    .repeatCount(-1)//一直循环播放
                    .build()
            }
            AnimatedWebpDecoderTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colors.background)
                ) {
                    AsyncImage(
                        model = playOnceRequest,
                        imageLoader = imageLoader,
                        contentDescription = null
                    )
                    AsyncImage(
                        model = playThreeTimesRequest,
                        imageLoader = imageLoader,
                        contentDescription = null
                    )
                    AsyncImage(
                        model = repeatForeverRequest,
                        imageLoader = imageLoader,
                        contentDescription = null,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    companion object {
        private const val TAG = "AnimatedWebPDecoder"
    }
}
