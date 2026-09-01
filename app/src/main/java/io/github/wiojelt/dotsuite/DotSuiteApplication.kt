package io.github.wiojelt.dotsuite

import android.app.Activity
import android.app.Application
import android.os.Bundle
import io.github.wiojelt.dotsuite.diagnostics.RecentDiagnostics

class DotSuiteApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        RecentDiagnostics.init(this)
        RecentDiagnostics.record("app", "started ${BuildConfig.VERSION_NAME}")
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try { RecentDiagnostics.failure("uncaught", error); RecentDiagnostics.flush() }
            finally {
                if (previous != null) previous.uncaughtException(thread, error)
                else { android.os.Process.killProcess(android.os.Process.myPid()); kotlin.system.exitProcess(10) }
            }
        }
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private fun event(activity: Activity, state: String) = RecentDiagnostics.record("screen.${activity.javaClass.simpleName}", state)
            override fun onActivityCreated(a: Activity, b: Bundle?) { event(a, "created") }
            override fun onActivityResumed(a: Activity) { event(a, "resumed") }
            override fun onActivityStopped(a: Activity) { event(a, "stopped") }
            override fun onActivityDestroyed(a: Activity) { event(a, "destroyed") }
            override fun onActivityStarted(a: Activity) {}
            override fun onActivityPaused(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
        })
    }
}
