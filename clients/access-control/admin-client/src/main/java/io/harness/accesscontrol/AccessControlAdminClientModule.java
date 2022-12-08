/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.accesscontrol;

import static io.harness.remote.client.ClientMode.NON_PRIVILEGED;
import static io.harness.remote.client.ClientMode.PRIVILEGED;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.remote.client.ClientMode;
import io.harness.security.ServiceTokenGenerator;
import io.harness.serializer.kryo.KryoConverterFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.name.Named;
import com.google.inject.name.Names;

@OwnedBy(HarnessTeam.PL)
public class AccessControlAdminClientModule extends AbstractModule {
  private static AccessControlAdminClientModule instance;
  private final AccessControlAdminClientConfiguration accessControlAdminClientConfiguration;
  private final String clientId;

  public AccessControlAdminClientModule(
      AccessControlAdminClientConfiguration accessControlAdminClientConfiguration, String clientId) {
    this.accessControlAdminClientConfiguration = accessControlAdminClientConfiguration;
    this.clientId = clientId;
  }

  public static synchronized AccessControlAdminClientModule getInstance(
      AccessControlAdminClientConfiguration accessControlAdminClientConfiguration, String clientId) {
    if (instance == null) {
      instance = new AccessControlAdminClientModule(accessControlAdminClientConfiguration, clientId);
    }
    return instance;
  }

  @Provides
  private AccessControlAdminHttpClientFactory accessControlHttpClientFactory(
      KryoConverterFactory kryoConverterFactory) {
    return new AccessControlAdminHttpClientFactory(
        accessControlAdminClientConfiguration.getAccessControlServiceConfig(),
        accessControlAdminClientConfiguration.getAccessControlServiceSecret(), new ServiceTokenGenerator(),
        kryoConverterFactory, clientId, NON_PRIVILEGED);
  }

  @Provides
  @Named("PRIVILEGED")
  private AccessControlAdminHttpClientFactory accessControlAdminHttpClientFactory(
      KryoConverterFactory kryoConverterFactory) {
    return new AccessControlAdminHttpClientFactory(
        accessControlAdminClientConfiguration.getAccessControlServiceConfig(),
        accessControlAdminClientConfiguration.getAccessControlServiceSecret(), new ServiceTokenGenerator(),
        kryoConverterFactory, clientId, PRIVILEGED);
  }

  @Override
  protected void configure() {
    bind(AccessControlAdminClient.class).toProvider(AccessControlAdminHttpClientFactory.class).in(Scopes.SINGLETON);
    bind(AccessControlAdminClient.class)
        .annotatedWith(Names.named(ClientMode.PRIVILEGED.name()))
        .toProvider(Key.get(AccessControlAdminHttpClientFactory.class, Names.named(ClientMode.PRIVILEGED.name())))
        .in(Scopes.SINGLETON);
  }
}
