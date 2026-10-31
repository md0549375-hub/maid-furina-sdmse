package eu.darken.sdmse.furina.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import eu.darken.sdmse.furina.core.bridge.CleanerGateway
import eu.darken.sdmse.furina.core.bridge.SdMaidCleanerGateway

@Module
@InstallIn(SingletonComponent::class)
abstract class FurinaModule {
    @Binds
    abstract fun bindCleanerGateway(impl: SdMaidCleanerGateway): CleanerGateway
}
