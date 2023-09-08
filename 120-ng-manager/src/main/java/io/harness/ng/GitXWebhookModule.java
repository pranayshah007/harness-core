/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng;

import io.harness.gitsync.gitxwebhooks.service.GitXWebhookService;
import io.harness.gitsync.gitxwebhooks.service.GitXWebhookServiceImpl;

import com.google.inject.AbstractModule;

public class GitXWebhookModule extends AbstractModule {
  NextGenConfiguration appConfig;

  public GitXWebhookModule(NextGenConfiguration appConfig) {
    this.appConfig = appConfig;
  }

  @Override
  protected void configure() {
    bind(GitXWebhookService.class).to(GitXWebhookServiceImpl.class);
  }
}
