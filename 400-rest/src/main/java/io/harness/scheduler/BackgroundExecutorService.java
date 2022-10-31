/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.scheduler;

import io.harness.threading.CurrentThreadExecutor;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

@Singleton
public class BackgroundExecutorService {
  private ExecutorService executorService;

  @Inject
  public BackgroundExecutorService(
      ExecutorService executorService, @Named("BackgroundSchedule") SchedulerConfig configuration) {
    this.executorService = configuration.isClustered() ? new CurrentThreadExecutor() : executorService;
  }

  public Future submit(Runnable task) {
    return executorService.submit(task);
  }
}
