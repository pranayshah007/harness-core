/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.migration.list;

import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.MetricCVConfig;
import io.harness.data.structure.UUIDGenerator;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import com.mongodb.client.MongoCursor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class CVConfigPopulateUuidForThresholds extends CVNGBaseMigration {
  @Inject private HPersistence hPersistence;

  @Override
  public void migrate() {
    log.info("starting CVConfigPopulateUuidForThresholds migration");
    MongoCursor<CVConfig> iterator = hPersistence.createQuery(CVConfig.class).iterator();
    while (iterator.hasNext()) {
      CVConfig cvConfig = iterator.next();
      log.info("starting CVConfigPopulateUuidForThresholds migration for cvConfigId: " + cvConfig.getUuid());
      if (!(cvConfig instanceof MetricCVConfig)) {
        continue;
      }
      MetricCVConfig metricCVConfig = (MetricCVConfig) cvConfig;
      if (metricCVConfig.getMetricPack() == null) {
        continue;
      }
      CollectionUtils.emptyIfNull(metricCVConfig.getMetricPack().getMetrics())
          .stream()
          .flatMap(metricDefinition -> CollectionUtils.emptyIfNull(metricDefinition.getThresholds()).stream())
          .filter(timeSeriesThreshold -> StringUtils.isEmpty(timeSeriesThreshold.getUuid()))
          .forEach(timeSeriesThreshold -> timeSeriesThreshold.setUuid(UUIDGenerator.generateUuid()));
      hPersistence.save(metricCVConfig);
      log.info("Done with CVConfigPopulateUuidForThresholds migration for cvConfigId: " + metricCVConfig.getUuid());
    }
  }
}
