package com.videoplayer.akii

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

class MyVideoPlayerApplication : Application(), ViewModelStoreOwner {
    override val viewModelStore: ViewModelStore by lazy {
        ViewModelStore()
    }
}