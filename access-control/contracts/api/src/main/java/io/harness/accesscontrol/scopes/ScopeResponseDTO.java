/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.scopes;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@OwnedBy(PL)
@Data
@Builder
@ApiModel(value = "ScopeResponse")
@Schema(name = "ScopeResponse")
public class ScopeResponseDTO {
  String accountIdentifier;
  String accountName;
  String orgIdentifier;
  String orgName;
  String projectIdentifier;
  String projectName;
}
