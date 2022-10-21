/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.views.entities;

import io.harness.NGCommonEntityConstants;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(name = "PolicyRequest", description = "This has the query to list the policies")
public class PolicyRequest {
  @Schema(description = "name") String name;
  @Schema(description = "account id") String accountId;
  @Schema(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) String orgIdentifier;
  @Schema(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) String projectIdentifier;
  @Schema(description = NGCommonEntityConstants.RESOURCE) String resource;
  @Schema(description = NGCommonEntityConstants.TAGS) String tags;
  @Schema(description = "cloudProvider") String cloudProvider;
  @Schema(description = "isStablePolicy") String isStablePolicy;
  @Schema(description = "isOOTBPolicy") String isOOTBPolicy;

  @Builder
  public PolicyRequest(String name, String accountId, String orgIdentifier, String projectIdentifier, String resource,
      String tags, String cloudProvider, String isStablePolicy, String isOOTBPolicy) {
    this.name = name;
    this.accountId = accountId;
    this.orgIdentifier = orgIdentifier;
    this.projectIdentifier = projectIdentifier;
    this.resource = resource;
    this.tags = tags;
    this.cloudProvider = cloudProvider;
    this.isStablePolicy = isStablePolicy;
    this.isOOTBPolicy = isOOTBPolicy;
  }
}
