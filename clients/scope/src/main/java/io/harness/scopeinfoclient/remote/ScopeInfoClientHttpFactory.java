/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.scopeinfoclient.remote;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.remote.client.AbstractHttpClientFactory;
import io.harness.remote.client.ClientMode;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.security.ServiceTokenGenerator;

import com.google.inject.Provider;
import com.google.inject.Singleton;

@Singleton
@OwnedBy(PL)
public class ScopeInfoClientHttpFactory extends AbstractHttpClientFactory implements Provider<ScopeInfoClient> {
  public ScopeInfoClientHttpFactory(ServiceHttpClientConfig ngManagerConfig, String serviceSecret,
      ServiceTokenGenerator tokenGenerator, String clientId, ClientMode clientMode) {
    super(ngManagerConfig, serviceSecret, tokenGenerator, null, clientId, false, clientMode);
  }

  @Override
  public ScopeInfoClient get() {
    return getRetrofit().create(ScopeInfoClient.class);
  }
}
