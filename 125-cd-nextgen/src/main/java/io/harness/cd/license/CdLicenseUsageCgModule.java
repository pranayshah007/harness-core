/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cd.license;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.security.ServiceTokenGenerator;
import io.harness.serializer.kryo.KryoConverterFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;

@OwnedBy(HarnessTeam.CDP)
public class CdLicenseUsageCgModule extends AbstractModule {
  private final ServiceHttpClientConfig serviceHttpClientConfig;
  private final String serviceSecret;
  private final String clientId;

  public CdLicenseUsageCgModule(ServiceHttpClientConfig cgManagerHttpConfig, String serviceSecret, String clientId) {
    this.serviceHttpClientConfig = ServiceHttpClientConfig.builder()
                                       .baseUrl(cgManagerHttpConfig.getBaseUrl())
                                       .connectTimeOutSeconds(cgManagerHttpConfig.getConnectTimeOutSeconds())
                                       .readTimeOutSeconds(600L)
                                       .enableHttpLogging(Boolean.FALSE)
                                       .build();
    this.serviceSecret = serviceSecret;
    this.clientId = clientId;
  }

  @Provides
  @Singleton
  private CdLicenseUsageCgClientFactory clientFactory(KryoConverterFactory kryoConverterFactory) {
    return new CdLicenseUsageCgClientFactory(
        serviceHttpClientConfig, serviceSecret, new ServiceTokenGenerator(), kryoConverterFactory, clientId);
  }

  @Override
  protected void configure() {
    bind(CdLicenseUsageCgClient.class).toProvider(CdLicenseUsageCgClientFactory.class).in(Scopes.SINGLETON);
  }
}
