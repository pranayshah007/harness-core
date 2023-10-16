/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.yaml.core.variables.v1;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;

import com.fasterxml.jackson.annotation.JsonProperty;

@OwnedBy(HarnessTeam.PIPELINE)
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
public enum NGVariableTypeV1 {
  @JsonProperty(NGVariableConstantsV1.STRING_TYPE) STRING(NGVariableConstantsV1.STRING_TYPE),
  @JsonProperty(NGVariableConstantsV1.NUMBER_TYPE) NUMBER(NGVariableConstantsV1.NUMBER_TYPE),
  @JsonProperty(NGVariableConstantsV1.SECRET_TYPE) SECRET(NGVariableConstantsV1.SECRET_TYPE),
  @JsonProperty(NGVariableConstantsV1.ARRAY_TYPE) ARRAY(NGVariableConstantsV1.ARRAY_TYPE),
  @JsonProperty(NGVariableConstantsV1.OBJECT_TYPE) OBJECT(NGVariableConstantsV1.OBJECT_TYPE),
  @JsonProperty(NGVariableConstantsV1.BOOLEAN_TYPE) BOOLEAN(NGVariableConstantsV1.BOOLEAN_TYPE);

  private final String yamlProperty;

  NGVariableTypeV1(String yamlProperty) {
    this.yamlProperty = yamlProperty;
  }
}
