/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.steps.stepinfo;

import static io.harness.annotations.dev.HarnessTeam.IACM;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.runtime;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.CIStepInfo;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.TypeInfo;
import io.harness.data.structure.CollectionUtils;
import io.harness.data.validator.EntityIdentifier;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.yaml.YamlSchemaTypes;
import io.harness.yaml.core.VariableExpression;
import io.harness.yaml.core.variables.OutputNGVariable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@Data
@NoArgsConstructor
@JsonTypeName("IACM")
@JsonIgnoreProperties(ignoreUnknown = true)
@TypeAlias("iacmStepInfo")
@OwnedBy(IACM)
@RecasterAlias("io.harness.beans.steps.stepinfo.IACMStepInfo")
public class IACMStepInfo implements CIStepInfo {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private String uuid;

  @VariableExpression(skipVariableExpression = true) public static final int DEFAULT_RETRY = 1;

  @JsonIgnore
  public static final StepType STEP_TYPE =
      StepType.newBuilder().setType(CIStepInfoType.IACM.getDisplayName()).setStepCategory(StepCategory.STEP).build();

  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  @NotNull
  @EntityIdentifier
  protected String identifier;
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) protected String name;
  @VariableExpression(skipVariableExpression = true) @Min(MIN_RETRY) @Max(MAX_RETRY) protected int retry;

  @YamlSchemaTypes(value = {runtime})
  @VariableExpression(skipVariableExpression = true)
  @ApiModelProperty(dataType = "[Lio.harness.yaml.core.variables.OutputNGVariable;")
  protected ParameterField<List<OutputNGVariable>> outputVariables;

  @VariableExpression(skipVariableExpression = true) protected static List<OutputNGVariable> defaultOutputVariables;

  static {
    defaultOutputVariables = Arrays.asList(OutputNGVariable.builder().name("JOB_ID").build(),
        OutputNGVariable.builder().name("JOB_STATUS").build(), OutputNGVariable.builder().name("CRITICAL").build(),
        OutputNGVariable.builder().name("HIGH").build(), OutputNGVariable.builder().name("MEDIUM").build(),
        OutputNGVariable.builder().name("LOW").build(), OutputNGVariable.builder().name("INFO").build(),
        OutputNGVariable.builder().name("UNASSIGNED").build(), OutputNGVariable.builder().name("TOTAL").build(),
        OutputNGVariable.builder().name("NEW_CRITICAL").build(), OutputNGVariable.builder().name("NEW_HIGH").build(),
        OutputNGVariable.builder().name("NEW_MEDIUM").build(), OutputNGVariable.builder().name("NEW_LOW").build(),
        OutputNGVariable.builder().name("NEW_INFO").build(), OutputNGVariable.builder().name("NEW_UNASSIGNED").build(),
        OutputNGVariable.builder().name("NEW_TOTAL").build());
  }

  @Override
  public TypeInfo getNonYamlInfo() {
    return TypeInfo.builder().stepInfoType(CIStepInfoType.IACM).build();
  }

  private String getTypeName() {
    return this.getClass().getAnnotation(JsonTypeName.class).value();
  }

  @Override
  public StepType getStepType() {
    return StepType.newBuilder().setType(getTypeName()).setStepCategory(StepCategory.STEP).build();
  }

  @Override
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.ASYNC;
  }

  public ParameterField<List<OutputNGVariable>> getOutputVariables() {
    return ParameterField.createValueField(
        Stream
            .concat(defaultOutputVariables.stream(),
                (CollectionUtils.emptyIfNull((List<OutputNGVariable>) outputVariables.fetchFinalValue())).stream())
            .collect(Collectors.toSet())
            .stream()
            .collect(Collectors.toList()));
  }
}
