/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitops;

import io.harness.gitops.remote.GitopsResourceClient;
import io.harness.gitops.remote.GitopsResourceClientHttpFactory;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.security.ServiceTokenGenerator;
import io.harness.serializer.kryo.KryoConverterFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Scopes;

public class GitopsResourceClientModule extends AbstractModule {
  private final ServiceHttpClientConfig clientConfig;
  private final String serviceSecret;
  private final String clientId;

  @Inject
  public GitopsResourceClientModule(ServiceHttpClientConfig clientConfig, String serviceSecret, String clientId) {
    this.clientConfig = clientConfig;
    this.serviceSecret = serviceSecret;
    this.clientId = clientId;
  }

  @Inject
  public GitopsResourceClientModule(GitopsResourceClientConfig config, String clientId) {
    this.clientConfig = config.getClientConfig();
    this.serviceSecret = config.getServiceSecret();
    this.clientId = clientId;
  }

  @Provides
  private GitopsResourceClientHttpFactory gitopsResourceClientHttpFactory(KryoConverterFactory kryoConverterFactory) {
    return new GitopsResourceClientHttpFactory(
        this.clientConfig, this.serviceSecret, new ServiceTokenGenerator(), kryoConverterFactory, clientId);
  }

  @Override
  protected void configure() {
    this.bind(GitopsResourceClient.class).toProvider(GitopsResourceClientHttpFactory.class).in(Scopes.SINGLETON);
  }
}
