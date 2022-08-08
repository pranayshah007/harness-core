/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.events.servicelevelobjective;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.ResourceTypeConstants;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveDTO;
import io.harness.ng.core.Resource;
import io.harness.ng.core.ResourceConstants;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@OwnedBy(HarnessTeam.CV)
@Getter
@SuperBuilder
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ServiceLevelObjectiveCreateEvent extends AbstractServiceLevelObjectiveEvent {
  private String resourceName;

  private String serviceLevelObjectiveIdentifier;

  private ServiceLevelObjectiveDTO newServiceLevelObjectiveDTO;

  private ServiceLevelObjectiveDTO oldServiceLevelObjectiveDTO;

  @Override
  public Resource getResource() {
    Map<String, String> labels = new HashMap<>();
    labels.put(ResourceConstants.LABEL_KEY_RESOURCE_NAME, resourceName);
    return Resource.builder()
        .identifier(serviceLevelObjectiveIdentifier)
        .labels(labels)
        .type(ResourceTypeConstants.SERVICE_LEVEL_OBJECTIVE)
        .build();
  }

  @Override
  public String getEventType() {
    return ServiceLevelObjectiveEventTypes.CREATE.toString();
  }
}
