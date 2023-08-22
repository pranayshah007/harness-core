/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.app.modules.platform.k8s;

import io.harness.delegate.service.core.litek8s.K8SLiteRunner;
import io.harness.delegate.service.core.litek8s.K8SRunnerConfig;
import io.harness.delegate.service.runners.itfc.Runner;

import com.google.inject.AbstractModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class K8SRunnerModule extends AbstractModule {
  private final K8SRunnerConfig config;

  @Override
  protected void configure() {
    /**
     * We don't need to re-install this module in immutable delegate
     install(new DelegateDecryptionModule());
     */
    try {
      install(new ApiClientModule());
    } catch (Exception e) {
      log.warn("Exception occurred when creating apiClient with k8sConfig.");
    }

    bind(K8SRunnerConfig.class).toInstance(config);
    bind(Runner.class).to(K8SLiteRunner.class);
  }
}
