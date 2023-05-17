/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.migration.list;

import io.harness.cvng.client.NextGenService;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.migration.CVNGMigration;
import io.harness.cvng.migration.beans.ChecklistItem;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveType;
import io.harness.cvng.servicelevelobjective.entities.AbstractServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.AbstractServiceLevelObjective.ServiceLevelObjectiveV2Keys;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator.ServiceLevelIndicatorKeys;
import io.harness.cvng.servicelevelobjective.entities.SimpleServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelIndicatorService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelObjectiveV2Service;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DeleteRedundantSLIsSLOs implements CVNGMigration {
  @Inject private HPersistence hPersistence;
  @Inject private NextGenService nextGenService;
  @Inject private ServiceLevelObjectiveV2Service serviceLevelObjectiveService;
  @Inject private ServiceLevelIndicatorService serviceLevelIndicatorService;

  @Override
  public void migrate() {
    log.info("Begin migration for deleting SLOs/SLIs not associated with each other");
    List<String> toBeDeletedSLOsUuids = new ArrayList<>();
    Query<AbstractServiceLevelObjective> serviceLevelObjectiveQuery =
        hPersistence.createQuery(AbstractServiceLevelObjective.class)
            .filter(AbstractServiceLevelObjective.ServiceLevelObjectiveV2Keys.type, ServiceLevelObjectiveType.SIMPLE);
    try (HIterator<AbstractServiceLevelObjective> iterator = new HIterator<>(serviceLevelObjectiveQuery.fetch())) {
      while (iterator.hasNext()) {
        AbstractServiceLevelObjective serviceLevelObjective = iterator.next();
        if (nextGenService.isProjectDeleted(serviceLevelObjective.getAccountId(),
                serviceLevelObjective.getOrgIdentifier(), serviceLevelObjective.getProjectIdentifier())) {
          toBeDeletedSLOsUuids.add(serviceLevelObjective.getUuid());
          log.info("Added SLO with uuid {} for deletion", serviceLevelObjective.getUuid());
          continue;
        }
        ProjectParams projectParams = ProjectParams.builder()
                                          .accountIdentifier(serviceLevelObjective.getAccountId())
                                          .orgIdentifier(serviceLevelObjective.getOrgIdentifier())
                                          .projectIdentifier(serviceLevelObjective.getProjectIdentifier())
                                          .build();
        if (serviceLevelIndicatorService
                .getEntities(
                    projectParams, ((SimpleServiceLevelObjective) serviceLevelObjective).getServiceLevelIndicators())
                .isEmpty()) {
          serviceLevelObjectiveService.forceDelete(projectParams, serviceLevelObjective.getIdentifier());
          toBeDeletedSLOsUuids.add(serviceLevelObjective.getUuid());
          log.info("Added SLO with uuid {} for deletion", serviceLevelObjective.getUuid());
        }
      }
    }
    hPersistence.delete(hPersistence.createQuery(AbstractServiceLevelObjective.class)
                            .field(ServiceLevelObjectiveV2Keys.uuid)
                            .in(toBeDeletedSLOsUuids));

    List<String> toBeDeletedSLIsUuids = new ArrayList<>();
    Query<ServiceLevelIndicator> serviceLevelIndicatorQuery = hPersistence.createQuery(ServiceLevelIndicator.class);
    try (HIterator<ServiceLevelIndicator> iterator = new HIterator<>(serviceLevelIndicatorQuery.fetch())) {
      while (iterator.hasNext()) {
        ServiceLevelIndicator serviceLevelIndicator = iterator.next();
        if (nextGenService.isProjectDeleted(serviceLevelIndicator.getAccountId(),
                serviceLevelIndicator.getOrgIdentifier(), serviceLevelIndicator.getProjectIdentifier())) {
          toBeDeletedSLIsUuids.add(serviceLevelIndicator.getUuid());
          log.info("Added SLI with uuid {} for deletion", serviceLevelIndicator.getUuid());
          continue;
        }
        ProjectParams projectParams = ProjectParams.builder()
                                          .accountIdentifier(serviceLevelIndicator.getAccountId())
                                          .orgIdentifier(serviceLevelIndicator.getOrgIdentifier())
                                          .projectIdentifier(serviceLevelIndicator.getProjectIdentifier())
                                          .build();
        if (serviceLevelObjectiveService.getFromSLIIdentifier(projectParams, serviceLevelIndicator.getIdentifier())
            == null) {
          toBeDeletedSLIsUuids.add(serviceLevelIndicator.getUuid());
          log.info("Added SLI with uuid {} for deletion", serviceLevelIndicator.getUuid());
        }
      }
    }
    hPersistence.delete(hPersistence.createQuery(ServiceLevelIndicator.class)
                            .field(ServiceLevelIndicatorKeys.uuid)
                            .in(toBeDeletedSLIsUuids));
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
