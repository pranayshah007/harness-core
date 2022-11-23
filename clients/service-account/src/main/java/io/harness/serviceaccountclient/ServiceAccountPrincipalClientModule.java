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
import io.harness.serviceaccountclient.remote.ServiceAccountPrincipalClient;
import io.harness.serviceaccountclient.remote.ServiceAccountPrincipalClientFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.name.Names;

public class ServiceAccountPrincipalClientModule extends AbstractModule {
  private final ServiceHttpClientConfig resourceGroupClientConfig;
  private final String serviceSecret;
  private final String clientId;

  public ServiceAccountPrincipalClientModule(
      ServiceHttpClientConfig resourceGroupClientConfig, String serviceSecret, String clientId) {
    this.resourceGroupClientConfig = resourceGroupClientConfig;
    this.serviceSecret = serviceSecret;
    this.clientId = clientId;
  }

  @Provides
  @Singleton
  private ServiceAccountPrincipalClientFactory resourceGroupHttpClientFactory(
      KryoConverterFactory kryoConverterFactory) {
    return new ServiceAccountPrincipalClientFactory(resourceGroupClientConfig, serviceSecret,
        new ServiceTokenGenerator(), kryoConverterFactory, clientId, ClientMode.NON_PRIVILEGED);
  }

  @Provides
  @Named("PRIVILEGED")
  @Singleton
  private ServiceAccountPrincipalClientFactory serviceAccountPrincipalClientFactory(
      KryoConverterFactory kryoConverterFactory) {
    return new ServiceAccountPrincipalClientFactory(resourceGroupClientConfig, serviceSecret,
        new ServiceTokenGenerator(), kryoConverterFactory, clientId, ClientMode.PRIVILEGED);
  }

  @Override
  protected void configure() {
    bind(ServiceAccountPrincipalClient.class)
        .toProvider(ServiceAccountPrincipalClientFactory.class)
        .in(Scopes.SINGLETON);
    bind(ServiceAccountPrincipalClient.class)
        .annotatedWith(Names.named(ClientMode.PRIVILEGED.name()))
        .toProvider(Key.get(ServiceAccountPrincipalClientFactory.class, Names.named(ClientMode.PRIVILEGED.name())))
        .in(Scopes.SINGLETON);
  }
}
