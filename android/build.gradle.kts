// Same toolchain as Stone and the sibling apps, so all of them build (and are signed) the same way.
plugins {
    id("com.android.application") version "8.13.2" apply false
    kotlin("android") version "2.3.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
}
