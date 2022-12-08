/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm;

import io.harness.remote.client.ClientMode;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.security.ServiceTokenGenerator;
import io.harness.serializer.kryo.KryoConverterFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Scopes;

public class LightwingClientModule extends AbstractModule {
  private final ServiceHttpClientConfig lightwingAutoCUDClientConfig;
  private final String serviceSecret;
  private final String clientId;
  private final ClientMode clientMode;

  @Inject
  public LightwingClientModule(ServiceHttpClientConfig lightwingAutoCUDClientConfig, String serviceSecret,
      String clientId, ClientMode clientMode) {
    this.lightwingAutoCUDClientConfig = lightwingAutoCUDClientConfig;
    this.serviceSecret = serviceSecret;
    this.clientId = clientId;
    this.clientMode = clientMode;
  }

  @Provides
  private LightwingHttpClientFactory providesHttpClientFactory(KryoConverterFactory kryoConverterFactory) {
    return new LightwingHttpClientFactory(this.lightwingAutoCUDClientConfig, this.serviceSecret,
        new ServiceTokenGenerator(), kryoConverterFactory, clientId, clientMode);
  }

  @Override
  protected void configure() {
    this.bind(LightwingClient.class).toProvider(LightwingHttpClientFactory.class).in(Scopes.SINGLETON);
  }
}
