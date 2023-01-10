/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.downtime.services.impl;

import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.downtime.beans.EntityUnavailabilityStatusesDTO;
import io.harness.cvng.downtime.entities.EntityUnavailabilityStatuses;
import io.harness.cvng.downtime.entities.EntityUnavailabilityStatuses.EntityUnavailabilityStatusesKeys;
import io.harness.cvng.downtime.services.api.EntityUnavailabilityStatusesService;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import java.util.List;

public class EntityUnavailabilityStatusesServiceImpl implements EntityUnavailabilityStatusesService {
  @Inject private HPersistence hPersistence;

  @Override
  public EntityUnavailabilityStatusesDTO create(
      ProjectParams projectParams, EntityUnavailabilityStatusesDTO entityUnavailabilityStatusesDTO) {
    return null;
  }

  @Override
  public EntityUnavailabilityStatusesDTO get(ProjectParams projectParams, String entityId) {
    return null;
  }

  @Override
  public EntityUnavailabilityStatusesDTO update(
      ProjectParams projectParams, String entityId, EntityUnavailabilityStatusesDTO entityUnavailabilityStatusesDTO) {
    return null;
  }

  @Override
  public boolean delete(ProjectParams projectParams, String entityId) {
    return false;
  }

  @Override
  public void deleteByProjectIdentifier(
      Class<EntityUnavailabilityStatuses> clazz, String accountId, String orgIdentifier, String projectIdentifier) {
    List<EntityUnavailabilityStatuses> entityUnavailabilityStatuses =
        hPersistence.createQuery(EntityUnavailabilityStatuses.class)
            .filter(EntityUnavailabilityStatusesKeys.accountId, accountId)
            .filter(EntityUnavailabilityStatusesKeys.orgIdentifier, orgIdentifier)
            .filter(EntityUnavailabilityStatusesKeys.projectIdentifier, projectIdentifier)
            .asList();
    entityUnavailabilityStatuses.forEach(entityUnavailabilityStatus
        -> delete(ProjectParams.builder()
                      .accountIdentifier(entityUnavailabilityStatus.getAccountId())
                      .orgIdentifier(entityUnavailabilityStatus.getOrgIdentifier())
                      .projectIdentifier(entityUnavailabilityStatus.getProjectIdentifier())
                      .build(),
            entityUnavailabilityStatus.getEntityId()));
  }

  @Override
  public void deleteByOrgIdentifier(Class<EntityUnavailabilityStatuses> clazz, String accountId, String orgIdentifier) {
    List<EntityUnavailabilityStatuses> entityUnavailabilityStatuses =
        hPersistence.createQuery(EntityUnavailabilityStatuses.class)
            .filter(EntityUnavailabilityStatusesKeys.accountId, accountId)
            .filter(EntityUnavailabilityStatusesKeys.orgIdentifier, orgIdentifier)
            .asList();
    entityUnavailabilityStatuses.forEach(entityUnavailabilityStatus
        -> delete(ProjectParams.builder()
                      .accountIdentifier(entityUnavailabilityStatus.getAccountId())
                      .orgIdentifier(entityUnavailabilityStatus.getOrgIdentifier())
                      .projectIdentifier(entityUnavailabilityStatus.getProjectIdentifier())
                      .build(),
            entityUnavailabilityStatus.getEntityId()));
  }

  @Override
  public void deleteByAccountIdentifier(Class<EntityUnavailabilityStatuses> clazz, String accountId) {
    List<EntityUnavailabilityStatuses> entityUnavailabilityStatuses =
        hPersistence.createQuery(EntityUnavailabilityStatuses.class)
            .filter(EntityUnavailabilityStatusesKeys.accountId, accountId)
            .asList();
    entityUnavailabilityStatuses.forEach(entityUnavailabilityStatus
        -> delete(ProjectParams.builder()
                      .accountIdentifier(entityUnavailabilityStatus.getAccountId())
                      .orgIdentifier(entityUnavailabilityStatus.getOrgIdentifier())
                      .projectIdentifier(entityUnavailabilityStatus.getProjectIdentifier())
                      .build(),
            entityUnavailabilityStatus.getEntityId()));
  }
}
