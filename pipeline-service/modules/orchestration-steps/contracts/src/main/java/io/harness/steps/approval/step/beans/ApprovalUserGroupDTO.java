/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.approval.step.beans;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.validator.EntityIdentifier;
import io.harness.data.validator.NGEntityName;
import io.harness.ng.core.dto.UserGroupDTO;

import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@OwnedBy(CDC)
@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(name = "ApprovalUserGroup", description = "User Group details used in Approvals.")
public class ApprovalUserGroupDTO {
  @Schema(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) String accountIdentifier;
  @Schema(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE)
  @EntityIdentifier(allowBlank = true)
  String orgIdentifier;
  @Schema(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE)
  @EntityIdentifier(allowBlank = true)
  String projectIdentifier;
  @Schema(description = "Identifier of the UserGroup.") @NotNull @EntityIdentifier String identifier;

  @Schema(description = "Name of the UserGroup.") @NotNull @NGEntityName String name;

  public static ApprovalUserGroupDTO toApprovalUserGroupDTO(UserGroupDTO userGroupDTO) {
    return ApprovalUserGroupDTO.builder()
        .accountIdentifier(userGroupDTO.getAccountIdentifier())
        .orgIdentifier(userGroupDTO.getOrgIdentifier())
        .projectIdentifier(userGroupDTO.getProjectIdentifier())
        .identifier(userGroupDTO.getIdentifier())
        .name(userGroupDTO.getName())
        .build();
  }
}
