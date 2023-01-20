/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.downtime.services.api;

import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.services.api.DeleteEntityByHandler;
import io.harness.cvng.downtime.beans.EntityUnavailabilityStatusesDTO;
import io.harness.cvng.downtime.entities.EntityUnavailabilityStatuses;

public interface EntityUnavailabilityStatusesService extends DeleteEntityByHandler<EntityUnavailabilityStatuses> {
  EntityUnavailabilityStatusesDTO create(
      ProjectParams projectParams, EntityUnavailabilityStatusesDTO entityUnavailabilityStatusesDTO);

  EntityUnavailabilityStatusesDTO get(ProjectParams projectParams, String entityId);

  EntityUnavailabilityStatusesDTO update(
      ProjectParams projectParams, String entityId, EntityUnavailabilityStatusesDTO entityUnavailabilityStatusesDTO);

  boolean delete(ProjectParams projectParams, String entityId);
}
