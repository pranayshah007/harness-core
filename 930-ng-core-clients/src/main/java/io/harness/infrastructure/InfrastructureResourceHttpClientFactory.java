/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.infrastructure;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.remote.client.AbstractHttpClientFactory;
import io.harness.remote.client.ClientMode;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.security.ServiceTokenGenerator;
import io.harness.serializer.kryo.KryoConverterFactory;

import com.google.inject.Provider;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CDC)
public class InfrastructureResourceHttpClientFactory
    extends AbstractHttpClientFactory implements Provider<InfrastructureResourceClient> {
  public InfrastructureResourceHttpClientFactory(ServiceHttpClientConfig ngManagerConfig, String serviceSecret,
      ServiceTokenGenerator tokenGenerator, KryoConverterFactory kryoConverterFactory, String clientId,
      ClientMode clientMode) {
    super(ngManagerConfig, serviceSecret, tokenGenerator, kryoConverterFactory, clientId, false, clientMode);
    log.info("secretManagerConfig {}", ngManagerConfig);
  }

  @Override
  public InfrastructureResourceClient get() {
    return getRetrofit().create(InfrastructureResourceClient.class);
  }
}
