/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.commons.outbox;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.audit.ResourceTypeConstants.ROLE;
import static io.harness.audit.ResourceTypeConstants.ROLE_ASSIGNMENT;

import io.harness.annotations.dev.OwnedBy;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxEventHandler;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Slf4j
public class AccessControlOutboxEventHandler implements OutboxEventHandler {
  private final RoleEventHandler roleEventHandler;
  private final RoleAssignmentEventHandler roleAssignmentEventHandler;

  @Inject
  public AccessControlOutboxEventHandler(
      RoleEventHandler roleEventHandler, RoleAssignmentEventHandler roleAssignmentEventHandler) {
    this.roleEventHandler = roleEventHandler;
    this.roleAssignmentEventHandler = roleAssignmentEventHandler;
  }

  @Override
  public boolean handle(OutboxEvent outboxEvent) {
    try {
      // TODO {karan} remove extra lowercase cases after some days
      switch (outboxEvent.getResource().getType()) {
        case ROLE:
        case "role":
          return roleEventHandler.handle(outboxEvent);
        case ROLE_ASSIGNMENT:
        case "roleassignment":
          return roleAssignmentEventHandler.handle(outboxEvent);
        default:
          return false;
      }
    } catch (Exception exception) {
      log.error(
          String.format("Unexpected error occurred during handling event of type %s", outboxEvent.getEventType()));
      return false;
    }
  }
}
