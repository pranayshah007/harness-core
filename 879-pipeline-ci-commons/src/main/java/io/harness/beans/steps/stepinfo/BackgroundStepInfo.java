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
import static io.harness.beans.SwaggerConstants.STRING_LIST_CLASSPATH;
import static io.harness.beans.SwaggerConstants.STRING_MAP_CLASSPATH;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.expression;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.runtime;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.string;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.CIStepInfo;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.TypeInfo;
import io.harness.beans.yaml.extended.CIShellType;
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
@JsonTypeName("Background")
@JsonIgnoreProperties(ignoreUnknown = true)
@TypeAlias("backgroundStepInfo")
@OwnedBy(CI)
@RecasterAlias("io.harness.beans.steps.stepinfo.BackgroundStepInfo")
public class BackgroundStepInfo implements CIStepInfo, WithConnectorRef {
  @VariableExpression(skipVariableExpression = true) public static final int DEFAULT_RETRY = 1;
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private String uuid;

  @JsonIgnore
  public static final TypeInfo typeInfo = TypeInfo.builder().stepInfoType(CIStepInfoType.BACKGROUND).build();
  @JsonIgnore
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(CIStepInfoType.BACKGROUND.getDisplayName())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) private String identifier;
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) private String name;
  @VariableExpression(skipVariableExpression = true) @Min(MIN_RETRY) @Max(MAX_RETRY) private int retry;

  @ApiModelProperty(dataType = STRING_CLASSPATH) private ParameterField<String> command;
  @YamlSchemaTypes(value = {string})
  @ApiModelProperty(dataType = STRING_MAP_CLASSPATH)
  private ParameterField<Map<String, ParameterField<String>>> envVariables;
  @YamlSchemaTypes(value = {runtime})
  @ApiModelProperty(dataType = "io.harness.beans.yaml.extended.reports.UnitTestReport")
  private ParameterField<UnitTestReport> reports;

  @ApiModelProperty(dataType = STRING_CLASSPATH) private ParameterField<String> image;
  @ApiModelProperty(dataType = STRING_CLASSPATH) private ParameterField<String> connectorRef;
  private ContainerResource resources;
  @YamlSchemaTypes({runtime})
  @ApiModelProperty(dataType = BOOLEAN_CLASSPATH)
  private ParameterField<Boolean> privileged;
  @YamlSchemaTypes({string}) @ApiModelProperty(dataType = INTEGER_CLASSPATH) private ParameterField<Integer> runAsUser;
  @YamlSchemaTypes({expression})
  @ApiModelProperty(dataType = "io.harness.beans.yaml.extended.CIShellType")
  private ParameterField<CIShellType> shell;
  @YamlSchemaTypes({expression})
  @ApiModelProperty(dataType = "io.harness.beans.yaml.extended.ImagePullPolicy")
  private ParameterField<ImagePullPolicy> imagePullPolicy;
  @YamlSchemaTypes(value = {string})
  @ApiModelProperty(dataType = STRING_LIST_CLASSPATH)
  private ParameterField<List<String>> entrypoint;
  @YamlSchemaTypes(value = {string})
  @ApiModelProperty(dataType = STRING_MAP_CLASSPATH)
  private ParameterField<Map<String, String>> portBindings;

  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private ParameterField<List<String>> ports;

  @Builder
  @ConstructorProperties({"identifier", "name", "retry", "command", "reports", "envVariables", "image", "connectorRef",
      "resources", "privileged", "runAsUser", "shell", "imagePullPolicy", "entrypoint", "portBindings", "ports"})
  public BackgroundStepInfo(String identifier, String name, Integer retry, ParameterField<String> command,
      ParameterField<UnitTestReport> reports, ParameterField<Map<String, ParameterField<String>>> envVariables,
      ParameterField<String> image, ParameterField<String> connectorRef, ContainerResource resources,
      ParameterField<Boolean> privileged, ParameterField<Integer> runAsUser, ParameterField<CIShellType> shell,
      ParameterField<ImagePullPolicy> imagePullPolicy, ParameterField<List<String>> entrypoint,
      ParameterField<Map<String, String>> portBindings, ParameterField<List<String>> ports) {
    this.identifier = identifier;
    this.name = name;
    this.retry = Optional.ofNullable(retry).orElse(DEFAULT_RETRY);
    this.command = command;
    this.envVariables = envVariables;
    this.reports = reports;
    this.image = image;
    this.connectorRef = connectorRef;
    this.resources = resources;
    this.privileged = privileged;
    this.runAsUser = runAsUser;
    this.shell = shell;
    this.imagePullPolicy = imagePullPolicy;
    this.entrypoint = entrypoint;
    this.portBindings = portBindings;
    this.ports = ports;
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
    if (connectorRef.getValue() != null) {
      connectorRefMap.put(YAMLFieldNameConstants.CONNECTOR_REF, connectorRef);
    }
    return connectorRefMap;
  }

  @Override
  public boolean skipUnresolvedExpressionsCheck() {
    return true;
  }
}
