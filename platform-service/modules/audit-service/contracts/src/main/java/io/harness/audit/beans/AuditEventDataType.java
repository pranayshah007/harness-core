/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.audit.beans;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.beans.custom.AuditEventDataTypeConstants;

@OwnedBy(HarnessTeam.PL)
public enum AuditEventDataType {
  UserInvitationAuditEventData(AuditEventDataTypeConstants.USER_INVITATION_AUDIT_EVENT_DATA),
  AddCollaboratorAuditEventData(AuditEventDataTypeConstants.ADD_COLLABORATOR_AUDIT_EVENT_DATA),
  TemplateAuditEventData(AuditEventDataTypeConstants.TEMPLATE_AUDIT_EVENT_DATA),
  OpaAuditEventData(AuditEventDataTypeConstants.OPA_AUDIT_EVENT_DATA),

  // Deprecated
  USER_INVITE(AuditEventDataTypeConstants.USER_INVITE),
  USER_MEMBERSHIP(AuditEventDataTypeConstants.USER_MEMBERSHIP);

  AuditEventDataType(String auditEventDataType) {
    if (!auditEventDataType.equals(this.name())) {
      throw new IllegalArgumentException(
          String.format("AuditEventDataType enum: %s doesn't match constant: %s", this.name(), auditEventDataType));
    }
  }
}
