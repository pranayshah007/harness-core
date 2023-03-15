/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.dto;

import static io.harness.annotations.dev.HarnessTeam.PL;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.validator.EntityIdentifier;
import io.harness.data.validator.NGEntityName;
import io.harness.ng.core.notification.NotificationSettingConfigDTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@OwnedBy(PL)
@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
@Schema(name = "UserGroupRequestV2", description = "User Group details defined in Harness.")
public class UserGroupRequestV2DTO {
  @Schema(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) String accountIdentifier;
  @Schema(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE)
  @EntityIdentifier(allowBlank = true)
  String orgIdentifier;
  @Schema(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE)
  @EntityIdentifier(allowBlank = true)
  String projectIdentifier;
  @Schema(description = "Identifier of the UserGroup.") @NotNull @EntityIdentifier String identifier;

  @Schema(description = "Name of the UserGroup.") @NotNull @NGEntityName String name;
  @Schema(description = "List of users emails in the UserGroup. Maximum users can be 5000.")
  @Size(max = 5000)
  List<String> users;
  @Schema(description = "List of notification settings.") List<NotificationSettingConfigDTO> notificationConfigs;

  @Schema(description = "Specifies whether or not the userGroup is linked via SSO or not.") private boolean isSsoLinked;
  @Schema(description = "Identifier of the linked SSO.") private String linkedSsoId;
  @Schema(description = "Name of the linked SSO.") private String linkedSsoDisplayName;
  @Schema(description = "Identifier of the userGroup in SSO.") private String ssoGroupId;
  @Schema(description = "Name of the SSO userGroup.") private String ssoGroupName;
  @Schema(description = "Type of linked SSO") private String linkedSsoType;

  @Schema(description = "Specifies whether or not the userGroup is externally managed.")
  private boolean externallyManaged;

  @Schema(description = NGCommonEntityConstants.DESCRIPTION) @Size(max = 1024) String description;
  @Schema(description = NGCommonEntityConstants.TAGS) @Size(max = 128) Map<String, String> tags;
  @Schema(description = "Specifies whether or not the userGroup is managed by harness.") private boolean harnessManaged;
}
