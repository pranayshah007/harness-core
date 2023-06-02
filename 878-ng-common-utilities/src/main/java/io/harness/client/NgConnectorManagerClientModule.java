/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.client;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.security.ServiceTokenGenerator;
import io.harness.serializer.kryo.KryoConverterFactory;
import io.harness.services.NgConnectorManagerClientService;
import io.harness.services.NgConnectorManagerClientServiceImpl;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;

@OwnedBy(HarnessTeam.PL)
public class NgConnectorManagerClientModule extends AbstractModule {
  private final ServiceHttpClientConfig managerClientConfig;
  private final String managerServiceSecret;

  public NgConnectorManagerClientModule(ServiceHttpClientConfig managerClientConfig, String managerServiceSecret) {
    this.managerClientConfig = managerClientConfig;
    this.managerServiceSecret = managerServiceSecret;
  }

  @Provides
  @Singleton
  private NgConnectorManagerClientFactory connectorManagerClientFactory(KryoConverterFactory kryoConverterFactory) {
    return new NgConnectorManagerClientFactory(
        managerClientConfig, managerServiceSecret, new ServiceTokenGenerator(), kryoConverterFactory);
  }

  @Override
  protected void configure() {
    bind(NgConnectorManagerClient.class).toProvider(NgConnectorManagerClientFactory.class).in(Scopes.SINGLETON);
    bind(NgConnectorManagerClientService.class).to(NgConnectorManagerClientServiceImpl.class);
  }
}
