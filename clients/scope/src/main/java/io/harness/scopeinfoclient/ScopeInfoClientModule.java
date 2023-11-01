/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.scopeinfoclient;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.remote.client.ClientMode;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.scopeinfoclient.remote.ScopeInfoClient;
import io.harness.scopeinfoclient.remote.ScopeInfoClientHttpFactory;
import io.harness.security.ServiceTokenGenerator;

import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.name.Names;

@OwnedBy(PL)
public class ScopeInfoClientModule extends AbstractModule {
  private final ServiceHttpClientConfig scopeInfoClientConfig;
  private final String serviceSecret;
  private final String clientId;

  public ScopeInfoClientModule(ServiceHttpClientConfig scopeInfoClientConfig, String serviceSecret, String clientId) {
    this.scopeInfoClientConfig = scopeInfoClientConfig;
    this.serviceSecret = serviceSecret;
    this.clientId = clientId;
  }

  @Provides
  @Singleton
  private ScopeInfoClientHttpFactory scopeInfoClientHttpFactory() {
    return new ScopeInfoClientHttpFactory(
        scopeInfoClientConfig, serviceSecret, new ServiceTokenGenerator(), clientId, ClientMode.NON_PRIVILEGED);
  }

  @Provides
  @Named("PRIVILEGED")
  @Singleton
  private ScopeInfoClientHttpFactory privilegedScopeInfoClientHttpFactory() {
    return new ScopeInfoClientHttpFactory(
        scopeInfoClientConfig, serviceSecret, new ServiceTokenGenerator(), clientId, ClientMode.PRIVILEGED);
  }

  @Override
  protected void configure() {
    bind(ScopeInfoClient.class)
        .annotatedWith(Names.named(ClientMode.PRIVILEGED.name()))
        .toProvider(Key.get(ScopeInfoClientHttpFactory.class, Names.named(ClientMode.PRIVILEGED.name())))
        .in(Scopes.SINGLETON);
    bind(ScopeInfoClient.class).toProvider(ScopeInfoClientHttpFactory.class).in(Scopes.SINGLETON);
  }
}
