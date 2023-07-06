/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.envGroup.dto;

import io.harness.NGCommonEntityConstants;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.ng.core.envGroup.constant.EnvironmentGroupConstants;
import io.harness.ng.core.environment.dto.EnvironmentResponse;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "EnvironmentGroupResponse", description = "This is the Environment Group Entity defined in Harness")
public class EnvironmentGroupResponseDTO {
  @Schema(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) String accountId;
  @Schema(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) String orgIdentifier;
  @Schema(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) String projectIdentifier;
  @Schema(description = NGCommonEntityConstants.IDENTIFIER_PARAM_MESSAGE) String identifier;
  @Schema(description = NGCommonEntityConstants.NAME_PARAM_MESSAGE) String name;
  @Schema(description = NGCommonEntityConstants.DESCRIPTION) String description;
  @Schema(description = NGCommonEntityConstants.COLOR_PARAM_MESSAGE) String color;
  @Schema(description = NGCommonEntityConstants.DELETED_PARAM_MESSAGE) boolean deleted;
  @Schema(description = NGCommonEntityConstants.TAGS) Map<String, String> tags;
  @Schema(description = NGCommonEntityConstants.VERSION_PARAM_MESSAGE) @JsonIgnore Long version;
  @Schema(description = EnvironmentGroupConstants.ENV_IDENTIFIERS_LIST_PARAM_MESSAGE) List<String> envIdentifiers;
  @Schema(description = EnvironmentGroupConstants.ENV_RESPONSE_PARAM_MESSAGE) List<EnvironmentResponse> envResponse;
  @Schema(description = "Yaml of the Environment Group") String yaml;

  // GitInfo
  @Schema(description = EnvironmentGroupConstants.GIT_DETAILS_MESSAGE) EntityGitDetails gitDetails;
}
