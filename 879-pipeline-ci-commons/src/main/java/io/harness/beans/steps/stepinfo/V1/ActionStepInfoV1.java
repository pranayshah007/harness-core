/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.steps.stepinfo.V1;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.beans.SwaggerConstants.STRING_CLASSPATH;
import static io.harness.beans.SwaggerConstants.STRING_LIST_CLASSPATH;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.runtime;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.string;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.TypeInfo;
import io.harness.filters.WithConnectorRef;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.YamlSchemaTypes;
import io.harness.yaml.core.VariableExpression;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.beans.ConstructorProperties;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = true)
@JsonTypeName("action")
@JsonIgnoreProperties(ignoreUnknown = true)
@OwnedBy(CI)
@RecasterAlias("io.harness.beans.steps.stepinfo.V1.ActionStepInfoV1")
public class ActionStepInfoV1 extends CIAbstractStepInfo implements WithConnectorRef {
  @JsonIgnore public static final TypeInfo typeInfo = TypeInfo.builder().stepInfoType(CIStepInfoType.ACTION_V1).build();

  @JsonIgnore
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(CIStepInfoType.ACTION_V1.getDisplayName())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  @NotNull @ApiModelProperty(dataType = STRING_CLASSPATH) ParameterField<String> uses;

  @VariableExpression(skipVariableExpression = true)
  @YamlSchemaTypes(value = {string})
  ParameterField<Map<String, String>> with;

  @VariableExpression(skipVariableExpression = true)
  @YamlSchemaTypes(value = {string})
  ParameterField<Map<String, String>> envs;

  @YamlSchemaTypes(value = {runtime})
  @ApiModelProperty(dataType = STRING_LIST_CLASSPATH)
  ParameterField<List<String>> outputs;

  @Builder
  @ConstructorProperties({"uuid", "uses", "with", "envs", "outputs"})
  public ActionStepInfoV1(String uuid, ParameterField<String> uses, ParameterField<Map<String, String>> with,
      ParameterField<Map<String, String>> envs, ParameterField<List<String>> outputs) {
    this.uuid = uuid;
    this.uses = uses;
    this.with = with;
    this.envs = envs;
    this.outputs = outputs;
  }

  @Override
  public TypeInfo getNonYamlInfo() {
    return typeInfo;
  }

  @Override
  public StepType getStepType() {
    return STEP_TYPE;
  }

  @Override
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.ASYNC;
  }

  @Override
  public Map<String, ParameterField<String>> extractConnectorRefs() {
    return new HashMap<>();
  }
}
