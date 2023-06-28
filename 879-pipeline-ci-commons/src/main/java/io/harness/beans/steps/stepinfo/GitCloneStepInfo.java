/*
 * Copyright 2022 Harness Inc. All rights reserved.
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
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.runtime;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.string;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
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
import io.harness.yaml.YamlSchemaTypes;
import io.harness.yaml.core.VariableExpression;
import io.harness.yaml.extended.ci.codebase.Build;
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
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import org.springframework.data.annotation.TypeAlias;

@Data
@JsonTypeName("GitClone")
@JsonIgnoreProperties(ignoreUnknown = true)
@TypeAlias("gitCloneStepInfo")
@OwnedBy(CI)
@RecasterAlias("io.harness.beans.steps.stepinfo.GitCloneStepInfo")
public class GitCloneStepInfo implements PluginCompatibleStep, WithConnectorRef {
  @VariableExpression(skipVariableExpression = true) public static final int DEFAULT_RETRY = 1;

  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private String uuid;

  @JsonIgnore public static final TypeInfo typeInfo = TypeInfo.builder().stepInfoType(CIStepInfoType.GIT_CLONE).build();

  @JsonIgnore
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(CIStepInfoType.GIT_CLONE.getDisplayName())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) private String identifier;

  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) private String name;

  @VariableExpression(skipVariableExpression = true) @Min(MIN_RETRY) @Max(MAX_RETRY) private int retry;

  @NotNull @ApiModelProperty(dataType = STRING_CLASSPATH) ParameterField<String> connectorRef;

  ContainerResource resources;

  @YamlSchemaTypes({string}) @ApiModelProperty(dataType = INTEGER_CLASSPATH) private ParameterField<Integer> runAsUser;

  @ApiModelProperty(dataType = STRING_CLASSPATH) ParameterField<String> repoName;

  @ApiModelProperty(dataType = STRING_CLASSPATH) ParameterField<String> projectName;

  @YamlSchemaTypes(value = {runtime})
  @ApiModelProperty(dataType = "io.harness.yaml.extended.ci.codebase.Build")
  @NotNull
  @VariableExpression(skipVariableExpression = true)
  ParameterField<Build> build;

  @YamlSchemaTypes({runtime}) @ApiModelProperty(dataType = INTEGER_CLASSPATH) ParameterField<Integer> depth;

  @YamlSchemaTypes({runtime}) @ApiModelProperty(dataType = BOOLEAN_CLASSPATH) ParameterField<Boolean> sslVerify;

  @ApiModelProperty(dataType = STRING_CLASSPATH) ParameterField<String> cloneDirectory;

  @ApiModelProperty(dataType = STRING_LIST_CLASSPATH) ParameterField<List<String>> outputFilePathsContent;

  @Builder
  @ConstructorProperties({"identifier", "name", "retry", "connectorRef", "resources", "runAsUser", "repoName", "build",
      "projectName", "depth", "sslVerify", "cloneDirectory", "outputFilePathsContent"})
  public GitCloneStepInfo(String identifier, String name, int retry, ParameterField<String> connectorRef,
      ContainerResource resources, ParameterField<Integer> runAsUser, ParameterField<String> repoName,
      ParameterField<Build> build, ParameterField<String> projectName, ParameterField<Integer> depth,
      ParameterField<Boolean> sslVerify, ParameterField<String> cloneDirectory,
      ParameterField<List<String>> outputFilePathsContent) {
    this.identifier = identifier;
    this.name = name;
    this.retry = retry;
    this.connectorRef = connectorRef;
    this.resources = resources;
    this.runAsUser = runAsUser;

    this.repoName = repoName;
    this.build = build;
    this.projectName = projectName;
    this.depth = depth;
    this.sslVerify = sslVerify;
    this.cloneDirectory = cloneDirectory;
    this.outputFilePathsContent = outputFilePathsContent;
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