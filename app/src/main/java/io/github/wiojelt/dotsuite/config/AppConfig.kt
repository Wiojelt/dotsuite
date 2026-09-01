package io.github.wiojelt.dotsuite.config

import io.github.wiojelt.dotsuite.BuildConfig

/** Product identity. Version values come from the Gradle build. */
object AppConfig {
    const val APP_NAME: String = "DotSuite"
    val VERSION_NAME: String = BuildConfig.VERSION_NAME
    val VERSION_CODE: Int = BuildConfig.VERSION_CODE
}
