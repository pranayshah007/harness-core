/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration.background;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.migration.NGMigration;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.mappers.NGServiceEntityMapper;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.ng.core.service.yaml.NGServiceConfig;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;

@Slf4j
public class FetchServicesWithIdentifierDissimilarityMigration implements NGMigration {
  @Inject private HPersistence persistence;
  @Inject ServiceEntityService serviceEntityService;

  private static final String DEBUG_LOG = "[FetchServicesWithIdentifierDissimilarityMigration]: ";
  private static final String MIS_CONFIGURED_PREFIX = "[MisConfiguredServiceFound]: ";

  @Override
  public void migrate() {
    try {
      log.info(DEBUG_LOG + "Starting migration to fetch service with identifier dissimilarity");
      Query<ServiceEntity> serviceEntityQuery = persistence.createQuery(ServiceEntity.class);
      try (HIterator<ServiceEntity> iterator = new HIterator<>(serviceEntityQuery.fetch())) {
        for (ServiceEntity serviceEntity : iterator) {
          if (isNotBlank(serviceEntity.getYaml())) {
            try {
              NGServiceConfig ngServiceConfig = NGServiceEntityMapper.toNGServiceConfig(serviceEntity);
              if (ngServiceConfig != null && ngServiceConfig.getNgServiceV2InfoConfig() != null) {
                if (isNotBlank(ngServiceConfig.getNgServiceV2InfoConfig().getIdentifier())
                    && isNotBlank(serviceEntity.getIdentifier())) {
                  log.warn(String.format(MIS_CONFIGURED_PREFIX
                          + "Service with Identifier : %s, ProjectIdentifier: %s, OrgIdentifier: %s, AccountIdentifier: %s has YamlIdentifier: %s",
                      serviceEntity.getIdentifier(), serviceEntity.getProjectIdentifier(),
                      serviceEntity.getOrgIdentifier(), serviceEntity.getAccountId(),
                      ngServiceConfig.getNgServiceV2InfoConfig().getIdentifier()));
                }
              }
            } catch (Exception e) {
              log.error(String.format(DEBUG_LOG
                      + "Operation failed for Service with Identifier : %s, ProjectIdentifier: %s, OrgIdentifier: %s, AccountIdentifier: %s",
                  serviceEntity.getIdentifier(), serviceEntity.getProjectIdentifier(), serviceEntity.getOrgIdentifier(),
                  serviceEntity.getAccountId()));
            }
          }
        }
      }
      log.info(DEBUG_LOG + "Migration to fetch service with identifier dissimilarity completed");
    } catch (Exception e) {
      log.error(DEBUG_LOG + "Migration fetch service with identifier dissimilarity", e);
    }
  }
}
