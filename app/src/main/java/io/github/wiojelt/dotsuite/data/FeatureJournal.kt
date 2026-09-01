package io.github.wiojelt.dotsuite.data

import android.content.Context
import io.github.wiojelt.dotsuite.diagnostics.RecentDiagnostics

/** Bounded local audit: operation names/results only, never carrier text, PINs or camera media. */
object FeatureJournal {
    fun record(context: Context, operation: String, result: String) {
        RecentDiagnostics.init(context)
        RecentDiagnostics.record(operation, result,
            if (result.contains("fail", true) || result.contains("unavailable", true)) "WARN" else "INFO")
    }

    fun read(context: Context): String { RecentDiagnostics.init(context); return RecentDiagnostics.text() }
}
