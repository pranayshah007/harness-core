/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.downtime.transformer;

import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.downtime.beans.EntityUnavailabilityStatusesDTO;
import io.harness.cvng.downtime.entities.EntityUnavailabilityStatuses;

public class EntityUnavailabilityStatusesEntityAndDTOTransformer {
  public EntityUnavailabilityStatuses getEntity(
      ProjectParams projectParams, EntityUnavailabilityStatusesDTO entityUnavailabilityStatusesDTO) {
    return EntityUnavailabilityStatuses.builder()
        .accountId(projectParams.getAccountIdentifier())
        .orgIdentifier(projectParams.getOrgIdentifier())
        .projectIdentifier(projectParams.getProjectIdentifier())
        .entityIdentifier(entityUnavailabilityStatusesDTO.getEntityId())
        .entityType(entityUnavailabilityStatusesDTO.getEntityType())
        .startTime(entityUnavailabilityStatusesDTO.getStartTime())
        .endTime(entityUnavailabilityStatusesDTO.getEndTime())
        .status(entityUnavailabilityStatusesDTO.getStatus())
        .entitiesRule(entityUnavailabilityStatusesDTO.getEntitiesRule())
        .build();
  }

  public EntityUnavailabilityStatusesDTO getDto(EntityUnavailabilityStatuses entityUnavailabilityStatuses) {
    return EntityUnavailabilityStatusesDTO.builder()
        .orgIdentifier(entityUnavailabilityStatuses.getOrgIdentifier())
        .projectIdentifier(entityUnavailabilityStatuses.getProjectIdentifier())
        .entityId(entityUnavailabilityStatuses.getEntityIdentifier())
        .entityType(entityUnavailabilityStatuses.getEntityType())
        .startTime(entityUnavailabilityStatuses.getStartTime())
        .endTime(entityUnavailabilityStatuses.getEndTime())
        .status(entityUnavailabilityStatuses.getStatus())
        .entitiesRule(entityUnavailabilityStatuses.getEntitiesRule())
        .build();
  }
}