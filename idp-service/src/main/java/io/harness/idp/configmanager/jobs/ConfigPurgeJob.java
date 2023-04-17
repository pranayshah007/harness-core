/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.configmanager.jobs;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.configmanager.beans.entity.AppConfigEntity;
import io.harness.idp.configmanager.service.ConfigManagerService;

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
public class ConfigPurgeJob implements Managed {
  private static final long DELAY_IN_DAYS = TimeUnit.DAYS.toDays(7);
  private ScheduledExecutorService executorService;
  private ConfigManagerService configManagerService;

  @Inject
  public ConfigPurgeJob(
      @Named("AppConfigPurger") ScheduledExecutorService executorService, ConfigManagerService configManagerService) {
    this.executorService = executorService;
    this.configManagerService = configManagerService;
  }

  @Override
  public void start() throws Exception {
    executorService = Executors.newSingleThreadScheduledExecutor(
        new ThreadFactoryBuilder().setNameFormat("app-config-purge-job").build());
    executorService.scheduleWithFixedDelay(this::run, 0, DELAY_IN_DAYS, TimeUnit.DAYS);
  }

  @Override
  public void stop() throws Exception {
    executorService.shutdownNow();
    executorService.awaitTermination(30, TimeUnit.SECONDS);
  }

  public void run() {
    log.info("App Config purge job started for disabled plugins....");
    try {
      List<AppConfigEntity> appConfigEntities =
          configManagerService.deleteDisabledPluginsConfigsDisabledMoreThanAWeekAgo();
      if (appConfigEntities.isEmpty()) {
        log.info(
            "No config shortlisted for Purging - either all plugins are enabled or they are disabled within one week");
      }
      appConfigEntities.forEach(appConfigEntity -> {
        String accountIdentifier = appConfigEntity.getAccountIdentifier();
        String configId = appConfigEntity.getConfigId();
        log.info("App config purged for account {} and plugin id - {}", accountIdentifier, configId);
      });
      log.info("Weekly Config purge job completed");
    } catch (Exception e) {
      log.error("Weekly App config purge job unsuccessful Error - ", e);
    }
  }
}
