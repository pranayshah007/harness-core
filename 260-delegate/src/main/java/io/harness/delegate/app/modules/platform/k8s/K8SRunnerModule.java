/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.app.modules.platform.k8s;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import io.harness.decryption.delegate.module.DelegateDecryptionModule;
import io.harness.delegate.service.core.litek8s.K8SLiteRunner;
import io.harness.delegate.service.core.runner.TaskRunner;
import io.harness.security.encryption.DelegateDecryptionService;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.Config;
import software.wings.service.impl.security.DelegateDecryptionServiceImpl;

import java.io.IOException;

public class K8SRunnerModule extends AbstractModule {

  @Override
  protected void configure() {
    install(new DelegateDecryptionModule());
    install(new ApiClientModule());

    bind(TaskRunner.class).to(K8SLiteRunner.class);
  }
}
