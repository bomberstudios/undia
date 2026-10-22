package org.naranjanaranja.undia

import android.app.Application
import com.onyx.android.sdk.rx.RxManager

class UndiaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        RxManager.Builder.initAppContext(this)
    }
}
