/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.credit.remote.admin;

import static io.harness.annotations.dev.HarnessTeam.GTM;

import io.harness.annotations.dev.OwnedBy;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.security.ServiceTokenGenerator;
import io.harness.serializer.kryo.KryoConverterFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import lombok.AllArgsConstructor;

@OwnedBy(GTM)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class AdminCreditHttpClientModule extends AbstractModule {
  private final ServiceHttpClientConfig adminCreditHttpClientConfig;
  private final String serviceSecret;
  private final String clientId;

  @Provides
  @Singleton
  private AdminCreditHttpClientFactory adminCreditHttpClientFactory(KryoConverterFactory kryoConverterFactory) {
    return new AdminCreditHttpClientFactory(this.adminCreditHttpClientConfig, this.serviceSecret,
        new ServiceTokenGenerator(), kryoConverterFactory, clientId);
  }

  @Override
  protected void configure() {
    this.bind(AdminCreditHttpClient.class).toProvider(AdminCreditHttpClientFactory.class).in(Scopes.SINGLETON);
  }
}
