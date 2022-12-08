/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngsettings.client.remote;

import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.security.ServiceTokenGenerator;
import io.harness.serializer.kryo.KryoConverterFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;

public class NGSettingsClientModule extends AbstractModule {
  private final ServiceHttpClientConfig ngSettingsClientConfig;
  private final String serviceSecret;
  private final String clientId;

  public NGSettingsClientModule(
      ServiceHttpClientConfig projectManagerClientConfig, String serviceSecret, String clientId) {
    this.ngSettingsClientConfig = projectManagerClientConfig;
    this.serviceSecret = serviceSecret;
    this.clientId = clientId;
  }

  @Provides
  private NGSettingsClientFactory ngSettingsClientFactory(KryoConverterFactory kryoConverterFactory) {
    return new NGSettingsClientFactory(
        ngSettingsClientConfig, serviceSecret, new ServiceTokenGenerator(), kryoConverterFactory, clientId);
  }

  @Override
  protected void configure() {
    bind(NGSettingsClient.class).toProvider(NGSettingsClientFactory.class).in(Scopes.SINGLETON);
  }
}
