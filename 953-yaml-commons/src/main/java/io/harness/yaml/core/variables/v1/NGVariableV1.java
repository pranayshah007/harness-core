/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.yaml.core.variables;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXISTING_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.pms.yaml.ParameterField;
import io.harness.walktree.visitor.Visitable;
import io.harness.yaml.core.variables.v1.ArrayNGVariableV1;
import io.harness.yaml.core.variables.v1.NGVariableConstantsV1;
import io.harness.yaml.core.variables.v1.NGVariableTypeV1;
import io.harness.yaml.core.variables.v1.NumberNGVariableV1;
import io.harness.yaml.core.variables.v1.ObjectNGVariableV1;
import io.harness.yaml.core.variables.v1.SecretNGVariableV1;
import io.harness.yaml.core.variables.v1.StringNGVariableV1;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.annotations.ApiModelProperty;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@JsonTypeInfo(use = NAME, property = "type", include = EXISTING_PROPERTY, visible = true)
@JsonSubTypes({
  @Type(value = NumberNGVariableV1.class, name = NGVariableConstantsV1.NUMBER_TYPE)
  , @Type(value = StringNGVariableV1.class, name = NGVariableConstantsV1.STRING_TYPE),
      @Type(value = SecretNGVariableV1.class, name = NGVariableConstantsV1.SECRET_TYPE),
      @Type(value = ObjectNGVariableV1.class, name = NGVariableConstantsV1.OBJECT_TYPE),
      @Type(value = ArrayNGVariableV1.class, name = NGVariableConstantsV1.ARRAY_TYPE),
      @Type(value = ObjectNGVariableV1.class, name = NGVariableConstantsV1.OBJECT_TYPE)
})
public interface NGVariableV1 extends Visitable {
  NGVariableTypeV1 getType();
  String getDesc();
  boolean isRequired();
  boolean isExecution_input();
  @JsonIgnore @ApiModelProperty(hidden = true) ParameterField<?> fetchValue();
  @JsonIgnore @ApiModelProperty(hidden = true) ParameterField<?> getCurrentValue();
}
