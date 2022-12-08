/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.licensing.remote;

import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.security.ServiceTokenGenerator;
import io.harness.serializer.kryo.KryoConverterFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;

public class NgLicenseHttpClientModule extends AbstractModule {
  private final ServiceHttpClientConfig ngManagerClientConfig;
  private final String serviceSecret;
  private final String clientId;
  private static NgLicenseHttpClientModule instance;

  private NgLicenseHttpClientModule(
      ServiceHttpClientConfig ngManagerClientConfig, String serviceSecret, String clientId) {
    this.ngManagerClientConfig = ngManagerClientConfig;
    this.serviceSecret = serviceSecret;
    this.clientId = clientId;
  }

  public static NgLicenseHttpClientModule getInstance(
      ServiceHttpClientConfig ngManagerClientConfig, String serviceSecret, String clientId) {
    if (instance == null) {
      instance = new NgLicenseHttpClientModule(ngManagerClientConfig, serviceSecret, clientId);
    }
    return instance;
  }

  @Provides
  private NgLicenseHttpClientFactory ngLicenseHttpClientFactory(KryoConverterFactory kryoConverterFactory) {
    return new NgLicenseHttpClientFactory(
        this.ngManagerClientConfig, this.serviceSecret, new ServiceTokenGenerator(), kryoConverterFactory, clientId);
  }

  @Override
  protected void configure() {
    this.bind(NgLicenseHttpClient.class).toProvider(NgLicenseHttpClientFactory.class).in(Scopes.SINGLETON);
  }
}
