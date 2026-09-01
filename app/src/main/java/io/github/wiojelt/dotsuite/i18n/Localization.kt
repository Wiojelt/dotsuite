package io.github.wiojelt.dotsuite.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

private val AppStrings = Strings()

@Composable
@ReadOnlyComposable
fun strings(): Strings = AppStrings
