/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pipeline.yamlschema;

import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.security.ServiceTokenGenerator;
import io.harness.serializer.kryo.KryoConverterFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;

public class PipelineYamlSchemaClientModule extends AbstractModule {
  private final ServiceHttpClientConfig serviceHttpClientConfig;
  private final String serviceSecret;
  private final String clientId;
  public static final String SECRET_NG_MANAGER_CLIENT_SERVICE = "secretNGManagerClientService";

  public PipelineYamlSchemaClientModule(
      ServiceHttpClientConfig serviceHttpClientConfig, String serviceSecret, String clientId) {
    this.serviceHttpClientConfig = serviceHttpClientConfig;
    this.serviceSecret = serviceSecret;
    this.clientId = clientId;
  }

  @Provides
  private PipelineYamlSchemaServiceHttpClientFactory secretNGManagerHttpClientFactory(
      KryoConverterFactory kryoConverterFactory) {
    return new PipelineYamlSchemaServiceHttpClientFactory(
        serviceHttpClientConfig, serviceSecret, new ServiceTokenGenerator(), kryoConverterFactory, clientId);
  }

  @Override
  protected void configure() {
    bind(PipelineYamlSchemaServiceClient.class)
        .toProvider(PipelineYamlSchemaServiceHttpClientFactory.class)
        .in(Scopes.SINGLETON);
  }
}
