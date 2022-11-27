/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.serviceaccountclient;

import io.harness.remote.client.ClientMode;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.security.ServiceTokenGenerator;
import io.harness.serializer.kryo.KryoConverterFactory;
import io.harness.serviceaccountclient.remote.ServiceAccountClient;
import io.harness.serviceaccountclient.remote.ServiceAccountClientFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.name.Names;

public class ServiceAccountClientModule extends AbstractModule {
  private final ServiceHttpClientConfig resourceGroupClientConfig;
  private final String serviceSecret;
  private final String clientId;

  public ServiceAccountClientModule(
      ServiceHttpClientConfig resourceGroupClientConfig, String serviceSecret, String clientId) {
    this.resourceGroupClientConfig = resourceGroupClientConfig;
    this.serviceSecret = serviceSecret;
    this.clientId = clientId;
  }

  @Provides
  @Singleton
  private ServiceAccountClientFactory resourceGroupHttpClientFactory(
      KryoConverterFactory kryoConverterFactory) {
    return new ServiceAccountClientFactory(resourceGroupClientConfig, serviceSecret,
        new ServiceTokenGenerator(), kryoConverterFactory, clientId, ClientMode.NON_PRIVILEGED);
  }

  @Provides
  @Named("PRIVILEGED")
  @Singleton
  private ServiceAccountClientFactory serviceAccountPrincipalClientFactory(
      KryoConverterFactory kryoConverterFactory) {
    return new ServiceAccountClientFactory(resourceGroupClientConfig, serviceSecret,
        new ServiceTokenGenerator(), kryoConverterFactory, clientId, ClientMode.PRIVILEGED);
  }

  @Override
  protected void configure() {
    bind(ServiceAccountClient.class)
        .toProvider(ServiceAccountClientFactory.class)
        .in(Scopes.SINGLETON);
    bind(ServiceAccountClient.class)
        .annotatedWith(Names.named(ClientMode.PRIVILEGED.name()))
        .toProvider(Key.get(ServiceAccountClientFactory.class, Names.named(ClientMode.PRIVILEGED.name())))
        .in(Scopes.SINGLETON);
  }
}
