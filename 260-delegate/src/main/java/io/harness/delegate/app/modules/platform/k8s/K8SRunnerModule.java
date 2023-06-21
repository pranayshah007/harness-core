/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.app.modules.platform.k8s;

import io.harness.decryption.delegate.module.DelegateDecryptionModule;
import io.harness.delegate.service.core.litek8s.K8SLiteRunner;
import io.harness.delegate.service.core.litek8s.K8SRunnerConfig;
import io.harness.delegate.service.core.runner.TaskRunner;

import com.google.inject.AbstractModule;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class K8SRunnerModule extends AbstractModule {
  private final K8SRunnerConfig config;

  @Override
  protected void configure() {
    install(new DelegateDecryptionModule());
    install(new ApiClientModule());

    bind(K8SRunnerConfig.class).toInstance(config);
    bind(TaskRunner.class).to(K8SLiteRunner.class);
  }
}
