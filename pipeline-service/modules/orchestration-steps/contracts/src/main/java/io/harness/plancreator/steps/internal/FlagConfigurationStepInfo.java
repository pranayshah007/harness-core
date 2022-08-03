/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps.internal;

import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.runtime;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.cf.FlagConfigurationStepParameters;
import io.harness.steps.cf.PatchInstruction;
import io.harness.yaml.YamlSchemaTypes;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.beans.ConstructorProperties;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.CF)
@Data
@NoArgsConstructor
@EqualsAndHashCode
@JsonTypeName("FlagConfiguration")
@TypeAlias("flagConfigurationStepInfo")
@RecasterAlias("io.harness.plancreator.steps.internal.FlagConfigurationStepInfo")
public class FlagConfigurationStepInfo implements PMSStepInfo {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private String uuid;

  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) String name;
  @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> feature;
  @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> environment;
  @NotNull
  @ApiModelProperty(dataType = "[Lio.harness.steps.cf.PatchInstruction;")
  @YamlSchemaTypes(value = {runtime})
  ParameterField<List<PatchInstruction>> instructions;

  @Builder
  @ConstructorProperties({"name", "feature", "environment", "instructions"})
  public FlagConfigurationStepInfo(String name, ParameterField<String> feature, ParameterField<String> environment,
      ParameterField<List<PatchInstruction>> instructions) {
    this.name = name;
    this.feature = feature;
    this.environment = environment;
    this.instructions = instructions;
  }

  @Override
  public StepType getStepType() {
    return StepSpecTypeConstants.FLAG_CONFIGURATION_STEP_TYPE;
  }

  @Override
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.SYNC;
  }

  @Override
  public SpecParameters getSpecParameters() {
    return FlagConfigurationStepParameters.builder()
        .name(name)
        .feature(feature)
        .environment(environment)
        .instructions(instructions)
        .build();
  }
}
