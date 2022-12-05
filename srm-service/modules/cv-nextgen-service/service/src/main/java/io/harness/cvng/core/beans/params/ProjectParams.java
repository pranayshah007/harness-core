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
import javax.validation.constraints.NotNull;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectParams {
  @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE)
  @AccountIdentifier
  @QueryParam("accountId")
  @NotNull
  String accountIdentifier;
  @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE)
  @OrgIdentifier
  @QueryParam("orgIdentifier")
  @NotNull
  String orgIdentifier;
  @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE)
  @ProjectIdentifier
  @QueryParam("projectIdentifier")
  @NotNull
  String projectIdentifier;
}
