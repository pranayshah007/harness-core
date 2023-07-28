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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.annotations.ApiModelProperty;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@JsonTypeInfo(use = NAME, property = "type", include = EXISTING_PROPERTY, visible = true)
@JsonSubTypes({
  @Type(value = NumberNGVariable.class, name = NGVariableConstants.NUMBER_TYPE)
  , @Type(value = StringNGVariable.class, name = NGVariableConstants.STRING_TYPE),
      @Type(value = SecretNGVariable.class, name = NGVariableConstants.SECRET_TYPE)
})
public interface NGVariable extends Visitable {
  NGVariableType getType();
  String getName();
  String getDescription();
  boolean isRequired();
  @JsonIgnore @ApiModelProperty(hidden = true) ParameterField<?> fetchValue();
  @JsonIgnore @ApiModelProperty(hidden = true) ParameterField<?> getCurrentValue();
}
