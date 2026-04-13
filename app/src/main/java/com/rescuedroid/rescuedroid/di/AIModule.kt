package com.rescuedroid.rescuedroid.di

import android.content.Context
import com.rescuedroid.rescuedroid.ai.GemmaIA
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AIModule {

    @Provides
    @Singleton
    fun provideGemmaIA(@ApplicationContext context: Context): GemmaIA {
        return GemmaIA(context)
    }
}
