/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.userng;

import io.harness.remote.client.ClientMode;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.security.ServiceTokenGenerator;
import io.harness.serializer.kryo.KryoConverterFactory;
import io.harness.userng.remote.UserNGClient;
import io.harness.userng.remote.UserNGHttpClientFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.name.Named;
import com.google.inject.name.Names;

public class UserNGClientModule extends AbstractModule {
  private static UserNGClientModule instance;
  private final ServiceHttpClientConfig serviceHttpClientConfig;
  private final String serviceSecret;
  private final String clientId;

  public UserNGClientModule(ServiceHttpClientConfig serviceHttpClientConfig, String serviceSecret, String clientId) {
    this.serviceHttpClientConfig = serviceHttpClientConfig;
    this.serviceSecret = serviceSecret;
    this.clientId = clientId;
  }

  public static UserNGClientModule getInstance(
      ServiceHttpClientConfig serviceHttpClientConfig, String serviceSecret, String clientId) {
    if (instance == null) {
      instance = new UserNGClientModule(serviceHttpClientConfig, serviceSecret, clientId);
    }

    return instance;
  }

  @Provides
  private UserNGHttpClientFactory userNGHttpClientFactory(KryoConverterFactory kryoConverterFactory) {
    return new UserNGHttpClientFactory(serviceHttpClientConfig, serviceSecret, new ServiceTokenGenerator(),
        kryoConverterFactory, clientId, ClientMode.NON_PRIVILEGED);
  }

  @Provides
  @Named("PRIVILEGED")
  private UserNGHttpClientFactory privilegedUserNGClientFactory() {
    return new UserNGHttpClientFactory(
        serviceHttpClientConfig, serviceSecret, new ServiceTokenGenerator(), null, clientId, ClientMode.PRIVILEGED);
  }

  @Override
  protected void configure() {
    bind(UserNGClient.class).toProvider(UserNGHttpClientFactory.class).in(Scopes.SINGLETON);
    bind(UserNGClient.class)
        .annotatedWith(Names.named(ClientMode.PRIVILEGED.name()))
        .toProvider(privilegedUserNGClientFactory())
        .in(Scopes.SINGLETON);
  }
}
