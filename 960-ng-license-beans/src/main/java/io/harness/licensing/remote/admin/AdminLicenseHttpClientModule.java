/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.licensing.remote.admin;

import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.security.ServiceTokenGenerator;
import io.harness.serializer.kryo.KryoConverterFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import lombok.AllArgsConstructor;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class AdminLicenseHttpClientModule extends AbstractModule {
  private final ServiceHttpClientConfig adminLicenseHttpClientConfig;
  private final String serviceSecret;
  private final String clientId;

  @Provides
  private AdminLicenseHttpClientFactory adminLicenseHttpClientFactory(KryoConverterFactory kryoConverterFactory) {
    return new AdminLicenseHttpClientFactory(this.adminLicenseHttpClientConfig, this.serviceSecret,
        new ServiceTokenGenerator(), kryoConverterFactory, clientId);
  }

  @Override
  protected void configure() {
    this.bind(AdminLicenseHttpClient.class).toProvider(AdminLicenseHttpClientFactory.class).in(Scopes.SINGLETON);
  }
}
