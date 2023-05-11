/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.remote;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.remote.client.AbstractHttpClientFactory;
import io.harness.remote.client.ClientMode;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.security.ServiceTokenGenerator;
import io.harness.serializer.kryo.KryoConverterFactory;

import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Singleton
@Slf4j
@OwnedBy(CDC)
public class TemplateResourceClientHttpFactory
    extends AbstractHttpClientFactory implements Provider<TemplateResourceClient> {
  public TemplateResourceClientHttpFactory(ServiceHttpClientConfig templateServiceConfig, String serviceSecret,
      ServiceTokenGenerator tokenGenerator, KryoConverterFactory kryoConverterFactory, String clientId,
      boolean enableCircuitBreaker, ClientMode clientMode) {
    super(templateServiceConfig, serviceSecret, tokenGenerator, kryoConverterFactory, clientId, enableCircuitBreaker,
        clientMode);
    log.info("secretManagerConfig {}", templateServiceConfig);
  }

  public TemplateResourceClientHttpFactory(ServiceHttpClientConfig templateServiceConfig, String serviceSecret,
      ServiceTokenGenerator tokenGenerator, KryoConverterFactory kryoConverterFactory, String clientId) {
    super(templateServiceConfig, serviceSecret, tokenGenerator, kryoConverterFactory, clientId);
    log.info("secretManagerConfig {}", templateServiceConfig);
  }

  @Override
  public TemplateResourceClient get() {
    return getRetrofit().create(TemplateResourceClient.class);
  }
}
