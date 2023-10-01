/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.app;

import io.harness.manage.ManagedScheduledExecutorService;
import io.harness.threading.ThreadPool;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ManagerExecutorModule extends AbstractModule {
  @Override
  protected void configure() {
    bind(ScheduledExecutorService.class)
        .annotatedWith(Names.named("waitStateResumer"))
        .toInstance(new ManagedScheduledExecutorService("WaitStateResumer"));
    bind(ScheduledExecutorService.class)
        .annotatedWith(Names.named("notifyResponseCleaner"))
        .toInstance(new ManagedScheduledExecutorService("NotifyResponseCleaner"));
    bind(ScheduledExecutorService.class)
        .annotatedWith(Names.named("delegateTaskNotifier"))
        .toInstance(new ManagedScheduledExecutorService("DelegateTaskNotifier"));
    bind(ScheduledExecutorService.class)
        .annotatedWith(Names.named("gitChangeSet"))
        .toInstance(new ManagedScheduledExecutorService("GitChangeSet"));
    bind(ScheduledExecutorService.class)
        .annotatedWith(Names.named("gitPolling"))
        .toInstance(new ManagedScheduledExecutorService("GitPolling"));
    bind(ScheduledExecutorService.class)
        .annotatedWith(Names.named("taskPollExecutor"))
        .toInstance(
            new ScheduledThreadPoolExecutor(3, new ThreadFactoryBuilder().setNameFormat("TaskPoll-Thread").build()));
    bind(ScheduledExecutorService.class)
        .annotatedWith(Names.named("delegatePool"))
        .toInstance(new ScheduledThreadPoolExecutor(
            4, new ThreadFactoryBuilder().setNameFormat("DelegatePool-Thread").build()));
    bind(ScheduledExecutorService.class)
        .annotatedWith(Names.named("perpetualTaskAssignor"))
        .toInstance(new ManagedScheduledExecutorService("perpetualTaskAssignor"));
    bind(ExecutorService.class)
        .annotatedWith(Names.named("asyncExecutor"))
        .toInstance(ThreadPool.create(10, 40, 1, TimeUnit.SECONDS,
            new ThreadFactoryBuilder().setNameFormat("async-task-%d").setPriority(Thread.MIN_PRIORITY).build()));
    bind(ExecutorService.class)
        .annotatedWith(Names.named("alternativeExecutor"))
        .toInstance(ThreadPool.create(10, 40, 1, TimeUnit.SECONDS,
            new ThreadFactoryBuilder()
                .setNameFormat("alternative-validation-task-%d")
                .setPriority(Thread.MIN_PRIORITY)
                .build()));
    bind(ExecutorService.class)
        .annotatedWith(Names.named("gdsExecutor"))
        .toInstance(ThreadPool.create(
            2, 10, 5, TimeUnit.SECONDS, new ThreadFactoryBuilder().setNameFormat("gds-log-fetcher-%d").build()));
    bind(ExecutorService.class)
        .annotatedWith(Names.named("stateMachineExecutor-handler"))
        .toInstance(ThreadPool.create(10, 100, 500L, TimeUnit.SECONDS,
            new ThreadFactoryBuilder().setNameFormat("stateMachineExecutor-handler-%d").build()));
    bind(ExecutorService.class)
        .annotatedWith(Names.named("cgCdLicenseUsageExecutor"))
        .toInstance(ThreadPool.create(5, 20, 1L, TimeUnit.SECONDS,
            new ThreadFactoryBuilder().setNameFormat("cgCdLicenseUsageExecutor-%d").build()));
  }
}
