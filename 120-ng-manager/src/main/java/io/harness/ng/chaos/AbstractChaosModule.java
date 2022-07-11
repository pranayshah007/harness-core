package io.harness.ng.chaos;

import io.harness.ng.chaos.client.ChaosClientModule;
import io.harness.remote.client.ServiceHttpClientConfig;

import com.google.inject.AbstractModule;

public abstract class AbstractChaosModule extends AbstractModule {
  @Override
  protected void configure() {
    install(ChaosModule.getInstance());
    install(new ChaosClientModule(chaosClientConfig(), serviceSecret(), clientId()));
  }

  public abstract ServiceHttpClientConfig chaosClientConfig();

  public abstract String serviceSecret();

  public abstract String clientId();
}
