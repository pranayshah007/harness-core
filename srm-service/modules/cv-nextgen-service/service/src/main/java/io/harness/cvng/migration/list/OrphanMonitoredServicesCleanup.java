/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.migration.list;

import static io.harness.cvng.core.jobs.EntityChangeEventMessageProcessor.ENTITIES_MAP;

import io.harness.cvng.client.NextGenService;
import io.harness.cvng.core.entities.MonitoredService;
import io.harness.cvng.core.services.api.DeleteEntityByHandler;
import io.harness.ng.core.dto.ProjectDTO;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;
import io.harness.persistence.PersistentEntity;

import com.google.inject.Inject;
import com.google.inject.Injector;
import dev.morphia.query.Query;
import java.util.HashSet;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OrphanMonitoredServicesCleanup extends CVNGBaseMigration {
  @Inject private HPersistence hPersistence;
  @Inject private NextGenService nextGenService;
  @Inject private Injector injector;

  @Override
  public void migrate() {
    log.info("Starting cleanup for orphan monitored services.");
    Query<MonitoredService> queryToGetMonitoredServices =
        hPersistence.createQuery(MonitoredService.class, new HashSet<>());
    try (HIterator<MonitoredService> iterator = new HIterator<>(queryToGetMonitoredServices.fetch())) {
      while (iterator.hasNext()) {
        MonitoredService monitoredService = iterator.next();
        log.info("Checking status for monitored service {}.", monitoredService.getUuid());
        boolean isProjectDeleted = true;
        try {
          ProjectDTO projectDTO = nextGenService.getProject(monitoredService.getAccountId(),
              monitoredService.getOrgIdentifier(), monitoredService.getProjectIdentifier());
          isProjectDeleted = projectDTO == null;
        } catch (Exception exception) {
        }
        if (isProjectDeleted) {
          log.info("Monitored service {} is orphan", monitoredService.getUuid());
          cleanupMonitoredServiceData(monitoredService);
        } else {
          log.info("Monitored service {} is valid", monitoredService.getUuid());
        }
        log.info("Completed check for monitored service {}.", monitoredService.getUuid());
      }
    }
    log.info("Completed cleanup for orphan monitored services.");
  }
  private void cleanupMonitoredServiceData(MonitoredService monitoredService) {
    ENTITIES_MAP.forEach((entity, handler) -> deleteEntity(monitoredService, entity, handler));
  }

  private void deleteEntity(MonitoredService monitoredService, Class<? extends PersistentEntity> entity,
      Class<? extends DeleteEntityByHandler> handler) {
    try {
      log.info("Deleting all records of entity {} for monitored service {}.", entity.getCanonicalName(),
          monitoredService.getIdentifier());
      injector.getInstance(handler).deleteByProjectIdentifier(entity, monitoredService.getAccountId(),
          monitoredService.getOrgIdentifier(), monitoredService.getProjectIdentifier());
    } catch (Exception e) {
      log.error("Exception while deleting entity {} for monitored service {}.", entity.getCanonicalName(),
          monitoredService.getIdentifier(), e);
    }
  }
}
