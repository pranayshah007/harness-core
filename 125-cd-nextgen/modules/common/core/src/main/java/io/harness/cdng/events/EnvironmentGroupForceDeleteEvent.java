/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cdng.events;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.audit.ResourceTypeConstants.ENVIRONMENT_GROUP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.envGroup.beans.EnvironmentGroupEntity;
import io.harness.event.Event;
import io.harness.ng.core.AccountScope;
import io.harness.ng.core.OrgScope;
import io.harness.ng.core.ProjectScope;
import io.harness.ng.core.Resource;
import io.harness.ng.core.ResourceScope;
import io.harness.ng.core.events.OutboxEventConstants;

import com.fasterxml.jackson.annotation.JsonIgnore;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@OwnedBy(CDC)
@Getter
@Builder
@AllArgsConstructor

public class EnvironmentGroupForceDeleteEvent implements Event {
  private String accountIdentifier;
  private String orgIdentifier;
  private String projectIdentifier;
  @NotNull private EnvironmentGroupEntity environmentGroupEntity;

  @JsonIgnore
  @Override
  public ResourceScope getResourceScope() {
    if (isEmpty(environmentGroupEntity.getOrgIdentifier())) {
      return new AccountScope(accountIdentifier);
    } else if (isEmpty(environmentGroupEntity.getProjectIdentifier())) {
      return new OrgScope(accountIdentifier, environmentGroupEntity.getOrgIdentifier());
    }
    return new ProjectScope(
        accountIdentifier, environmentGroupEntity.getOrgIdentifier(), environmentGroupEntity.getProjectIdentifier());
  }

  @JsonIgnore
  @Override
  public Resource getResource() {
    return Resource.builder().identifier(environmentGroupEntity.getIdentifier()).type(ENVIRONMENT_GROUP).build();
  }

  @JsonIgnore
  @Override
  public String getEventType() {
    return OutboxEventConstants.ENVIRONMENT_GROUP_FORCE_DELETED;
  }
}
