/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.governanceAccount;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorResourceClient;
import io.harness.connector.ConnectorResourceHttpClientFactory;
import io.harness.remote.client.ClientMode;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.security.ServiceTokenGenerator;
import io.harness.serializer.kryo.KryoConverterFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
@OwnedBy(CE)
public class GovernanceConnectorClientModule extends AbstractModule {
  private final ServiceHttpClientConfig httpClientConfig;
  private final String serviceSecret;
  private final String clientId;
  private final ClientMode clientMode;
  @Inject
  public GovernanceConnectorClientModule(
      ServiceHttpClientConfig httpClientConfig, String serviceSecret, String clientId, ClientMode clientMode) {
    this.httpClientConfig = httpClientConfig;
    this.serviceSecret = serviceSecret;
    this.clientId = clientId;
    this.clientMode = clientMode;
  }

  @Provides
  @Singleton
  private GovernanceConnectorHttpClientFactory providesHttpClientFactory(KryoConverterFactory kryoConverterFactory) {
    return new GovernanceConnectorHttpClientFactory(this.httpClientConfig, this.serviceSecret,
        new ServiceTokenGenerator(), kryoConverterFactory, clientId, clientMode);
  }

  @Override
  protected void configure() {
    this.bind(GovernanceConnectorClient.class)
        .toProvider(GovernanceConnectorHttpClientFactory.class)
        .in(Scopes.SINGLETON);
  }
}
