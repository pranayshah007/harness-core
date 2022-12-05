/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.api;

import static io.harness.annotations.dev.HarnessTeam.CV;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;

import java.util.List;

@OwnedBy(CV)
public interface SetupUsageEventService {
  void sendCreateEventsForMonitoredService(ProjectParams projectParams, MonitoredServiceDTO monitoredServiceDTO);

  void sendDeleteEventsForMonitoredService(ProjectParams projectParams, String identifier);

  void sendEvents(String accountId, EntityDetailProtoDTO referredByEntity, List<EntityDetailProtoDTO> referredEntities,
      EntityTypeProtoEnum referredEntityType);
}
