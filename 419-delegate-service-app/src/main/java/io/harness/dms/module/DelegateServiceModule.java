/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.dms.module;

import static io.harness.authorization.AuthorizationServiceHeader.DMS;

import io.harness.account.AccountClientModule;
import io.harness.cache.CacheModule;
import io.harness.dms.configuration.DelegateServiceConfiguration;
import io.harness.metrics.impl.DelegateMetricsServiceImpl;
import io.harness.metrics.intfc.DelegateMetricsService;
import io.harness.metrics.modules.MetricsModule;
import io.harness.module.AgentMtlsModule;
import io.harness.module.DelegateAuthModule;
import io.harness.module.DmsModule;
import io.harness.threading.ExecutorModule;

import com.google.inject.AbstractModule;

public class DelegateServiceModule extends AbstractModule {
  private final DelegateServiceConfiguration config;

  public DelegateServiceModule(DelegateServiceConfiguration config) {
    this.config = config;
  }

  @Override
  protected void configure() {
    install(new MetricsModule());
    install(new DelegateServiceKryoModule());
    install(new DelegateServiceMongoModule(config));
    install(new DelegateAuthModule());
    install(new DmsModule());
    install(new AgentMtlsModule(config.getAgentMtlsSubdomain()));
    install(ExecutorModule.getInstance());
    install(new CacheModule(config.getCacheConfig()));
    bind(DelegateServiceConfiguration.class).toInstance(config);
    bind(DelegateMetricsService.class).to(DelegateMetricsServiceImpl.class);
    install(new AccountClientModule(config.getManagerClientConfig(), config.getManagerConfigSecret(), DMS.toString()));
  }
}