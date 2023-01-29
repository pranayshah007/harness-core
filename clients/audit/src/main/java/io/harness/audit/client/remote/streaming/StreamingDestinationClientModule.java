/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.audit.client.remote.streaming;

import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.security.ServiceTokenGenerator;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;

public class StreamingDestinationClientModule extends AbstractModule {
  private final ServiceHttpClientConfig streamingDestinationClientConfig;
  private final String serviceSecret;
  private final String clientId;

  public StreamingDestinationClientModule(
      ServiceHttpClientConfig streamingDestinationClientConfig, String serviceSecret, String clientId) {
    this.streamingDestinationClientConfig = streamingDestinationClientConfig;
    this.serviceSecret = serviceSecret;
    this.clientId = clientId;
  }

  @Provides
  @Singleton
  private StreamingDestinationClientFactory streamingDestinationClientFactory() {
    return new StreamingDestinationClientFactory(
        streamingDestinationClientConfig, serviceSecret, new ServiceTokenGenerator(), clientId);
  }

  @Override
  protected void configure() {
    bind(StreamingDestinationClient.class).toProvider(StreamingDestinationClientFactory.class).in(Scopes.SINGLETON);
  }
}
