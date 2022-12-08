/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.migration.list;

import io.harness.cvng.core.entities.MonitoringSourcePerpetualTask;
import io.harness.cvng.core.entities.MonitoringSourcePerpetualTask.MonitoringSourcePerpetualTaskKeys;
import io.harness.cvng.core.entities.PrometheusCVConfig;
import io.harness.cvng.core.services.api.MonitoringSourcePerpetualTaskService;
import io.harness.cvng.migration.CVNGMigration;
import io.harness.cvng.migration.beans.ChecklistItem;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import java.util.HashSet;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PrometheusConnectorAuthenticationPerpetualTaskMigration implements CVNGMigration {
  @Inject private HPersistence hPersistence;
  @Inject private MonitoringSourcePerpetualTaskService monitoringSourcePerpetualTaskService;

  @Override
  public void migrate() {
    List<PrometheusCVConfig> prometheusCVConfigs = hPersistence.createQuery(PrometheusCVConfig.class, new HashSet<>())
                                                       .filter("className", PrometheusCVConfig.class.getCanonicalName())
                                                       .asList();
    prometheusCVConfigs.forEach(prometheusCVConfig -> {
      log.info("Resetting perpetual task for prometheusCVConfig : " + prometheusCVConfig.getUuid());
      List<MonitoringSourcePerpetualTask> monitoringSourcePerpetualTasks =
          hPersistence.createQuery(MonitoringSourcePerpetualTask.class)
              .filter(MonitoringSourcePerpetualTaskKeys.accountId, prometheusCVConfig.getAccountId())
              .filter(MonitoringSourcePerpetualTaskKeys.orgIdentifier, prometheusCVConfig.getOrgIdentifier())
              .filter(MonitoringSourcePerpetualTaskKeys.projectIdentifier, prometheusCVConfig.getProjectIdentifier())
              .filter(MonitoringSourcePerpetualTaskKeys.monitoringSourceIdentifier,
                  prometheusCVConfig.getFullyQualifiedIdentifier())
              .asList();
      monitoringSourcePerpetualTasks.forEach(monitoringSourcePerpetualTask
          -> monitoringSourcePerpetualTaskService.resetLiveMonitoringPerpetualTask(monitoringSourcePerpetualTask));
    });
  }

  @Override
  public ChecklistItem whatHappensOnRollback() {
    return ChecklistItem.NA;
  }

  @Override
  public ChecklistItem whatHappensIfOldVersionIteratorPicksMigratedEntity() {
    return ChecklistItem.NA;
  }
}
