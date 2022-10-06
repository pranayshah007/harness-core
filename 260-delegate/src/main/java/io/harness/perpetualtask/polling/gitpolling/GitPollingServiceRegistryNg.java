/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.polling.gitpolling;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.git.taskHandlers.GitPollingTaskHandler;
import io.harness.delegate.task.git.taskHandlers.github.GitHubTaskHandler;
import io.harness.delegate.task.gitpolling.GitPollingSourceType;
import io.harness.exception.InvalidRequestException;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Singleton;

@OwnedBy(HarnessTeam.CDC)
@Singleton
public class GitPollingServiceRegistryNg {
  @Inject Injector injector;

  public GitPollingTaskHandler getWebhookPollingService(GitPollingSourceType gitPollingSourceType) {
    Class<? extends GitPollingTaskHandler> buildServiceClass = getWebhookPollingServiceClass(gitPollingSourceType);
    return injector.getInstance(Key.get(buildServiceClass));
  }

  public Class<? extends GitPollingTaskHandler> getWebhookPollingServiceClass(
      GitPollingSourceType gitPollingSourceType) {
    switch (gitPollingSourceType) {
      case GITHUB:
        return GitHubTaskHandler.class;
      default:
        throw new InvalidRequestException("Unknown git source type: " + gitPollingSourceType);
    }
  }
}
