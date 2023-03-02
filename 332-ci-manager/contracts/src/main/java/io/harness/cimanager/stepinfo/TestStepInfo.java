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
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.runtime;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.string;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.TypeInfo;
import io.harness.beans.steps.stepinfo.V1.CIAbstractStepInfo;
import io.harness.beans.yaml.extended.TILanguage;
import io.harness.beans.yaml.extended.beans.BuildTool;
import io.harness.beans.yaml.extended.beans.PullPolicy;
import io.harness.beans.yaml.extended.beans.Shell;
import io.harness.beans.yaml.extended.beans.Splitting;
import io.harness.beans.yaml.extended.reports.V1.Report;
import io.harness.beans.yaml.extended.volumes.V1.Volume;
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
import lombok.extern.slf4j.Slf4j;

@Value
@EqualsAndHashCode(callSuper = true)
@JsonTypeName("test")
@JsonIgnoreProperties(ignoreUnknown = true)
@OwnedBy(CI)
@RecasterAlias("io.harness.beans.steps.stepinfo.TestStepInfo")
@Slf4j
public class TestStepInfo extends CIAbstractStepInfo {
  @JsonIgnore private static final TypeInfo typeInfo = TypeInfo.builder().stepInfoType(CIStepInfoType.TEST).build();
  @JsonIgnore
  private static final StepType STEP_TYPE =
      StepType.newBuilder().setType(CIStepInfoType.TEST.getDisplayName()).setStepCategory(StepCategory.STEP).build();

  // Keeping the timeout to a day as it's a test step and might take lot' of time.
  @VariableExpression(skipVariableExpression = true) private static final int DAY = 60 * 60 * 24; // 24 hour;

  @VariableExpression(skipVariableExpression = true) @YamlSchemaTypes(value = {runtime}) Map<String, JsonNode> with;
  public Map<String, JsonNode> getWith() {
    if (this.with == null) {
      return Collections.emptyMap();
    }
    return this.with;
  }

  @YamlSchemaTypes(value = {runtime}) @ApiModelProperty(dataType = STRING_CLASSPATH) ParameterField<String> image;
  @YamlSchemaTypes(value = {runtime})
  @ApiModelProperty(dataType = "io.harness.beans.yaml.extended.beans.BuildTool")
  BuildTool uses;

  @YamlSchemaTypes(value = {string})
  @ApiModelProperty(dataType = STRING_MAP_CLASSPATH)
  ParameterField<Map<String, ParameterField<String>>> envs;
  public ParameterField<Map<String, ParameterField<String>>> getEnvs() {
    if (ParameterField.isNull(this.envs)) {
      this.envs.setValue(Collections.emptyMap());
    }
    return this.envs;
  }

  @YamlSchemaTypes(value = {runtime})
  @ApiModelProperty(dataType = "io.harness.beans.yaml.extended.reports.V1.Report", hidden = true)
  ParameterField<List<Report>> reports;
  public ParameterField<List<Report>> getReports() {
    if (ParameterField.isNull(this.reports)) {
      this.reports.setValue(Collections.emptyList());
    }
    return this.reports;
  }

  @YamlSchemaTypes(value = {runtime})
  @ApiModelProperty(dataType = STRING_LIST_CLASSPATH)
  @VariableExpression(skipVariableExpression = true)
  ParameterField<List<String>> outputs;
  public ParameterField<List<String>> getOutputs() {
    if (ParameterField.isNull(this.outputs)) {
      this.outputs.setValue(Collections.emptyList());
    }
    return this.outputs;
  }

  @YamlSchemaTypes({string}) @ApiModelProperty(dataType = INTEGER_CLASSPATH) ParameterField<Integer> user;
  @YamlSchemaTypes({runtime}) @ApiModelProperty(dataType = BOOLEAN_CLASSPATH) ParameterField<Boolean> privileged;
  @YamlSchemaTypes({runtime}) @ApiModelProperty(dataType = "io.harness.beans.yaml.extended.beans.Shell") Shell shell;
  @YamlSchemaTypes({runtime})
  @ApiModelProperty(dataType = "io.harness.beans.yaml.extended.beans.PullPolicy")
  PullPolicy pull;

  @ApiModelProperty(dataType = "io.harness.beans.yaml.extended.beans.Splitting") Splitting splitting;
  public Splitting getSplitting() {
    if (splitting == null) {
      return Splitting.builder().build();
    }
    return splitting;
  }

  @Builder
  @ConstructorProperties({"uuid", "image", "uses", "with", "resources", "envs", "outputs", "reports", "privileged",
      "user", "pull", "shell", "volumes", "splitting"})
  public TestStepInfo(String uuid, ParameterField<String> image, BuildTool uses, Map<String, JsonNode> with,
      ContainerResource resources, ParameterField<Map<String, ParameterField<String>>> envs,
      ParameterField<List<String>> outputs, ParameterField<List<Report>> reports, ParameterField<Boolean> privileged,
      ParameterField<Integer> user, PullPolicy pull, Shell shell, ParameterField<List<Volume>> volumes,
      Splitting splitting) {
    this.uuid = uuid;
    this.image = image;
    this.uses = uses;
    this.with = with;
    this.resources = resources;
    this.envs = envs;
    this.outputs = outputs;
    this.reports = reports;
    this.privileged = privileged;
    this.user = user;
    this.pull = pull;
    this.shell = shell;
    this.volumes = volumes;
    this.splitting = splitting;
  }

  @Override
  public long getDefaultTimeout() {
    return DAY;
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

  public ParameterField<TILanguage> getLanguage() {
    JsonNode language = this.getWith().get("language");
    if (language != null && language.isTextual()) {
      return ParameterField.createValueField(TILanguage.fromString(language.asText()));
    }
    return ParameterField.ofNull();
  }
}
