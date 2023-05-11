/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.template.events;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.ResourceTypeConstants;
import io.harness.encryption.Scope;
import io.harness.event.Event;
import io.harness.ng.core.AccountScope;
import io.harness.ng.core.OrgScope;
import io.harness.ng.core.ProjectScope;
import io.harness.ng.core.Resource;
import io.harness.ng.core.ResourceConstants;
import io.harness.ng.core.ResourceScope;
import io.harness.template.entity.TemplateEntity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;

@OwnedBy(CDC)
@Getter
@NoArgsConstructor
public class TemplateUpdateEvent implements Event {
  private String accountIdentifier;
  private String orgIdentifier;
  private String projectIdentifier;
  private TemplateEntity newTemplateEntity;
  private TemplateEntity oldTemplateEntity;
  private String comments;
  private TemplateUpdateEventType templateUpdateEventType;

  public TemplateUpdateEvent(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      TemplateEntity newTemplateEntity, TemplateEntity oldTemplateEntity, String comments,
      TemplateUpdateEventType templateUpdateEventType) {
    this.accountIdentifier = accountIdentifier;
    this.orgIdentifier = orgIdentifier;
    this.projectIdentifier = projectIdentifier;
    this.newTemplateEntity = newTemplateEntity;
    this.oldTemplateEntity = oldTemplateEntity;
    this.comments = comments;
    this.templateUpdateEventType = templateUpdateEventType;
  }

  @JsonIgnore
  @Override
  public ResourceScope getResourceScope() {
    if (newTemplateEntity.getTemplateScope().equals(Scope.PROJECT)) {
      return new ProjectScope(accountIdentifier, orgIdentifier, projectIdentifier);
    } else if (newTemplateEntity.getTemplateScope().equals(Scope.ORG)) {
      return new OrgScope(accountIdentifier, orgIdentifier);
    } else {
      return new AccountScope(accountIdentifier);
    }
  }

  @JsonIgnore
  @Override
  public Resource getResource() {
    Map<String, String> labels = new HashMap<>();
    labels.put(ResourceConstants.LABEL_KEY_RESOURCE_NAME, newTemplateEntity.getName());
    labels.put("versionLabel", newTemplateEntity.getVersionLabel());
    labels.put("templateEntityType", newTemplateEntity.getTemplateEntityType().toString());
    return Resource.builder()
        .identifier(newTemplateEntity.getIdentifier())
        .type(ResourceTypeConstants.TEMPLATE)
        .labels(labels)
        .build();
  }

  @JsonIgnore
  @Override
  public String getEventType() {
    return TemplateOutboxEvents.TEMPLATE_VERSION_UPDATED;
  }
}
