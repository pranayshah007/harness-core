/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.servicelevelobjective.transformer.servicelevelobjectivev2;

import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveDTO;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveV2DTO;
import io.harness.cvng.servicelevelobjective.entities.AbstractServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelObjective;

public interface SLOV2Transformer<E extends AbstractServiceLevelObjective> {
  E getSLOV2(ProjectParams projectParams, ServiceLevelObjectiveV2DTO serviceLevelObjectiveV2DTO, Boolean isEnabled);

  E getSLOV2(ServiceLevelObjective serviceLevelObjective);

  ServiceLevelObjectiveV2DTO getSLOV2DTO(E serviceLevelObjective);

  ServiceLevelObjectiveV2DTO getSLOV2DTO(ServiceLevelObjectiveDTO serviceLevelObjectiveDTO);
}
