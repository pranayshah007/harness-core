/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.roles.events;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.audit.ResourceTypeConstants.ROLE;

import io.harness.accesscontrol.roles.Role;
import io.harness.accesscontrol.scopes.AccessControlResourceScope;
import io.harness.annotations.dev.OwnedBy;
import io.harness.event.Event;
import io.harness.ng.core.Resource;
import io.harness.ng.core.ResourceConstants;
import io.harness.ng.core.ResourceScope;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;

@OwnedBy(PL)
@Getter
@NoArgsConstructor
public class RoleUpdateEventV2 implements Event {
  public static final String ROLE_UPDATE_EVENT = "RoleUpdated";
  private String scope;
  private Role newRole;
  private Role oldRole;

  public RoleUpdateEventV2(String scope, Role oldRole, Role newRole) {
    this.scope = scope;
    this.oldRole = oldRole;
    this.newRole = newRole;
  }

  @JsonIgnore
  @Override
  public ResourceScope getResourceScope() {
    return new AccessControlResourceScope(scope);
  }

  @JsonIgnore
  @Override
  public Resource getResource() {
    Map<String, String> labels = new HashMap<>();
    labels.put(ResourceConstants.LABEL_KEY_RESOURCE_NAME, newRole.getName());
    return Resource.builder().identifier(newRole.getIdentifier()).type(ROLE).labels(labels).build();
  }

  @JsonIgnore
  @Override
  public String getEventType() {
    return ROLE_UPDATE_EVENT;
  }
}
