/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.steps.stepinfo;

import static io.harness.beans.SwaggerConstants.STRING_CLASSPATH;

import io.harness.annotation.RecasterAlias;
import io.harness.beans.plugin.compatible.PluginCompatibleStep;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.TypeInfo;
import io.harness.filters.WithConnectorRef;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlNode;
import io.harness.yaml.extended.ci.container.ContainerResource;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TypeAlias("SscaOrchestrationStepInfo")
@JsonTypeName("SscaOrchestration")
@RecasterAlias("io.harness.beans.steps.stepinfo.SscaOrchestrationStepInfo")
public class SscaOrchestrationStepInfo implements PluginCompatibleStep, WithConnectorRef {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private String uuid;

  @JsonIgnore
  public static final TypeInfo typeInfo = TypeInfo.builder().stepInfoType(CIStepInfoType.SSCA_ORCHESTRATION).build();

  @JsonIgnore
  public static StepType STEP_TYPE = StepType.newBuilder()
                                         .setType(CIStepInfoType.SSCA_ORCHESTRATION.getDisplayName())
                                         .setStepCategory(StepCategory.STEP)
                                         .build();

  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) private String identifier;
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) private String name;

  @ApiModelProperty(dataType = STRING_CLASSPATH) private ParameterField<String> connectorRef;

  private GenerationType generationType;

  @ApiModelProperty(dataType = STRING_CLASSPATH) private ParameterField<String> sbomGenerationTool;

  @ApiModelProperty(dataType = STRING_CLASSPATH) private ParameterField<String> sbomFormat;

  @ApiModelProperty(dataType = STRING_CLASSPATH) private ParameterField<String> sbomSource;

  @JsonIgnore private ContainerResource resources;

  @Override
  public TypeInfo getNonYamlInfo() {
    return typeInfo;
  }

  @Override
  public ParameterField<Integer> getRunAsUser() {
    return null;
  }

  @Override
  public StepType getStepType() {
    return STEP_TYPE;
  }

  public void setStepType(String type) {
    STEP_TYPE = STEP_TYPE.toBuilder().setType(type).build();
  }

  @Override
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.ASYNC;
  }

  @Override
  public Map<String, ParameterField<String>> extractConnectorRefs() {
    Map<String, ParameterField<String>> connectorRefMap = new HashMap<>();
    connectorRefMap.put(YAMLFieldNameConstants.CONNECTOR_REF, connectorRef);
    return connectorRefMap;
  }

  enum GenerationType {
    ORCHESTRATED("Orchestrated");

    @Getter private String name;

    GenerationType(String name) {
      this.name = name;
    }
  }
}
