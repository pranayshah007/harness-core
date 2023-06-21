package io.harness.app;



import com.google.inject.AbstractModule;
import io.harness.delegate.app.modules.common.DelegateTokensModule;
import io.harness.delegate.app.modules.platform.DelegateExecutorsModule;
import io.harness.delegate.configuration.DelegateConfiguration;
import io.harness.delegate.service.DelegateAgentService;

import io.harness.delegate.service.core.client.DelegateCoreManagerClient;
import io.harness.serializer.KryoModule;
import io.harness.version.VersionModule;
import lombok.RequiredArgsConstructor;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClient;

import java.time.Clock;

@RequiredArgsConstructor
public class MockDelegateAppModule extends AbstractModule {

    private final DelegateConfiguration configuration;
    @Override
    protected void configure() {
        super.configure();
        bind(DelegateConfiguration.class).toInstance(configuration);
        install(new DelegateTokensModule(configuration));
        install(KryoModule.getInstance());
        install(new MockDelegateKryoModule());
        install(
                new DelegateExecutorsModule(configuration.isDynamicHandlingOfRequestEnabled())); // Check if some can be removed
        install(VersionModule.getInstance());
        bind(Clock.class).toInstance(Clock.systemUTC());
        bind(DelegateAgentService.class).to(MockDelegateAgentService.class);
        bind(AsyncHttpClient.class).to(DefaultAsyncHttpClient.class);
        bind(DelegateCoreManagerClient.class).toProvider(MockDelegateCoreManagerClientFactory.class);


    }
}
