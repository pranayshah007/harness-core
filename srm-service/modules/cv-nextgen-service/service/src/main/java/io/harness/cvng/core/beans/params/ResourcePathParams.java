/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans.params;

import static io.harness.cvng.core.services.CVNextGenConstants.ACCOUNT_IDENTIFIER_KEY;
import static io.harness.cvng.core.services.CVNextGenConstants.ORG_IDENTIFIER_KEY;
import static io.harness.cvng.core.services.CVNextGenConstants.PROJECT_IDENTIFIER_KEY;
import static io.harness.cvng.core.services.CVNextGenConstants.RESOURCE_IDENTIFIER_KEY;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.accesscontrol.ResourceIdentifier;

import io.swagger.v3.oas.annotations.Parameter;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.PathParam;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResourcePathParams {
  @Parameter(description = NGCommonEntityConstants.IDENTIFIER_PARAM_MESSAGE)
  @ResourceIdentifier
  @PathParam(RESOURCE_IDENTIFIER_KEY)
  @Valid
  String identifier;
  @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE)
  @AccountIdentifier
  @PathParam(ACCOUNT_IDENTIFIER_KEY)
  @NotNull
  @Valid
  String accountIdentifier;
  @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE)
  @OrgIdentifier
  @PathParam(ORG_IDENTIFIER_KEY)
  @Valid
  String orgIdentifier;
  @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE)
  @ProjectIdentifier
  @PathParam(PROJECT_IDENTIFIER_KEY)
  @Valid
  String projectIdentifier;
}
