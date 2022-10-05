/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.services.api;

import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.services.api.DeleteEntityByHandler;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveV2DTO;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveV2Response;
import io.harness.cvng.servicelevelobjective.entities.AbstractServiceLevelObjective;

import java.util.List;

public interface ServiceLevelObjectiveV2Service extends DeleteEntityByHandler<AbstractServiceLevelObjective> {
  ServiceLevelObjectiveV2Response create(
      ProjectParams projectParams, ServiceLevelObjectiveV2DTO serviceLevelObjectiveDTO);

  ServiceLevelObjectiveV2Response update(ProjectParams projectParams, String identifier,
      ServiceLevelObjectiveV2DTO serviceLevelObjectiveDTO, List<String> serviceLevelIndicators);

  AbstractServiceLevelObjective getEntity(ProjectParams projectParams, String identifier);

  boolean delete(ProjectParams projectParams, String identifier);

  void setMonitoredServiceSLOsEnableFlag(
      ProjectParams projectParams, String monitoreServiceIdentifier, boolean isEnabled);

  void updateNotificationRuleRefInSLO(ProjectParams projectParams, AbstractServiceLevelObjective serviceLevelObjective,
      List<String> notificationRuleRefs);
}
