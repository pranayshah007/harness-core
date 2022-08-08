/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.migration.list;

import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.core.entities.AppDynamicsCVConfig;
import io.harness.cvng.core.entities.AppDynamicsCVConfig.AppDynamicsCVConfigKeys;
import io.harness.cvng.core.entities.AppDynamicsCVConfig.MetricInfo.AppDynamicsMetricInfoKeys;
import io.harness.cvng.core.entities.MetricCVConfig.MetricCVConfigKeys;
import io.harness.cvng.core.entities.MetricPack.MetricPackKeys;
import io.harness.cvng.migration.CVNGMigration;
import io.harness.cvng.migration.beans.ChecklistItem;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

@Slf4j
public class AppDCVConfigCompleteMetricPathMigration implements CVNGMigration {
  @Inject private HPersistence hPersistence;

  @Override
  public void migrate() {
    Query<AppDynamicsCVConfig> query =
        hPersistence.createQuery(AppDynamicsCVConfig.class, excludeAuthority)
            .filter(MetricCVConfigKeys.metricPack + "." + MetricPackKeys.dataSourceType, DataSourceType.APP_DYNAMICS)
            .field(AppDynamicsCVConfigKeys.metricInfos + "." + AppDynamicsMetricInfoKeys.completeMetricPath)
            .doesNotExist();
    try (HIterator<AppDynamicsCVConfig> iterator = new HIterator<>(query.fetch())) {
      while (iterator.hasNext()) {
        AppDynamicsCVConfig appDynamicsCVConfig = iterator.next();
        if (CollectionUtils.isNotEmpty(appDynamicsCVConfig.getMetricInfos())) {
          log.info("AppDCVConfigCompleteMetricPathMigration started for the CVConfig with uuid : "
              + appDynamicsCVConfig.getUuid());
          appDynamicsCVConfig.populateCompleteMetricPaths();
          UpdateOperations<AppDynamicsCVConfig> updateOperations =
              hPersistence.createUpdateOperations(AppDynamicsCVConfig.class)
                  .set(AppDynamicsCVConfigKeys.metricInfos, appDynamicsCVConfig.getMetricInfos());
          hPersistence.update(appDynamicsCVConfig, updateOperations);
          log.info("AppDCVConfigCompleteMetricPathMigration done for the CVConfig with uuid : "
              + appDynamicsCVConfig.getUuid());
        }
      }
    }
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
