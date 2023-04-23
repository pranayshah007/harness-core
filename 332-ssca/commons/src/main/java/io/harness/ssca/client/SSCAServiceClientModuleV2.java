/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.client;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.security.ServiceTokenGenerator;
import io.harness.serializer.kryo.KryoConverterFactory;
import io.harness.ssca.beans.entities.SSCAServiceConfig;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;

@OwnedBy(HarnessTeam.SSCA)
@Singleton
public class SSCAServiceClientModuleV2 extends AbstractModule {
  private final SSCAServiceConfig sscaServiceConfig;
  private final String clientId;

  public SSCAServiceClientModuleV2(SSCAServiceConfig sscaServiceConfig, String clientId) {
    this.sscaServiceConfig = sscaServiceConfig;
    this.clientId = clientId;
  }

  @Provides
  @Singleton
  private SSCAServiceClientFactoryV2 sscaServiceClientFactoryV2(KryoConverterFactory kryoConverterFactory) {
    return new SSCAServiceClientFactoryV2(sscaServiceConfig.getHttpClientConfig(), sscaServiceConfig.getServiceSecret(),
        new ServiceTokenGenerator(), kryoConverterFactory, clientId);
  }

  @Override
  protected void configure() {
    this.bind(SSCAServiceConfig.class).toInstance(this.sscaServiceConfig);
    bind(SSCAServiceClient.class).toProvider(SSCAServiceClientFactoryV2.class).in(Scopes.SINGLETON);
  }
}
