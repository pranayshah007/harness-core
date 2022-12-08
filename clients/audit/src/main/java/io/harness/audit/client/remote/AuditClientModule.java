/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.audit.client.remote;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.client.api.AuditClientService;
import io.harness.audit.client.api.impl.AuditClientServiceImpl;
import io.harness.audit.client.api.impl.NoopAuditClientServiceImpl;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.security.ServiceTokenGenerator;
import io.harness.serializer.kryo.KryoConverterFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;

@OwnedBy(PL)
public class AuditClientModule extends AbstractModule {
  private final ServiceHttpClientConfig auditClientConfig;
  private final String serviceSecret;
  private final String clientId;
  private final boolean enableAuditClient;

  public AuditClientModule(ServiceHttpClientConfig projectManagerClientConfig, String serviceSecret, String clientId,
      boolean enableAuditClient) {
    this.auditClientConfig = projectManagerClientConfig;
    this.serviceSecret = serviceSecret;
    this.clientId = clientId;
    this.enableAuditClient = enableAuditClient;
  }

  @Provides
  private AuditClientFactory auditClientFactory(KryoConverterFactory kryoConverterFactory) {
    return new AuditClientFactory(
        auditClientConfig, serviceSecret, new ServiceTokenGenerator(), kryoConverterFactory, clientId);
  }

  @Override
  protected void configure() {
    bind(AuditClient.class).toProvider(AuditClientFactory.class).in(Scopes.SINGLETON);
    if (enableAuditClient) {
      bind(AuditClientService.class).to(AuditClientServiceImpl.class);
    } else {
      bind(AuditClientService.class).to(NoopAuditClientServiceImpl.class);
    }
  }
}
