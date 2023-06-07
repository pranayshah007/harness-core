package io.harness.app;

import com.google.inject.AbstractModule;
import io.harness.serializer.KryoModule;

public class MockDelegateAppModule extends AbstractModule {
    @Override
    protected void configure() {
        super.configure();
        install(KryoModule.getInstance());
        install(new MockDelegateKryoModule());
    }
}
