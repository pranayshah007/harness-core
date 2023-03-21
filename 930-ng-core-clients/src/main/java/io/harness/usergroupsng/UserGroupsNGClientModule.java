/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.usergroupsng;

import io.harness.remote.client.ClientMode;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.security.ServiceTokenGenerator;
import io.harness.serializer.kryo.KryoConverterFactory;
import io.harness.usergroupsng.remote.UserGroupsNGClient;
import io.harness.usergroupsng.remote.UserGroupsNGHttpClientFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;

public class UserGroupsNGClientModule extends AbstractModule {
  private static UserGroupsNGClientModule instance;
  private final ServiceHttpClientConfig serviceHttpClientConfig;
  private final String serviceSecret;
  private final String clientId;

  public UserGroupsNGClientModule(
      ServiceHttpClientConfig serviceHttpClientConfig, String serviceSecret, String clientId) {
    this.serviceHttpClientConfig = serviceHttpClientConfig;
    this.serviceSecret = serviceSecret;
    this.clientId = clientId;
  }

  public static UserGroupsNGClientModule getInstance(
      ServiceHttpClientConfig serviceHttpClientConfig, String serviceSecret, String clientId) {
    if (instance == null) {
      instance = new UserGroupsNGClientModule(serviceHttpClientConfig, serviceSecret, clientId);
    }

    return instance;
  }

  @Provides
  @Singleton
  private UserGroupsNGHttpClientFactory userNGHttpClientFactory(KryoConverterFactory kryoConverterFactory) {
    return new UserGroupsNGHttpClientFactory(serviceHttpClientConfig, serviceSecret, new ServiceTokenGenerator(),
        kryoConverterFactory, clientId, ClientMode.NON_PRIVILEGED);
  }

  @Override
  protected void configure() {
    bind(UserGroupsNGClient.class).toProvider(UserGroupsNGHttpClientFactory.class).in(Scopes.SINGLETON);
  }
}
