/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.reconcile;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.reconcile.remote.NgManagerReconcileClient;
import io.harness.reconcile.remote.NgManagerReconcileClientHttpFactory;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.security.ServiceTokenGenerator;
import io.harness.serializer.kryo.KryoConverterFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;

@OwnedBy(HarnessTeam.CDC)
public class NgManagerReconcileClientModule extends AbstractModule {
  private final ServiceHttpClientConfig ngManagerReconcileClientConfig;
  private final String serviceSecret;
  private final String clientId;

  @Inject
  public NgManagerReconcileClientModule(
      ServiceHttpClientConfig ngManagerReconcileClientConfig, String serviceSecret, String clientId) {
    this.ngManagerReconcileClientConfig = ngManagerReconcileClientConfig;
    this.serviceSecret = serviceSecret;
    this.clientId = clientId;
  }

  @Provides
  @Singleton
  private NgManagerReconcileClientHttpFactory ngManagerReconcileClientHttpFactory(
      KryoConverterFactory kryoConverterFactory) {
    return new NgManagerReconcileClientHttpFactory(this.ngManagerReconcileClientConfig, this.serviceSecret,
        new ServiceTokenGenerator(), kryoConverterFactory, clientId);
  }

  @Override
  protected void configure() {
    this.bind(NgManagerReconcileClient.class)
        .toProvider(NgManagerReconcileClientHttpFactory.class)
        .in(Scopes.SINGLETON);
  }
}
