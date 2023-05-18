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
import io.harness.beans.yaml.extended.volumes.V1.Volume;
import io.harness.filters.WithConnectorRef;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.YamlSchemaTypes;
import io.harness.yaml.extended.ci.container.ContainerResource;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.beans.ConstructorProperties;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = true)
@JsonTypeName("background")
@JsonIgnoreProperties(ignoreUnknown = true)
@OwnedBy(CI)
@RecasterAlias("io.harness.beans.steps.stepinfo.V1.BackgroundStepInfoV1")
public class BackgroundStepInfoV1 extends CIAbstractStepInfo implements WithConnectorRef {
  @JsonIgnore
  private static final TypeInfo typeInfo = TypeInfo.builder().stepInfoType(CIStepInfoType.BACKGROUND_V1).build();
  @JsonIgnore
  private static final StepType STEP_TYPE = StepType.newBuilder()
                                                .setType(CIStepInfoType.BACKGROUND_V1.getDisplayName())
                                                .setStepCategory(StepCategory.STEP)
                                                .build();

  @NotNull @ApiModelProperty(dataType = STRING_CLASSPATH) ParameterField<String> run;
  @YamlSchemaTypes(value = {string})
  @ApiModelProperty(dataType = STRING_MAP_CLASSPATH)
  ParameterField<Map<String, ParameterField<String>>> envs;
  public ParameterField<Map<String, ParameterField<String>>> getEnvs() {
    if (ParameterField.isNull(this.envs)) {
      this.envs.setValue(Collections.emptyMap());
    }
    return this.envs;
  }

  @YamlSchemaTypes(value = {string}) @ApiModelProperty(dataType = STRING_CLASSPATH) ParameterField<String> entrypoint;
  @YamlSchemaTypes(value = {string})
  @ApiModelProperty(dataType = STRING_LIST_CLASSPATH)
  ParameterField<List<String>> args;
  @YamlSchemaTypes(value = {string})
  @ApiModelProperty(dataType = STRING_LIST_CLASSPATH)
  ParameterField<List<String>> ports;
  @YamlSchemaTypes(value = {string}) @ApiModelProperty(dataType = STRING_CLASSPATH) ParameterField<String> network;

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
  @ConstructorProperties({"uuid", "run", "envs", "entrypoint", "args", "ports", "network", "image", "resources",
      "privileged", "user", "shell", "pull", "volumes"})
  public BackgroundStepInfoV1(String uuid, ParameterField<String> run,
      ParameterField<Map<String, ParameterField<String>>> envs, ParameterField<String> entrypoint,
      ParameterField<List<String>> args, ParameterField<List<String>> ports, ParameterField<String> network,
      ParameterField<String> image, ContainerResource resources, ParameterField<Boolean> privileged,
      ParameterField<Integer> user, ParameterField<Shell> shell, ParameterField<PullPolicy> pull,
      ParameterField<List<Volume>> volumes) {
    this.uuid = uuid;
    this.run = run;
    this.envs = envs;
    this.entrypoint = entrypoint;
    this.args = args;
    this.ports = ports;
    this.network = network;
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

  public ParameterField<List<String>> getEntrypointList() {
    List<String> argsList = new ArrayList<>();
    if (ParameterField.isNotNull(args)) {
      argsList = args.getValue();
    }
    if (ParameterField.isNotNull(entrypoint)) {
      argsList.add(0, (String) entrypoint.fetchFinalValue());
    }
    return ParameterField.createValueField(argsList);
  }
}
