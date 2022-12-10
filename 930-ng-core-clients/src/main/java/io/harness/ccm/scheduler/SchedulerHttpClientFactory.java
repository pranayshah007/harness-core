/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.scheduler;

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
public class SchedulerHttpClientFactory extends AbstractHttpClientFactory implements Provider<SchedulerClient> {
  public SchedulerHttpClientFactory(ServiceHttpClientConfig dkronClientConfig, String serviceSecret,
      ServiceTokenGenerator tokenGenerator, KryoConverterFactory kryoConverterFactory, String clientId,
      ClientMode clientMode) {
    super(dkronClientConfig, serviceSecret, tokenGenerator, kryoConverterFactory, clientId, false, clientMode);
    log.info("dkronClientConfig {}", dkronClientConfig);
  }

  @Override
  public SchedulerClient get() {
    return getRetrofit().create(SchedulerClient.class);
  }
}
