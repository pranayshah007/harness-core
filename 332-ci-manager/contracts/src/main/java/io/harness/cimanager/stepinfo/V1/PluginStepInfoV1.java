/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.steps.stepinfo.V1;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.beans.SwaggerConstants.BOOLEAN_CLASSPATH;
import static io.harness.beans.SwaggerConstants.INTEGER_CLASSPATH;
import static io.harness.beans.SwaggerConstants.STRING_CLASSPATH;
import static io.harness.beans.SwaggerConstants.STRING_MAP_CLASSPATH;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.expression;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.runtime;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.TypeInfo;
import io.harness.beans.yaml.extended.beans.PullPolicy;
import io.harness.beans.yaml.extended.volumes.V1.Volume;
import io.harness.filters.WithConnectorRef;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.YamlSchemaTypes;
import io.harness.yaml.core.VariableExpression;
import io.harness.yaml.extended.ci.container.ContainerResource;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.annotations.ApiModelProperty;
import java.beans.ConstructorProperties;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = true)
@JsonTypeName("plugin")
@JsonIgnoreProperties(ignoreUnknown = true)
@OwnedBy(CI)
@RecasterAlias("io.harness.beans.steps.stepinfo.V1.PluginStepInfoV1")
public class PluginStepInfoV1 extends CIAbstractStepInfo implements WithConnectorRef {
  @JsonIgnore
  private static final TypeInfo typeInfo = TypeInfo.builder().stepInfoType(CIStepInfoType.PLUGIN_V1).build();
  @JsonIgnore
  private static final StepType STEP_TYPE = StepType.newBuilder()
                                                .setType(CIStepInfoType.PLUGIN_V1.getDisplayName())
                                                .setStepCategory(StepCategory.STEP)
                                                .build();

  @VariableExpression(skipVariableExpression = true)
  @YamlSchemaTypes(value = {runtime})
  ParameterField<Map<String, JsonNode>> with;
  @YamlSchemaTypes(value = {runtime}) @ApiModelProperty(dataType = STRING_CLASSPATH) ParameterField<String> image;
  @YamlSchemaTypes(value = {runtime}) @ApiModelProperty(dataType = STRING_CLASSPATH) ParameterField<String> uses;

  @YamlSchemaTypes(value = {runtime})
  @ApiModelProperty(dataType = STRING_MAP_CLASSPATH)
  ParameterField<Map<String, ParameterField<String>>> envs;

  @YamlSchemaTypes({runtime}) @ApiModelProperty(dataType = BOOLEAN_CLASSPATH) ParameterField<Boolean> privileged;
  @YamlSchemaTypes({runtime}) @ApiModelProperty(dataType = INTEGER_CLASSPATH) ParameterField<Integer> user;
  @YamlSchemaTypes({expression})
  @ApiModelProperty(dataType = "io.harness.beans.yaml.extended.beans.PullPolicy")
  ParameterField<PullPolicy> pull;

  @Builder
  @ConstructorProperties(
      {"uuid", "with", "image", "uses", "resources", "envs", "privileged", "user", "pull", "volumes"})
  public PluginStepInfoV1(String uuid, ParameterField<Map<String, JsonNode>> with, ParameterField<String> image,
      ParameterField<String> uses, ContainerResource resources,
      ParameterField<Map<String, ParameterField<String>>> envs, ParameterField<Boolean> privileged,
      ParameterField<Integer> user, ParameterField<PullPolicy> pull, ParameterField<List<Volume>> volumes) {
    this.uuid = uuid;
    this.with = with;
    this.image = image;
    this.uses = uses;
    this.resources = resources;
    this.envs = envs;
    this.privileged = privileged;
    this.user = user;
    this.pull = pull;
    this.volumes = volumes;
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
    return Collections.emptyMap();
  }

  @Override
  public boolean skipUnresolvedExpressionsCheck() {
    return true;
  }
}
