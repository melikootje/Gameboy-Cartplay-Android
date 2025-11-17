package com.gbaoperator.plugin.di

import android.content.Context
import com.gbaoperator.plugin.core.GBOperatorInterface
import com.gbaoperator.plugin.core.MockGBOperatorInterface

object AppModule {

    fun provideGBOperatorInterface(context: Context): GBOperatorInterface {
        // Use mock implementation for emulator/testing
        // Change to GBOperatorInterface(context) for real hardware
        return MockGBOperatorInterface(context)
    }
}
