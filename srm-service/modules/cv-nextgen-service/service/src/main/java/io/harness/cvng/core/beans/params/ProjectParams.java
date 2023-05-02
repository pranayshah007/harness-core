/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans.params;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;

import io.swagger.v3.oas.annotations.Parameter;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ProjectParams {
  @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE)
  @AccountIdentifier
  @QueryParam("accountId")
  @NotNull
  @Valid
  String accountIdentifier;
  @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE)
  @OrgIdentifier
  @QueryParam("orgIdentifier")
  @Valid
  String orgIdentifier;
  @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE)
  @ProjectIdentifier
  @QueryParam("projectIdentifier")
  @Valid
  String projectIdentifier;

  public static ProjectParams fromProjectPathParams(ProjectPathParams projectPathParams) {
    return ProjectParams.builder()
        .accountIdentifier(projectPathParams.getAccountIdentifier())
        .orgIdentifier(projectPathParams.getOrgIdentifier())
        .projectIdentifier(projectPathParams.getProjectIdentifier())
        .build();
  }

  public static ProjectParams fromResourcePathParams(ResourcePathParams resourcePathParams) {
    return ProjectParams.builder()
        .accountIdentifier(resourcePathParams.getAccountIdentifier())
        .orgIdentifier(resourcePathParams.getOrgIdentifier())
        .projectIdentifier(resourcePathParams.getProjectIdentifier())
        .build();
  }
}
