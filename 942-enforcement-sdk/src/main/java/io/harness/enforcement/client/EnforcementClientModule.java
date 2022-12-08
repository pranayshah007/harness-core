/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.enforcement.client;

import io.harness.enforcement.client.annotation.FeatureRestrictionCheck;
import io.harness.enforcement.client.annotation.interceptor.FeatureRestrictionCheckInterceptor;
import io.harness.enforcement.client.services.EnforcementClientService;
import io.harness.enforcement.client.services.EnforcementSdkRegisterService;
import io.harness.enforcement.client.services.impl.EnforcementClientServiceImpl;
import io.harness.enforcement.client.services.impl.EnforcementSdkRegisterServiceImpl;
import io.harness.govern.ProviderMethodInterceptor;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.security.ServiceTokenGenerator;
import io.harness.serializer.kryo.KryoConverterFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.matcher.Matchers;

public class EnforcementClientModule extends AbstractModule {
  private static EnforcementClientModule instance;
  private final ServiceHttpClientConfig ngManagerClientConfig;
  private final String serviceSecret;
  private final String clientId;
  private final EnforcementClientConfiguration enforcementClientConfiguration;

  private EnforcementClientModule(ServiceHttpClientConfig ngManagerClientConfig, String serviceSecret, String clientId,
      EnforcementClientConfiguration enforcementClientConfiguration) {
    this.ngManagerClientConfig = ngManagerClientConfig;
    this.serviceSecret = serviceSecret;
    this.clientId = clientId;
    this.enforcementClientConfiguration = enforcementClientConfiguration;
  }

  public static EnforcementClientModule getInstance(ServiceHttpClientConfig ngManagerClientConfig, String serviceSecret,
      String clientId, EnforcementClientConfiguration enforcementClientConfiguration) {
    if (instance == null) {
      instance =
          new EnforcementClientModule(ngManagerClientConfig, serviceSecret, clientId, enforcementClientConfiguration);
    }
    return instance;
  }

  @Provides
  private EnforcementClientFactory enforcementClientFactory(KryoConverterFactory kryoConverterFactory) {
    return new EnforcementClientFactory(
        this.ngManagerClientConfig, this.serviceSecret, new ServiceTokenGenerator(), kryoConverterFactory, clientId);
  }

  @Provides
  private EnforcementClientConfiguration enforcementClientConfiguration() {
    return enforcementClientConfiguration;
  }

  @Override
  protected void configure() {
    ProviderMethodInterceptor featureCheck =
        new ProviderMethodInterceptor(getProvider(FeatureRestrictionCheckInterceptor.class));
    bindInterceptor(Matchers.any(), Matchers.annotatedWith(FeatureRestrictionCheck.class), featureCheck);

    bind(EnforcementClient.class).toProvider(EnforcementClientFactory.class).in(Scopes.SINGLETON);
    bind(EnforcementClientService.class).to(EnforcementClientServiceImpl.class);
    bind(EnforcementSdkRegisterService.class).to(EnforcementSdkRegisterServiceImpl.class);
  }
}
