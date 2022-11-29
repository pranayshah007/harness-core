/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.migration.list;

import io.harness.cvng.beans.CVMonitoringCategory;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.beans.TimeSeriesMetricType;
import io.harness.cvng.core.entities.MetricPack;
import io.harness.cvng.core.entities.MetricPack.MetricDefinition;
import io.harness.cvng.core.entities.MetricPack.MetricPackKeys;
import io.harness.cvng.core.services.CVNextGenConstants;
import io.harness.cvng.migration.CVNGMigration;
import io.harness.cvng.migration.beans.ChecklistItem;
import io.harness.persistence.HPersistence;
import io.harness.persistence.HQuery;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CustomHealthCustomPackForExistingProjects implements CVNGMigration {
  @Inject private HPersistence hPersistence;

  @Override
  public void migrate() {
    List<MetricPack> metricPack = hPersistence.createQuery(MetricPack.class, HQuery.excludeCount)
                                      .filter(MetricPackKeys.dataSourceType, DataSourceType.NEW_RELIC)
                                      .filter(MetricPackKeys.identifier, CVNextGenConstants.PERFORMANCE_PACK_IDENTIFIER)
                                      .asList();

    for (MetricPack pack : metricPack) {
      MetricPack customPack = hPersistence.createQuery(MetricPack.class)
                                  .filter(MetricPackKeys.dataSourceType, DataSourceType.CUSTOM_HEALTH_METRIC)
                                  .filter(MetricPackKeys.identifier, CVNextGenConstants.CUSTOM_PACK_IDENTIFIER)
                                  .filter(MetricPackKeys.accountId, pack.getAccountId())
                                  .filter(MetricPackKeys.projectIdentifier, pack.getOrgIdentifier())
                                  .filter(MetricPackKeys.orgIdentifier, pack.getOrgIdentifier())
                                  .get();

      if (customPack == null) {
        customPack = MetricPack.builder()
                         .accountId(pack.getAccountId())
                         .orgIdentifier(pack.getOrgIdentifier())
                         .projectIdentifier(pack.getProjectIdentifier())
                         .category(CVMonitoringCategory.ERRORS)
                         .dataSourceType(DataSourceType.CUSTOM_HEALTH_METRIC)
                         .identifier(CVNextGenConstants.CUSTOM_PACK_IDENTIFIER)
                         .metrics(new HashSet<>(Arrays.asList(MetricDefinition.builder()
                                                                  .name(CVNextGenConstants.CUSTOM_PACK_IDENTIFIER)
                                                                  .identifier(CVNextGenConstants.CUSTOM_PACK_IDENTIFIER)
                                                                  .type(TimeSeriesMetricType.ERROR)
                                                                  .build())))
                         .build();
        hPersistence.saveIgnoringDuplicateKeys(Collections.singletonList(customPack));
        log.info("Saved Custom health custom pack for {}", pack.getProjectIdentifier());
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
