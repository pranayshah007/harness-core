/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.events;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.audit.ResourceTypeConstants.PROJECT;

import io.harness.annotations.dev.OwnedBy;
import io.harness.event.Event;
import io.harness.ng.core.ProjectScope;
import io.harness.ng.core.Resource;
import io.harness.ng.core.ResourceConstants;
import io.harness.ng.core.ResourceScope;
import io.harness.ng.core.dto.ProjectDTO;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;

@OwnedBy(PL)
@Getter
@NoArgsConstructor
public class ProjectCreateEvent implements Event {
  private ProjectDTO project;
  private String accountIdentifier;
  private String uniqueId;

  public ProjectCreateEvent(String accountIdentifier, ProjectDTO project, String uniqueId) {
    this.project = project;
    this.accountIdentifier = accountIdentifier;
    this.uniqueId = uniqueId;
  }

  @JsonIgnore
  @Override
  public ResourceScope getResourceScope() {
    return new ProjectScope(accountIdentifier, project.getOrgIdentifier(), project.getIdentifier());
  }

  @JsonIgnore
  @Override
  public Resource getResource() {
    Map<String, String> labels = new HashMap<>();
    labels.put(ResourceConstants.LABEL_KEY_RESOURCE_NAME, project.getName());
    return Resource.builder()
        .identifier(project.getIdentifier())
        .type(PROJECT)
        .labels(labels)
        .uniqueId(uniqueId)
        .build();
  }

  @JsonIgnore
  @Override
  public String getEventType() {
    return "ProjectCreated";
  }
}
