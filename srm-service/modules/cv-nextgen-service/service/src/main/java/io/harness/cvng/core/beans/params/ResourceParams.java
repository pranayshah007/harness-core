/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans.params;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.ResourceIdentifier;

import io.swagger.v3.oas.annotations.Parameter;
import javax.validation.Valid;
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
public class ResourceParams extends ProjectParams {
  @Parameter(description = NGCommonEntityConstants.IDENTIFIER_PARAM_MESSAGE)
  @ResourceIdentifier
  @QueryParam("identifier")
  @Valid
  String identifier;
}
