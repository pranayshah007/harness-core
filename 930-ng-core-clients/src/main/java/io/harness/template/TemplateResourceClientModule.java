/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.security.ServiceTokenGenerator;
import io.harness.serializer.kryo.KryoConverterFactory;
import io.harness.template.remote.TemplateResourceClient;
import io.harness.template.remote.TemplateResourceClientHttpFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;

@OwnedBy(CDC)
public class TemplateResourceClientModule extends AbstractModule {
  private final ServiceHttpClientConfig templateClientConfig;
  private final String serviceSecret;
  private final String clientId;

  @Inject
  public TemplateResourceClientModule(
      ServiceHttpClientConfig templateClientConfig, String serviceSecret, String clientId) {
    this.templateClientConfig = templateClientConfig;
    this.serviceSecret = serviceSecret;
    this.clientId = clientId;
  }

  @Provides
  @Singleton
  private TemplateResourceClientHttpFactory templateResourceClientHttpFactory(
      KryoConverterFactory kryoConverterFactory) {
    return new TemplateResourceClientHttpFactory(
        this.templateClientConfig, this.serviceSecret, new ServiceTokenGenerator(), kryoConverterFactory, clientId);
  }

  @Override
  protected void configure() {
    this.bind(TemplateResourceClient.class).toProvider(TemplateResourceClientHttpFactory.class).in(Scopes.SINGLETON);
  }
}
