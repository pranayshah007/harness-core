package io.harness.app;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import io.harness.govern.ProviderModule;
import io.harness.serializer.KryoRegistrar;
import java.util.Set;

public class MockDelegateKryoModule extends ProviderModule {
    @Provides
    @Singleton
    Set<Class<? extends KryoRegistrar> > registrars() {
        return ImmutableSet.<Class<? extends KryoRegistrar> >builder().addAll(MockDelegateRegistrars.kryoRegistrars).build();
    }
}
