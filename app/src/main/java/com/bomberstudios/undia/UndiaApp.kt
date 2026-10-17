package com.bomberstudios.undia

import android.app.Application
import android.os.Build
import com.onyx.android.sdk.rx.RxManager
import org.lsposed.hiddenapibypass.HiddenApiBypass

class UndiaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenApiBypass.addHiddenApiExemptions("")
        }
        RxManager.Builder.initAppContext(this)
    }
}
