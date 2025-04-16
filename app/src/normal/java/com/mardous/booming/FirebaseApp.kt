package com.mardous.booming

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase

private val firebaseCrashlytics: FirebaseCrashlytics by lazy { Firebase.crashlytics }

fun recordException(throwable: Throwable) {
    firebaseCrashlytics.recordException(throwable)
}