/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.hsqs.client;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.hsqs.client.api.HsqsClientService;
import io.harness.hsqs.client.api.impl.HsqsClientServiceImpl;
import io.harness.hsqs.client.model.HsqsClientConstants;
import io.harness.hsqs.client.model.QueueServiceClientConfig;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.security.ServiceTokenGenerator;
import io.harness.serializer.kryo.KryoConverterFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;

@OwnedBy(PIPELINE)
@FieldDefaults(level = AccessLevel.PRIVATE)
@Singleton
public class HsqsServiceClientModule extends AbstractModule {
  private final ServiceHttpClientConfig serviceHttpClientConfig;
  private final String serviceSecret;
  private final String clientId;
  private final String queueEnvNamespace;

  public HsqsServiceClientModule(QueueServiceClientConfig queueServiceClientConfig, String clientId) {
    this.serviceHttpClientConfig = queueServiceClientConfig.getHttpClientConfig();
    this.serviceSecret = queueServiceClientConfig.getQueueServiceSecret();
    this.clientId = clientId;
    this.queueEnvNamespace = queueServiceClientConfig.getEnvNamespace();
  }

  @Provides
  @Singleton
  private HsqsServiceHttpClientFactory hsqsHttpClientFactory(KryoConverterFactory kryoConverterFactory) {
    return new HsqsServiceHttpClientFactory(
        serviceHttpClientConfig, serviceSecret, new ServiceTokenGenerator(), kryoConverterFactory, clientId);
  }

  @Override
  protected void configure() {
    bind(HsqsClient.class).toProvider(HsqsServiceHttpClientFactory.class).in(Scopes.SINGLETON);
    bind(HsqsClientService.class).to(HsqsClientServiceImpl.class).in(Scopes.SINGLETON);
  }

  @Provides
  @Singleton
  @Named(HsqsClientConstants.QUEUE_SERVICE_ENV_NAMESPACE)
  public String getQueueEnvNamespace() {
    return this.queueEnvNamespace;
  }
}
