/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ff;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.kryo.KryoConverterFactory;
import io.harness.remote.client.AbstractHttpClientFactory;
import io.harness.remote.client.ClientMode;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.security.ServiceTokenGenerator;

import com.google.inject.Provider;

@OwnedBy(HarnessTeam.PL)
public class FeatureFlagClientHttpFactory extends AbstractHttpClientFactory implements Provider<FeatureFlagsClient> {
  protected FeatureFlagClientHttpFactory(ServiceHttpClientConfig secretManagerConfig, String serviceSecret,
      ServiceTokenGenerator tokenGenerator, KryoConverterFactory kryoConverterFactory, String clientId,
      boolean enableCircuitBreaker, ClientMode clientMode) {
    super(secretManagerConfig, serviceSecret, tokenGenerator, kryoConverterFactory, clientId, enableCircuitBreaker,
        clientMode);
  }

  @Override
  public FeatureFlagsClient get() {
    return getRetrofit().create(FeatureFlagsClient.class);
  }
}
