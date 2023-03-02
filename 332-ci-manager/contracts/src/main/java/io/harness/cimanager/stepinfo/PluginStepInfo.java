/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.steps.stepinfo;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.beans.SwaggerConstants.BOOLEAN_CLASSPATH;
import static io.harness.beans.SwaggerConstants.INTEGER_CLASSPATH;
import static io.harness.beans.SwaggerConstants.STRING_CLASSPATH;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.runtime;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.string;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.beans.steps.CIStepInfo;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.TypeInfo;
import io.harness.beans.yaml.extended.ImagePullPolicy;
import io.harness.beans.yaml.extended.reports.UnitTestReport;
import io.harness.filters.WithConnectorRef;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlNode;
import io.harness.yaml.YamlSchemaTypes;
import io.harness.yaml.core.VariableExpression;
import io.harness.yaml.extended.ci.container.ContainerResource;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.annotations.ApiModelProperty;
import java.beans.ConstructorProperties;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import org.springframework.data.annotation.TypeAlias;

@Data
@JsonTypeName("Plugin")
@JsonIgnoreProperties(ignoreUnknown = true)
@TypeAlias("pluginStepInfo")
@OwnedBy(CI)
@RecasterAlias("io.harness.beans.steps.stepinfo.PluginStepInfo")
public class PluginStepInfo implements CIStepInfo, WithConnectorRef {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private String uuid;

  @VariableExpression(skipVariableExpression = true) public static final int DEFAULT_RETRY = 1;

  @JsonIgnore public static final TypeInfo typeInfo = TypeInfo.builder().stepInfoType(CIStepInfoType.PLUGIN).build();
  @JsonIgnore
  public static final StepType STEP_TYPE =
      StepType.newBuilder().setType(CIStepInfoType.PLUGIN.getDisplayName()).setStepCategory(StepCategory.STEP).build();
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) private String identifier;
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) private String name;
  @VariableExpression(skipVariableExpression = true) @Min(MIN_RETRY) @Max(MAX_RETRY) private int retry;

  @VariableExpression(skipVariableExpression = true)
  @YamlSchemaTypes(value = {string})
  private ParameterField<Map<String, JsonNode>> settings;
  @ApiModelProperty(dataType = STRING_CLASSPATH) private ParameterField<String> image;
  @ApiModelProperty(dataType = STRING_CLASSPATH) private ParameterField<String> connectorRef;

  @ApiModelProperty(dataType = STRING_CLASSPATH) private ParameterField<String> uses;
  private ContainerResource resources;

  @YamlSchemaTypes(value = {string})
  @ApiModelProperty(dataType = SwaggerConstants.STRING_LIST_CLASSPATH)
  private ParameterField<List<String>> entrypoint;
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private ParameterField<Map<String, ParameterField<String>>> envVariables;
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private boolean harnessManagedImage;
  @YamlSchemaTypes(value = {runtime})
  @ApiModelProperty(dataType = "io.harness.beans.yaml.extended.reports.UnitTestReport")
  private ParameterField<UnitTestReport> reports;

  @YamlSchemaTypes({runtime})
  @ApiModelProperty(dataType = BOOLEAN_CLASSPATH)
  private ParameterField<Boolean> privileged;
  @YamlSchemaTypes({string}) @ApiModelProperty(dataType = INTEGER_CLASSPATH) private ParameterField<Integer> runAsUser;
  @YamlSchemaTypes({runtime})
  @ApiModelProperty(dataType = "io.harness.beans.yaml.extended.ImagePullPolicy")
  private ParameterField<ImagePullPolicy> imagePullPolicy;

  @Builder
  @ConstructorProperties({"identifier", "name", "retry", "settings", "image", "connectorRef", "uses", "resources",
      "reports", "entrypoint", "envVariables", "harnessInternalImage", "privileged", "runAsUser", "imagePullPolicy"})
  public PluginStepInfo(String identifier, String name, Integer retry, ParameterField<Map<String, JsonNode>> settings,
      ParameterField<String> image, ParameterField<String> connectorRef, ParameterField<String> uses,
      ContainerResource resources, ParameterField<UnitTestReport> reports, ParameterField<List<String>> entrypoint,
      ParameterField<Map<String, ParameterField<String>>> envVariables, boolean harnessManagedImage,
      ParameterField<Boolean> privileged, ParameterField<Integer> runAsUser,
      ParameterField<ImagePullPolicy> imagePullPolicy) {
    this.identifier = identifier;
    this.name = name;
    this.retry = Optional.ofNullable(retry).orElse(DEFAULT_RETRY);

    this.settings = settings;
    this.image = image;
    this.connectorRef = connectorRef;
    this.uses = uses;
    this.entrypoint = entrypoint;
    this.envVariables = envVariables;
    this.resources = resources;
    this.privileged = privileged;
    this.runAsUser = runAsUser;
    this.harnessManagedImage = harnessManagedImage;
    this.imagePullPolicy = imagePullPolicy;
    this.reports = reports;
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
    Map<String, ParameterField<String>> connectorRefMap = new HashMap<>();
    connectorRefMap.put(YAMLFieldNameConstants.CONNECTOR_REF, connectorRef);
    return connectorRefMap;
  }
}
