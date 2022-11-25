/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.scheduler;

import io.harness.remote.client.ClientMode;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.security.ServiceTokenGenerator;
import io.harness.serializer.kryo.KryoConverterFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;

public class SchedulerClientModule extends AbstractModule {
  private final ServiceHttpClientConfig dkronClientConfig;
  private final String serviceSecret;
  private final String clientId;
  private final ClientMode clientMode;

  @Inject
  public SchedulerClientModule(
      ServiceHttpClientConfig dkronClientConfig, String serviceSecret, String clientId, ClientMode clientMode) {
    this.dkronClientConfig = dkronClientConfig;
    this.serviceSecret = serviceSecret;
    this.clientId = clientId;
    this.clientMode = clientMode;
  }

  @Provides
  @Singleton
  private SchedulerHttpClientFactory providesHttpClientFactory(KryoConverterFactory kryoConverterFactory) {
    return new SchedulerHttpClientFactory(this.dkronClientConfig, this.serviceSecret, new ServiceTokenGenerator(),
        kryoConverterFactory, clientId, clientMode);
  }

  @Override
  protected void configure() {
    this.bind(SchedulerClient.class).toProvider(SchedulerHttpClientFactory.class).in(Scopes.SINGLETON);
  }
}
