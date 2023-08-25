/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.execution;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CI)
public class BackgroundTaskUtility {
  @Inject @Named("ciBackgroundTaskExecutor") private ExecutorService backgroundExecutorService;

  public void queueJob(Runnable task) {
    backgroundExecutorService.submit(task);
  }
}
