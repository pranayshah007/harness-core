/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.envvariable.jobs;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.envvariable.service.BackstageEnvVariableService;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.spec.server.idp.v1.model.BackstageEnvVariable;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.dropwizard.lifecycle.Managed;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.IDP)
public class BackstageEnvVariablesSyncJob implements Managed {
  private static final long DELAY_IN_MINUTES = TimeUnit.HOURS.toMinutes(24);
  private ScheduledExecutorService executorService;
  private final BackstageEnvVariableService backstageEnvVariableService;
  private final NamespaceService namespaceService;

  @Inject
  public BackstageEnvVariablesSyncJob(@Named("backstageEnvVariableSyncer") ScheduledExecutorService executorService,
      BackstageEnvVariableService backstageEnvVariableService, NamespaceService namespaceService) {
    this.executorService = executorService;
    this.backstageEnvVariableService = backstageEnvVariableService;
    this.namespaceService = namespaceService;
  }

  @Override
  public void start() throws Exception {
    executorService = Executors.newSingleThreadScheduledExecutor(
        new ThreadFactoryBuilder().setNameFormat("backstage-env-variable-sync-job").build());
    executorService.scheduleWithFixedDelay(this::run, 0, DELAY_IN_MINUTES, TimeUnit.MINUTES);
  }

  @Override
  public void stop() throws Exception {
    executorService.shutdownNow();
    executorService.awaitTermination(30, TimeUnit.SECONDS);
  }

  public void run() {
    log.info("Backstage env variables sync job started");
    List<String> accountIds = namespaceService.getAccountIds();
    accountIds.forEach(account -> {
      try {
        backstageEnvVariableService.findAndSync(account);
      } catch (Exception e) {
        log.error("Could not sync backstage env variables for account {}", account, e);
      }
    });
    log.info("Backstage env variables sync job completed");
  }
}
