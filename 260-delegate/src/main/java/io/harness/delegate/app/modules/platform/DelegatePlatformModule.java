/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.app.modules.platform;

import io.harness.delegate.app.modules.common.DelegateHealthModule;
import io.harness.delegate.app.modules.common.DelegateTokensModule;
import io.harness.delegate.app.modules.platform.k8s.K8SRunnerModule;
import io.harness.delegate.configuration.DelegateConfiguration;
import io.harness.delegate.service.core.litek8s.K8SRunnerConfig;
import io.harness.metrics.MetricRegistryModule;

import com.codahale.metrics.MetricRegistry;
import com.google.inject.AbstractModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@RequiredArgsConstructor
@Slf4j
public class DelegatePlatformModule extends AbstractModule {
  private final DelegateConfiguration configuration;

  @Override
  protected void configure() {
    super.configure();

    if (StringUtils.isNotEmpty(configuration.getClientCertificateFilePath())
        && StringUtils.isNotEmpty(configuration.getClientCertificateKeyFilePath())) {
      log.info("Delegate is running with mTLS enabled.");
    }

    install(new DelegateTokensModule(configuration));
    install(new DelegateHealthModule());

    install(new MetricRegistryModule(new MetricRegistry()));
    install(new ClientModule());
    install(
        new DelegateExecutorsModule(configuration.isDynamicHandlingOfRequestEnabled())); // Check if some can be removed
    install(new DelegateCommonModule(configuration));

    final var delegateName = System.getenv().get("DELEGATE_GROUP_NAME");
    final var delegateNamespace = System.getenv().get("DELEGATE_NAMESPACE");
    final var runnerConfig = new K8SRunnerConfig(
        delegateNamespace, delegateName, configuration.getAccountId(), configuration.getLogStreamingServiceBaseUrl());

    install(new K8SRunnerModule(runnerConfig));
  }
}
