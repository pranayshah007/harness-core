/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.stages;
import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.steps.IACMStepSpecTypeConstants;
import io.harness.beans.yaml.extended.platform.V1.Arch;
import io.harness.beans.yaml.extended.platform.V1.OS;
import io.harness.beans.yaml.extended.platform.V1.PlatformV1;
import io.harness.beans.yaml.extended.runtime.V1.CloudRuntimeV1;
import io.harness.beans.yaml.extended.runtime.V1.RuntimeV1;
import io.harness.plancreator.execution.ExecutionElementConfig;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.plancreator.stages.stage.StageInfoConfig;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.yaml.clone.Clone;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.TypeAlias;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.IACM)
@Data
@Builder
@AllArgsConstructor
@JsonTypeName(IACMStepSpecTypeConstants.IACM_STAGE_V1)
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@TypeAlias("IACMStageConfigImplV1")
@RecasterAlias("io.harness.beans.stages.IACMStageConfigImplV1")
public class IACMStageConfigImplV1 implements StageInfoConfig {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  String uuid;

  @NotNull @JsonProperty("steps") @Size(min = 1) List<JsonNode> steps;

  @JsonProperty("clone") Clone clone;
  public Clone getClone() {
    if (this.clone == null) {
      this.clone = Clone.builder().build();
    }
    return this.clone;
  }

  @JsonTypeInfo(
      use = JsonTypeInfo.Id.NAME, property = "type", include = JsonTypeInfo.As.EXISTING_PROPERTY, visible = true)
  @JsonProperty("runtime")
  RuntimeV1 runtime;
  public RuntimeV1 getRuntime() {
    if (this.runtime == null) {
      this.runtime = CloudRuntimeV1.builder().build();
    }
    return this.runtime;
  }

  // TODO: May not need this. Check what is necessary to run dlite
  @JsonProperty("platform") PlatformV1 platform;
  public PlatformV1 getPlatform() {
    if (this.platform == null) {
      this.platform = PlatformV1.builder()
                          .os(ParameterField.createValueField(OS.LINUX))
                          .arch(ParameterField.createValueField(Arch.AMD_64))
                          .build();
    }
    return this.platform;
  }

  @Override
  public ExecutionElementConfig getExecution() {
    List<ExecutionWrapperConfig> stepsList =
        steps.stream().map(step -> ExecutionWrapperConfig.builder().step(step).build()).collect(Collectors.toList());
    return ExecutionElementConfig.builder().steps(stepsList).build();
  }
}
