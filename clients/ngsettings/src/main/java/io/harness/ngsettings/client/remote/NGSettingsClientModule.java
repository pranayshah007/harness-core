/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngsettings.client.remote;

import io.harness.remote.client.ClientMode;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.security.ServiceTokenGenerator;

import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.name.Names;

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
  @Named("NON_PRIVILEGED")
  @Singleton
  private NGSettingsClientFactory ngSettingsClientFactory() {
    return new NGSettingsClientFactory(
        ngSettingsClientConfig, serviceSecret, new ServiceTokenGenerator(), clientId, ClientMode.NON_PRIVILEGED);
  }

  @Provides
  @Named("PRIVILEGED")
  @Singleton
  private NGSettingsClientFactory privilegedNgSettingsClientFactory() {
    return new NGSettingsClientFactory(
        ngSettingsClientConfig, serviceSecret, new ServiceTokenGenerator(), clientId, ClientMode.PRIVILEGED);
  }

  @Override
  protected void configure() {
    bind(NGSettingsClient.class)
        .annotatedWith(Names.named(ClientMode.PRIVILEGED.name()))
        .toProvider(Key.get(NGSettingsClientFactory.class, Names.named(ClientMode.PRIVILEGED.name())))
        .in(Scopes.SINGLETON);
    bind(NGSettingsClient.class)
        .toProvider(Key.get(NGSettingsClientFactory.class, Names.named(ClientMode.NON_PRIVILEGED.name())))
        .in(Scopes.SINGLETON);
  }
}
