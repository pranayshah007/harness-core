/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.audit.beans;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.beans.custom.AuditEventDataTypeConstants;
import io.harness.audit.beans.custom.chaos.ChaosAuditEventData;
import io.harness.audit.beans.custom.executions.NodeExecutionEventData;
import io.harness.audit.beans.custom.ff.FeatureFlagAuditEventData;
import io.harness.audit.beans.custom.opa.OpaAuditEventData;
import io.harness.audit.beans.custom.template.TemplateEventData;
import io.harness.audit.beans.custom.user.AddCollaboratorAuditEventData;
import io.harness.audit.beans.custom.user.UserInvitationAuditEventData;
import io.harness.audit.beans.custom.user.UserInviteAuditEventData;
import io.harness.audit.beans.custom.user.UserMembershipAuditEventData;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.annotations.ApiModelProperty;
import javax.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.validator.constraints.NotBlank;

@OwnedBy(PL)
@Data
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type", visible = true)
@JsonSubTypes(value =
    {
      @JsonSubTypes.Type(value = UserInvitationAuditEventData.class,
          name = AuditEventDataTypeConstants.USER_INVITATION_AUDIT_EVENT_DATA)
      ,
          @JsonSubTypes.Type(value = AddCollaboratorAuditEventData.class,
              name = AuditEventDataTypeConstants.ADD_COLLABORATOR_AUDIT_EVENT_DATA),
          @JsonSubTypes.Type(
              value = TemplateEventData.class, name = AuditEventDataTypeConstants.TEMPLATE_AUDIT_EVENT_DATA),
          @JsonSubTypes.Type(value = OpaAuditEventData.class, name = AuditEventDataTypeConstants.OPA_AUDIT_EVENT_DATA),
          @JsonSubTypes.Type(
              value = ChaosAuditEventData.class, name = AuditEventDataTypeConstants.CHAOS_AUDIT_EVENT_DATA),
          @JsonSubTypes.Type(value = FeatureFlagAuditEventData.class,
              name = AuditEventDataTypeConstants.FEATURE_FLAG_AUDIT_EVENT_DATA),

          // Deprecated
          @JsonSubTypes.Type(value = UserInviteAuditEventData.class, name = AuditEventDataTypeConstants.USER_INVITE),
          @JsonSubTypes.Type(
              value = UserMembershipAuditEventData.class, name = AuditEventDataTypeConstants.USER_MEMBERSHIP),
          @JsonSubTypes.Type(
              value = NodeExecutionEventData.class, name = AuditEventDataTypeConstants.NODE_EXECUTION_EVENT_DATA)
    })
public abstract class AuditEventData {
  public static final String AUDIT_EVENT_DATA_TYPE = "io.harness.audit.beans.AuditEventDataType";

  @NotNull @NotBlank @ApiModelProperty(dataType = AUDIT_EVENT_DATA_TYPE) protected String type;
}
