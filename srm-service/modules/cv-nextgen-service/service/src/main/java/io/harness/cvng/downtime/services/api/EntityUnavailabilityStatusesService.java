/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.downtime.services.api;

import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.services.api.DeleteEntityByHandler;
import io.harness.cvng.downtime.beans.DowntimeDTO;
import io.harness.cvng.downtime.beans.EntityType;
import io.harness.cvng.downtime.beans.EntityUnavailabilityStatusesDTO;
import io.harness.cvng.downtime.entities.EntityUnavailabilityStatuses;

import java.util.List;
import org.apache.commons.lang3.tuple.Pair;

public interface EntityUnavailabilityStatusesService extends DeleteEntityByHandler<EntityUnavailabilityStatuses> {
  void create(ProjectParams projectParams, List<EntityUnavailabilityStatusesDTO> entityUnavailabilityStatusesDTOS);

  void update(ProjectParams projectParams, String entityId,
      List<EntityUnavailabilityStatusesDTO> entityUnavailabilityStatusesDTOS);

  void updateAndSaveRunningInstance(ProjectParams projectParams, String entityId);
  List<EntityUnavailabilityStatusesDTO> getEntityUnavaialabilityStatusesDTOs(
      ProjectParams projectParams, DowntimeDTO downtimeDTO, List<Pair<Long, Long>> futureInstances);
  List<EntityUnavailabilityStatusesDTO> getPastInstances(ProjectParams projectParams);

  EntityUnavailabilityStatuses getInstanceById(String uuid);

  List<EntityUnavailabilityStatusesDTO> getAllInstances(ProjectParams projectParams);

  List<EntityUnavailabilityStatusesDTO> getAllInstances(
      ProjectParams projectParams, EntityType entityType, String entityIdentifier);

  List<EntityUnavailabilityStatusesDTO> getAllInstances(ProjectParams projectParams, long startTime, long endTime);

  List<EntityUnavailabilityStatuses> getAllUnavailabilityInstances(
      ProjectParams projectParams, long startTime, long endTime);

  List<EntityUnavailabilityStatusesDTO> getActiveOrFirstUpcomingInstance(
      ProjectParams projectParams, List<String> entityIds);
  boolean deleteFutureDowntimeInstances(ProjectParams projectParams, String entityId);
  boolean deleteAllInstances(ProjectParams projectParams, String entityId);
}
