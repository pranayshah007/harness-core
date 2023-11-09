/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.roleassignments.events;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.audit.ResourceTypeConstants.ROLE_ASSIGNMENT;

import io.harness.accesscontrol.roleassignments.RoleAssignment;
import io.harness.accesscontrol.scopes.AccessControlResourceScope;
import io.harness.annotations.dev.OwnedBy;
import io.harness.event.Event;
import io.harness.ng.core.Resource;
import io.harness.ng.core.ResourceScope;

import lombok.Getter;
import lombok.NoArgsConstructor;

@OwnedBy(PL)
@Getter
@NoArgsConstructor
public class RoleAssignmentCreateEventV2 implements Event {
  public static final String ROLE_ASSIGNMENT_CREATE_EVENT_V2 = "RoleAssignmentCreated";
  private RoleAssignment roleAssignment;
  private String scope;

  public RoleAssignmentCreateEventV2(RoleAssignment roleAssignment, String scope) {
    this.roleAssignment = roleAssignment;
    this.scope = scope;
  }

  @Override
  public ResourceScope getResourceScope() {
    return new AccessControlResourceScope(scope);
  }

  @Override
  public Resource getResource() {
    return Resource.builder().identifier(roleAssignment.getIdentifier()).type(ROLE_ASSIGNMENT).build();
  }

  @Override
  public String getEventType() {
    return ROLE_ASSIGNMENT_CREATE_EVENT_V2;
  }
}
