/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.runners;

import io.harness.delegate.app.modules.platform.k8s.K8SRunnerModule;
import io.harness.delegate.configuration.DelegateConfiguration;
import io.harness.delegate.service.core.litek8s.K8SRunnerConfig;

import com.google.inject.AbstractModule;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class RunnersModule extends AbstractModule {
  private final DelegateConfiguration configuration;

  @Override
  protected void configure() {
    final var delegateName = System.getenv().get("DELEGATE_GROUP_NAME");
    final var delegateNamespace = System.getenv().get("DELEGATE_NAMESPACE");
    final var runnerConfig = new K8SRunnerConfig(
        delegateNamespace, delegateName, configuration.getAccountId(), configuration.getLogStreamingServiceBaseUrl());
    install(new K8SRunnerModule(runnerConfig));
  }
}
