/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.migration.list;

import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.core.entities.ELKCVConfig;
import io.harness.cvng.core.entities.HealthSourceParams;
import io.harness.cvng.core.entities.NextGenLogCVConfig;
import io.harness.cvng.core.entities.QueryParams;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import java.util.HashSet;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UpdateELKCVConfigToNextGenCVConfig extends CVNGBaseMigration {
  private static final String DEFAULT_GROUP_NAME = "default_group";
  @Inject private HPersistence hPersistence;

  @Override
  public void migrate() {
    log.info("starting UpdateELKCVConfigToV2 migration");
    List<ELKCVConfig> elkCVConfigs = getElkcvConfigs();
    int retryCount = 0;
    while (elkCVConfigs.size() > 0 && retryCount < 3) {
      for (ELKCVConfig cvConfig : elkCVConfigs) {
        log.info("starting UpdateELKCVConfigToV2 migration for cvConfigId: " + cvConfig.getFullyQualifiedIdentifier());
        NextGenLogCVConfig nextGenLogCVConfig = getNextGenLogCVConfigFromELKCVConfig(cvConfig);
        Query<NextGenLogCVConfig> nextGenLogCVConfigQuery =
            hPersistence.createQuery(NextGenLogCVConfig.class)
                .disableValidation()
                .filter("identifier", cvConfig.getFullyQualifiedIdentifier())
                .filter("className", NextGenLogCVConfig.class.getCanonicalName())
                .filter("productName", cvConfig.getProductName());
        if (nextGenLogCVConfigQuery.count() == 0) {
          hPersistence.insertIgnoringDuplicateKeys(nextGenLogCVConfig);
        }
        // Check if the current CVConfig has been migrated to NextGenCVConfig.
        if (nextGenLogCVConfigQuery.count() > 0) {
          log.info("Going to delete document for Old CVConfig: {}", cvConfig);
          hPersistence.delete(hPersistence.createQuery(ELKCVConfig.class)
                                  .disableValidation()
                                  .filter("identifier", cvConfig.getFullyQualifiedIdentifier())
                                  .filter("className", ELKCVConfig.class.getCanonicalName())
                                  .filter("productName", cvConfig.getProductName())
                                  .field(NextGenLogCVConfig.CVConfigKeys.dataSourceType)
                                  .doesNotExist());
          log.info(
              "Done with UpdateELKCVConfigToV2 migration for cvConfigId: " + cvConfig.getFullyQualifiedIdentifier());
        }
      }
      elkCVConfigs = getElkcvConfigs();
      if (elkCVConfigs.size() > 0) {
        retryCount++;
      }
    }
    if (retryCount == 3) {
      throw new RuntimeException("Could not successfully migrate all CVConfigs");
    }
  }

  private List<ELKCVConfig> getElkcvConfigs() {
    return hPersistence.createQuery(ELKCVConfig.class, new HashSet<>())
        .filter("className", ELKCVConfig.class.getCanonicalName())
        .asList();
  }

  private static NextGenLogCVConfig getNextGenLogCVConfigFromELKCVConfig(ELKCVConfig cvConfig) {
    return NextGenLogCVConfig.builder()
        .identifier(cvConfig.getIdentifier())
        .queryIdentifier(cvConfig.getQueryName())
        .queryName(cvConfig.getQueryName())
        .query(cvConfig.getQuery())
        .groupName(DEFAULT_GROUP_NAME)
        .queryParams(QueryParams.builder()
                         .index(cvConfig.getIndex())
                         .timeStampIdentifier(cvConfig.getTimeStampIdentifier())
                         .timeStampFormat(cvConfig.getTimeStampFormat())
                         .messageIdentifier(cvConfig.getMessageIdentifier())
                         .serviceInstanceField(cvConfig.getServiceInstanceIdentifier())
                         .build())
        .healthSourceParams(HealthSourceParams.builder().build())
        .productName(cvConfig.getProductName())
        .dataSourceType(DataSourceType.ELASTICSEARCH)
        .accountId(cvConfig.getAccountId())
        .connectorIdentifier(cvConfig.getConnectorIdentifier())
        .enabled(cvConfig.isEnabled())
        .monitoredServiceIdentifier(cvConfig.getMonitoredServiceIdentifier())
        .monitoringSourceName(cvConfig.getMonitoringSourceName())
        .orgIdentifier(cvConfig.getOrgIdentifier())
        .projectIdentifier(cvConfig.getProjectIdentifier())
        .isDemo(cvConfig.isDemo())
        .verificationType(cvConfig.getVerificationType())
        .category(cvConfig.getCategory())
        .build();
  }
}
