/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.code;

import io.harness.remote.client.ClientMode;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.security.ServiceTokenGenerator;
import io.harness.serializer.kryo.KryoConverterFactory;

import com.google.inject.*;

public class CodeResourceClientModule extends AbstractModule {
  private final ServiceHttpClientConfig codeClientConfig;
  private final String serviceSecret;
  private final String clientId;
  private final ClientMode clientMode;

  @Inject
  public CodeResourceClientModule(
      ServiceHttpClientConfig codeClientConfig, String serviceSecret, String clientId, ClientMode clientMode) {
    this.codeClientConfig = codeClientConfig;
    this.serviceSecret = serviceSecret;
    this.clientId = clientId;
    this.clientMode = clientMode;
  }

  @Inject
  public CodeResourceClientModule(ServiceHttpClientConfig codeClientConfig, String serviceSecret, String clientId) {
    this.codeClientConfig = codeClientConfig;
    this.serviceSecret = serviceSecret;
    this.clientId = clientId;
    this.clientMode = ClientMode.NON_PRIVILEGED;
  }

  @Provides
  @Singleton
  private CodeResourceClientFactory providesHttpClientFactory(KryoConverterFactory kryoConverterFactory) {
    return new CodeResourceClientFactory(this.codeClientConfig, this.serviceSecret, new ServiceTokenGenerator(),
        kryoConverterFactory, clientId, clientMode);
  }

  @Override
  protected void configure() {
    this.bind(CodeResourceClient.class).toProvider(CodeResourceClientFactory.class).in(Scopes.SINGLETON);
  }
}
