/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.events;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.audit.ResourceTypeConstants.ENVIRONMENT_GROUP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.envGroup.beans.EnvironmentGroupEntity;
import io.harness.event.Event;
import io.harness.ng.core.ProjectScope;
import io.harness.ng.core.Resource;
import io.harness.ng.core.ResourceScope;
import io.harness.ng.core.events.OutboxEventConstants;

import com.fasterxml.jackson.annotation.JsonIgnore;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@OwnedBy(PIPELINE)
@Getter
@Builder
@AllArgsConstructor
public class EnvironmentGroupUpdateEvent implements Event {
  private String accountIdentifier;
  private String orgIdentifier;
  private String projectIdentifier;
  @NotNull private EnvironmentGroupEntity newEnvironmentGroupEntity;
  @NotNull private EnvironmentGroupEntity oldEnvironmentGroupEntity;

  @JsonIgnore
  @Override
  public ResourceScope getResourceScope() {
    return new ProjectScope(accountIdentifier, newEnvironmentGroupEntity.getOrgIdentifier(),
        newEnvironmentGroupEntity.getProjectIdentifier());
  }

  @JsonIgnore
  @Override
  public Resource getResource() {
    return Resource.builder().identifier(newEnvironmentGroupEntity.getIdentifier()).type(ENVIRONMENT_GROUP).build();
  }

  @JsonIgnore
  @Override
  public String getEventType() {
    return OutboxEventConstants.ENVIRONMENT_GROUP_UPDATED;
  }
}
