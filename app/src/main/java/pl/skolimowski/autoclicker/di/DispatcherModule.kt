package pl.skolimowski.autoclicker.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import pl.skolimowski.autoclicker.ui.DefaultDispatchers
import pl.skolimowski.autoclicker.ui.DispatcherProvider

@Module
@InstallIn(SingletonComponent::class)
abstract class DispatcherModule {
    @Binds
    abstract fun provideDispatcherProvider(dispatchers: DefaultDispatchers): DispatcherProvider
}