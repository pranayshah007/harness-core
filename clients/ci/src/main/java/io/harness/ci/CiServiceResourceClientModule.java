/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ci.remote.CiServiceResourceClient;
import io.harness.ci.remote.CiServiceResourceClientFactory;
import io.harness.ci.remote.NoOpCiResourceClient;
import io.harness.remote.client.ClientMode;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.security.ServiceTokenGenerator;
import io.harness.serializer.kryo.KryoConverterFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;

@OwnedBy(HarnessTeam.PIPELINE)
public class CiServiceResourceClientModule extends AbstractModule {
  private final ServiceHttpClientConfig serviceHttpClientConfig;
  private final String serviceSecret;
  private final String clientId;
  private final boolean containerStepConfigureWithCi;

  public CiServiceResourceClientModule(ServiceHttpClientConfig serviceHttpClientConfig, String serviceSecret,
      String clientId, boolean containerStepConfigureWithCi) {
    this.serviceHttpClientConfig = serviceHttpClientConfig;
    this.serviceSecret = serviceSecret;
    this.clientId = clientId;
    this.containerStepConfigureWithCi = containerStepConfigureWithCi;
  }

  @Provides
  @Singleton
  private CiServiceResourceClientFactory privilegedCiServiceResourceClientFactory(
      KryoConverterFactory kryoConverterFactory) {
    return new CiServiceResourceClientFactory(serviceHttpClientConfig, serviceSecret, new ServiceTokenGenerator(),
        kryoConverterFactory, clientId, ClientMode.PRIVILEGED);
  }

  @Override
  protected void configure() {
    if (containerStepConfigureWithCi) {
      bind(CiServiceResourceClient.class).toProvider(CiServiceResourceClientFactory.class).in(Scopes.SINGLETON);
    } else {
      bind(CiServiceResourceClient.class).to(NoOpCiResourceClient.class);
    }
  }
}
