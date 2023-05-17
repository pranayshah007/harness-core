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
public class TemplateCreateEvent implements Event {
  private String accountIdentifier;
  private String orgIdentifier;
  private String projectIdentifier;
  private TemplateEntity templateEntity;
  private String comments;

  public TemplateCreateEvent(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      TemplateEntity templateEntity, String comments) {
    this.accountIdentifier = accountIdentifier;
    this.orgIdentifier = orgIdentifier;
    this.projectIdentifier = projectIdentifier;
    this.templateEntity = templateEntity;
    this.comments = comments;
  }

  @JsonIgnore
  @Override
  public ResourceScope getResourceScope() {
    if (templateEntity.getTemplateScope().equals(Scope.PROJECT)) {
      return new ProjectScope(accountIdentifier, orgIdentifier, projectIdentifier);
    } else if (templateEntity.getTemplateScope().equals(Scope.ORG)) {
      return new OrgScope(accountIdentifier, orgIdentifier);
    } else {
      return new AccountScope(accountIdentifier);
    }
  }

  @JsonIgnore
  @Override
  public Resource getResource() {
    Map<String, String> labels = new HashMap<>();
    labels.put(ResourceConstants.LABEL_KEY_RESOURCE_NAME, templateEntity.getName());
    labels.put("versionLabel", templateEntity.getVersionLabel());
    labels.put("templateEntityType", templateEntity.getTemplateEntityType().toString());
    return Resource.builder()
        .identifier(templateEntity.getIdentifier())
        .type(ResourceTypeConstants.TEMPLATE)
        .labels(labels)
        .build();
  }

  @JsonIgnore
  @Override
  public String getEventType() {
    return TemplateOutboxEvents.TEMPLATE_VERSION_CREATED;
  }
}
