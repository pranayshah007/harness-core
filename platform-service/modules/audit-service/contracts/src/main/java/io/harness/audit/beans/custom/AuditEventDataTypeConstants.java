/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.audit.beans.custom;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

@OwnedBy(PL)
@UtilityClass
public class AuditEventDataTypeConstants {
  public static final String USER_INVITATION_AUDIT_EVENT_DATA = "UserInvitationAuditEventData";
  public static final String ADD_COLLABORATOR_AUDIT_EVENT_DATA = "AddCollaboratorAuditEventData";
  public static final String TEMPLATE_AUDIT_EVENT_DATA = "TemplateAuditEventData";
  public static final String FILE_AUDIT_EVENT_DATA = "FileAuditEventData";
  public static final String OPA_AUDIT_EVENT_DATA = "OpaAuditEventData";
  public static final String CHAOS_AUDIT_EVENT_DATA = "ChaosAuditEventData";
  public static final String FEATURE_FLAG_AUDIT_EVENT_DATA = "FeatureFlagAuditEventData";

  // Deprecated
  public static final String USER_INVITE = "USER_INVITE";
  public static final String USER_MEMBERSHIP = "USER_MEMBERSHIP";
  public static final String NODE_EXECUTION_EVENT_DATA = "NodeExecutionEventData";
}
