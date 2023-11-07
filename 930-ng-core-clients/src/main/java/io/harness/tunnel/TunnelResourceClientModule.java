/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.tunnel;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.annotations.dev.OwnedBy;
import io.harness.remote.client.ClientMode;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.security.ServiceTokenGenerator;
import io.harness.serializer.kryo.KryoConverterFactory;

import com.google.inject.*;

@OwnedBy(CI)
public class TunnelResourceClientModule extends AbstractModule {
  private final ServiceHttpClientConfig ngManagerClientConfig;
  private final String serviceSecret;
  private final String clientId;
  private final ClientMode clientMode;

  @Inject
  public TunnelResourceClientModule(
      ServiceHttpClientConfig ngManagerClientConfig, String serviceSecret, String clientId) {
    this.ngManagerClientConfig = ngManagerClientConfig;
    this.serviceSecret = serviceSecret;
    this.clientId = clientId;
    this.clientMode = ClientMode.NON_PRIVILEGED;
  }

  @Inject
  public TunnelResourceClientModule(
      ServiceHttpClientConfig ngManagerClientConfig, String serviceSecret, String clientId, ClientMode clientMode) {
    this.ngManagerClientConfig = ngManagerClientConfig;
    this.serviceSecret = serviceSecret;
    this.clientId = clientId;
    this.clientMode = clientMode;
  }

  @Provides
  @Singleton
  private TunnelResourceClientHttpFactory secretManagerHttpClientFactory(KryoConverterFactory kryoConverterFactory) {
    return new TunnelResourceClientHttpFactory(this.ngManagerClientConfig, this.serviceSecret,
        new ServiceTokenGenerator(), kryoConverterFactory, clientId, clientMode);
  }

  @Override
  protected void configure() {
    this.bind(TunnelResourceClient.class).toProvider(TunnelResourceClientHttpFactory.class).in(Scopes.SINGLETON);
  }
}
