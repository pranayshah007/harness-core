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
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.string;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.plugin.compatible.PluginCompatibleStep;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.TypeInfo;
import io.harness.data.validator.EntityIdentifier;
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
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import org.springframework.data.annotation.TypeAlias;

@Data
@JsonTypeName("BuildAndPushGCR")
@JsonIgnoreProperties(ignoreUnknown = true)
@TypeAlias("gcrStepInfo")
@OwnedBy(CI)
@RecasterAlias("io.harness.beans.steps.stepinfo.GCRStepInfo")
public class GCRStepInfo implements PluginCompatibleStep, WithConnectorRef {
  @VariableExpression(skipVariableExpression = true) public static final int DEFAULT_RETRY = 1;
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private String uuid;
  @JsonIgnore public static final TypeInfo typeInfo = TypeInfo.builder().stepInfoType(CIStepInfoType.GCR).build();
  @JsonIgnore
  public static final StepType STEP_TYPE =
      StepType.newBuilder().setType(CIStepInfoType.GCR.getDisplayName()).setStepCategory(StepCategory.STEP).build();

  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  @NotNull
  @EntityIdentifier
  private String identifier;
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) private String name;
  @VariableExpression(skipVariableExpression = true) @Min(MIN_RETRY) @Max(MAX_RETRY) private int retry;

  @NotNull @ApiModelProperty(dataType = STRING_CLASSPATH) private ParameterField<String> connectorRef;
  private ContainerResource resources;

  // plugin settings
  @NotNull @ApiModelProperty(dataType = STRING_CLASSPATH) private ParameterField<String> host;
  @NotNull @ApiModelProperty(dataType = STRING_CLASSPATH) private ParameterField<String> projectID;
  @NotNull @ApiModelProperty(dataType = STRING_CLASSPATH) private ParameterField<String> imageName;

  @NotNull
  @YamlSchemaTypes(value = {string})
  @ApiModelProperty(dataType = STRING_LIST_CLASSPATH)
  private ParameterField<List<String>> tags;
  @ApiModelProperty(dataType = STRING_CLASSPATH) private ParameterField<String> context;
  @ApiModelProperty(dataType = STRING_CLASSPATH) private ParameterField<String> dockerfile;
  @ApiModelProperty(dataType = STRING_CLASSPATH) private ParameterField<String> target;
  @YamlSchemaTypes(value = {string})
  @ApiModelProperty(dataType = STRING_MAP_CLASSPATH)
  private ParameterField<Map<String, String>> labels;
  @YamlSchemaTypes(value = {string})
  @ApiModelProperty(dataType = STRING_MAP_CLASSPATH)
  private ParameterField<Map<String, String>> buildArgs;
  @YamlSchemaTypes({string}) @ApiModelProperty(dataType = INTEGER_CLASSPATH) private ParameterField<Integer> runAsUser;
  @YamlSchemaTypes({string}) @ApiModelProperty(dataType = BOOLEAN_CLASSPATH) private ParameterField<Boolean> optimize;
  @ApiModelProperty(dataType = STRING_CLASSPATH) private ParameterField<String> remoteCacheImage;
  @YamlSchemaTypes(value = {string})
  @ApiModelProperty(dataType = STRING_LIST_CLASSPATH)
  private ParameterField<List<String>> cacheFrom;
  @ApiModelProperty(dataType = STRING_CLASSPATH) private ParameterField<String> cacheTo;
  @YamlSchemaTypes({string}) @ApiModelProperty(dataType = BOOLEAN_CLASSPATH) private ParameterField<Boolean> caching;

  @Builder
  @ConstructorProperties({"identifier", "name", "retry", "connectorRef", "resources", "host", "projectID", "imageName",
      "tags", "context", "dockerfile", "target", "labels", "buildArgs", "runAsUser", "optimize", "remoteCacheImage",
      "cacheFrom", "cacheTo", "caching"})
  public GCRStepInfo(String identifier, String name, Integer retry, ParameterField<String> connectorRef,
      ContainerResource resources, ParameterField<String> host, ParameterField<String> projectID,
      ParameterField<String> imageName, ParameterField<List<String>> tags, ParameterField<String> context,
      ParameterField<String> dockerfile, ParameterField<String> target, ParameterField<Map<String, String>> labels,
      ParameterField<Map<String, String>> buildArgs, ParameterField<Integer> runAsUser,
      ParameterField<Boolean> optimize, ParameterField<String> remoteCacheImage, ParameterField<List<String>> cacheFrom,
      ParameterField<String> cacheTo, ParameterField<Boolean> caching) {
    this.identifier = identifier;
    this.name = name;
    this.retry = Optional.ofNullable(retry).orElse(DEFAULT_RETRY);
    this.connectorRef = connectorRef;
    this.resources = resources;
    this.host = host;
    this.projectID = projectID;
    this.imageName = imageName;
    this.tags = tags;
    this.context = context;
    this.dockerfile = dockerfile;
    this.target = target;
    this.labels = labels;
    this.buildArgs = buildArgs;
    this.runAsUser = runAsUser;
    this.optimize = optimize;
    this.remoteCacheImage = remoteCacheImage;
    this.cacheFrom = cacheFrom;
    this.cacheTo = cacheTo;
    this.caching = caching;
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
