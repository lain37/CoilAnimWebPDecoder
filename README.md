# Coil Animated WebP Decoder

An implementation of [Coil 3](https://github.com/coil-kt/coil)'s `Decoder.Factory` for animated WebP on Android API 23–27. It uses the bundled _libwebp_ sources as a native library. On API 28 and newer, prefer Coil's platform-backed animated image decoder.

The native library is built for Android devices that use 16 KB memory pages.

# Setup

In your `settings.gradle`

```gradle
dependencyResolutionManagement {
    repositories {
        maven {
            url "https://maven.pkg.github.com/lain37/CoilAnimWebPDecoder"
            credentials {
                username <Your GitHub ID>
                password <Your GitHub Personal Access Token>
            }
        }
    }
}
```

In your `app/build.gradle`

```gradle
dependencies {
    implementation "io.coil-kt.coil3:coil:3.6.0"
    implementation "io.coil-kt.coil3:coil-gif:3.6.0"
    implementation "com.github.skgmn:animatedwebpdecoder:0.1.3"
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

# Proguard rules

The AAR includes the JNI keep rules as consumer Proguard rules. No additional app-level rules are required.
