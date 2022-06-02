package io.harness.notifications;

import io.harness.remote.client.ClientMode;
import io.harness.remote.client.ServiceHttpClientConfig;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Scopes;

public class NotificationResourceClientModule extends AbstractModule {
  private final ServiceHttpClientConfig ngManagerClientConfig;
  private final String serviceSecret;
  private final String clientId;
  private final ClientMode clientMode;

  @Inject
  public NotificationResourceClientModule(
      ServiceHttpClientConfig ngManagerClientConfig, String serviceSecret, String clientId, ClientMode clientMode) {
    this.ngManagerClientConfig = ngManagerClientConfig;
    this.serviceSecret = serviceSecret;
    this.clientId = clientId;
    this.clientMode = clientMode;
  }

  @Inject
  public NotificationResourceClientModule(
      ServiceHttpClientConfig ngManagerClientConfig, String serviceSecret, String clientId) {
    this.ngManagerClientConfig = ngManagerClientConfig;
    this.serviceSecret = serviceSecret;
    this.clientId = clientId;
    this.clientMode = ClientMode.NON_PRIVILEGED;
  }

  @Override
  protected void configure() {
    this.bind(NotificationResourceClient.class)
        .toProvider(NotificationResourceClientFactory.class)
        .in(Scopes.SINGLETON);
  }
}
