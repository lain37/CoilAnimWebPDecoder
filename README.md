# Coil Animated WebP Decoder

An implementation of [Coil 3](https://github.com/coil-kt/coil)'s `Decoder.Factory` for animated WebP on Android API 23–27. It uses the bundled _libwebp_ sources as a native library. On API 28 and newer, prefer Coil's platform-backed animated image decoder.

The native library is built for Android devices that use 16 KB memory pages.

# Setup

In your `settings.gradle`

```gradle
dependencyResolutionManagement {
    repositories {
    mavenCentral()
        maven {url 'https://jitpack.io'}
    }
}
```

In your `app/build.gradle`

```gradle
dependencies {
    implementation 'com.github.lain37:CoilAnimWebPDecoder:Tag'
}
```

# How to use

Add `AnimatedWebPDecoder.Factory` to your `ImageLoader.Builder` on API 23–27. Coil's `coil-gif` artifact provides the platform decoder for newer Android versions.

```kotlin
val imageLoader = ImageLoader.Builder(context)
    .components {
        if (SDK_INT >= 28) {
            add(AnimatedImageDecoder.Factory())
        } else {
            add(AnimatedWebPDecoder.Factory())
        }
    }
    .build()
```

# Playback count

Use Coil's `repeatCount` request option to control animated WebP playback on every supported Android version.
The value is the number of repeats after the first play.

```kotlin
import coil3.gif.repeatCount
import coil3.load

// Play once.
imageView.load(url) {
    repeatCount(0)
}

// Play three times in total.
imageView.load(url) {
    repeatCount(2)
}

// Repeat forever.
imageView.load(url) {
    repeatCount(-1)
}
```

# Animation callbacks

Use Coil's standard animation callbacks to observe playback. `onAnimationEnd` is invoked when a finite animation finishes.
An infinitely repeating animation does not finish naturally.

```kotlin
import coil3.gif.onAnimationEnd
import coil3.gif.onAnimationStart
import coil3.gif.repeatCount
import coil3.load

imageView.load(url) {
    repeatCount(0)
    onAnimationStart {
        // Animation started.
    }
    onAnimationEnd {
        // The animation finished playing once.
    }
}
```

# Proguard rules

The AAR includes the JNI keep rules as consumer Proguard rules. No additional app-level rules are required.
