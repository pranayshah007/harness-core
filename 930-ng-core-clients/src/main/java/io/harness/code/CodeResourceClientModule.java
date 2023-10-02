/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.code;

import static io.harness.annotations.dev.HarnessTeam.CODE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.remote.client.ClientMode;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.security.ServiceTokenGenerator;
import io.harness.serializer.kryo.KryoConverterFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;

@OwnedBy(CODE)
public class CodeResourceClientModule extends AbstractModule {
  private final ServiceHttpClientConfig httpClientConfig;
  private final String serviceSecret;
  private final String clientId;
  private final ClientMode clientMode;

  @Inject
  public CodeResourceClientModule(
      ServiceHttpClientConfig httpClientConfig, String serviceSecret, String clientId, ClientMode clientMode) {
    this.httpClientConfig = httpClientConfig;
    this.serviceSecret = serviceSecret;
    this.clientId = clientId;
    this.clientMode = clientMode;
  }

  @Provides
  @Singleton
  private CodeResourceHttpClientFactory providesHttpClientFactory(KryoConverterFactory kryoConverterFactory) {
    return new CodeResourceHttpClientFactory(this.httpClientConfig, this.serviceSecret, new ServiceTokenGenerator(),
        kryoConverterFactory, clientId, clientMode);
  }

  @Override
  protected void configure() {
    this.bind(CodeResourceClient.class).toProvider(CodeResourceHttpClientFactory.class).in(Scopes.SINGLETON);
  }
}
