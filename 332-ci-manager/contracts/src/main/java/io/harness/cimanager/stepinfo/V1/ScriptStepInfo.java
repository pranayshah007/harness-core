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
import static io.harness.beans.SwaggerConstants.STRING_LIST_CLASSPATH;
import static io.harness.beans.SwaggerConstants.STRING_MAP_CLASSPATH;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.expression;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.runtime;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.string;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.TypeInfo;
import io.harness.beans.yaml.extended.beans.PullPolicy;
import io.harness.beans.yaml.extended.beans.Shell;
import io.harness.beans.yaml.extended.reports.V1.Report;
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
import io.swagger.annotations.ApiModelProperty;
import java.beans.ConstructorProperties;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = true)
@JsonTypeName("script")
@JsonIgnoreProperties(ignoreUnknown = true)
@OwnedBy(CI)
@RecasterAlias("io.harness.beans.steps.stepinfo.V1.ScriptStepInfo")
public class ScriptStepInfo extends CIAbstractStepInfo implements WithConnectorRef {
  @JsonIgnore private static final TypeInfo typeInfo = TypeInfo.builder().stepInfoType(CIStepInfoType.SCRIPT).build();
  @JsonIgnore
  private static final StepType STEP_TYPE =
      StepType.newBuilder().setType(CIStepInfoType.SCRIPT.getDisplayName()).setStepCategory(StepCategory.STEP).build();

  @NotNull @ApiModelProperty(dataType = STRING_CLASSPATH) ParameterField<String> run;
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

  @YamlSchemaTypes({runtime}) @ApiModelProperty(dataType = STRING_CLASSPATH) ParameterField<String> image;
  @YamlSchemaTypes({runtime}) @ApiModelProperty(dataType = BOOLEAN_CLASSPATH) ParameterField<Boolean> privileged;
  @YamlSchemaTypes({string}) @ApiModelProperty(dataType = INTEGER_CLASSPATH) ParameterField<Integer> user;
  @YamlSchemaTypes({expression})
  @ApiModelProperty(dataType = "io.harness.beans.yaml.extended.beans.Shell")
  ParameterField<Shell> shell;
  @YamlSchemaTypes({expression})
  @ApiModelProperty(dataType = "io.harness.beans.yaml.extended.beans.PullPolicy")
  ParameterField<PullPolicy> pull;

  @Builder
  @ConstructorProperties({"uuid", "run", "outputs", "envs", "reports", "image", "resources", "privileged", "user",
      "shell", "pull", "volumes"})
  public ScriptStepInfo(String uuid, ParameterField<String> run, ParameterField<List<String>> outputs,
      ParameterField<Map<String, ParameterField<String>>> envs, ParameterField<List<Report>> reports,
      ParameterField<String> image, ContainerResource resources, ParameterField<Boolean> privileged,
      ParameterField<Integer> user, ParameterField<Shell> shell, ParameterField<PullPolicy> pull,
      ParameterField<List<Volume>> volumes) {
    this.uuid = uuid;
    this.run = run;
    this.envs = envs;
    this.reports = reports;
    this.outputs = outputs;
    this.image = image;
    this.resources = resources;
    this.privileged = privileged;
    this.user = user;
    this.shell = shell;
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
