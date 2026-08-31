package com.github.skgmn.animatedwebpdecoder.sample

import android.os.Bundle
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
            AnimatedWebpDecoderTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colors.background)
                ) {
                    AsyncImage(
                        model = R.drawable.animated_webp_sample,
                        imageLoader = imageLoader,
                        contentDescription = null
                    )
                    AsyncImage(
                        model = R.drawable.animated_webp_sample_2,
                        imageLoader = imageLoader,
                        contentDescription = null
                    )
                    AsyncImage(
                        model = R.drawable.animated_webp_sample_3,
                        imageLoader = imageLoader,
                        contentDescription = null,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
