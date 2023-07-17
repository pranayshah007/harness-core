package io.harness.delegate.app.modules;

import io.harness.dmsclient.DelegateAgentDMSClient;
import io.harness.dmsclient.DelegateAgentDMSClientFactory;

import com.google.inject.AbstractModule;

public class DelegateDMSClientModule extends AbstractModule {
  @Override
  protected void configure() {
    bind(DelegateAgentDMSClient.class).toProvider(DelegateAgentDMSClientFactory.class);
  }
}
