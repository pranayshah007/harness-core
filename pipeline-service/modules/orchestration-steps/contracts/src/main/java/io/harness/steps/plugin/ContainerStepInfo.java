/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.plugin;

import static io.harness.beans.SwaggerConstants.BOOLEAN_CLASSPATH;
import static io.harness.beans.SwaggerConstants.INTEGER_CLASSPATH;
import static io.harness.beans.SwaggerConstants.STRING_CLASSPATH;
import static io.harness.beans.SwaggerConstants.STRING_MAP_CLASSPATH;
import static io.harness.steps.plugin.ContainerStepConstants.MAX_RETRY;
import static io.harness.steps.plugin.ContainerStepConstants.MIN_RETRY;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.runtime;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.string;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.yaml.extended.CIShellType;
import io.harness.beans.yaml.extended.ImagePullPolicy;
import io.harness.filters.WithConnectorRef;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.plancreator.steps.common.WithDelegateSelector;
import io.harness.plancreator.steps.internal.PMSStepInfo;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlNode;
import io.harness.steps.container.ContainerStepSpecTypeConstants;
import io.harness.steps.plugin.infrastructure.ContainerStepInfra;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;
import io.harness.yaml.YamlSchemaTypes;
import io.harness.yaml.core.VariableExpression;
import io.harness.yaml.core.variables.OutputNGVariable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.annotations.ApiModelProperty;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonTypeName(ContainerStepSpecTypeConstants.CONTAINER_STEP)
@SimpleVisitorHelper(helperClass = ContainerStepInfoVisitorHelper.class)
@TypeAlias("containerStepInfo")
@OwnedBy(HarnessTeam.PIPELINE)
@RecasterAlias("io.harness.steps.plugin.ContainerStepInfo")
public class ContainerStepInfo extends ContainerBaseStepInfo
    implements PMSStepInfo, Visitable, WithDelegateSelector, WithConnectorRef, SpecParameters, ContainerStepSpec {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private String uuid;

  @VariableExpression(skipVariableExpression = true) public static final int DEFAULT_RETRY = 1;

  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) private String identifier;
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) private String name;
  @ApiModelProperty(hidden = true)
  @VariableExpression(skipVariableExpression = true)
  @Min(MIN_RETRY)
  @Max(MAX_RETRY)
  private int retry;

  @VariableExpression(skipVariableExpression = true)
  @YamlSchemaTypes(value = {string})
  private ParameterField<Map<String, JsonNode>> settings;

  @ApiModelProperty(dataType = STRING_CLASSPATH, hidden = true) private ParameterField<String> uses;

  @NotNull @Valid private ContainerStepInfra infrastructure;

  @YamlSchemaTypes(value = {string})
  @ApiModelProperty(dataType = STRING_MAP_CLASSPATH)
  private ParameterField<Map<String, String>> envVariables;

  @YamlSchemaTypes({runtime})
  @ApiModelProperty(dataType = BOOLEAN_CLASSPATH)
  private ParameterField<Boolean> privileged;

  @YamlSchemaTypes({string})
  @ApiModelProperty(dataType = INTEGER_CLASSPATH, hidden = true)
  private ParameterField<Integer> runAsUser;
  @YamlSchemaTypes({runtime})
  @ApiModelProperty(dataType = "io.harness.beans.yaml.extended.CIShellType")
  private ParameterField<CIShellType> shell;

  @NotNull @ApiModelProperty(dataType = STRING_CLASSPATH) private ParameterField<String> command;

  @YamlSchemaTypes(value = {runtime})
  @ApiModelProperty(dataType = "[Lio.harness.yaml.core.variables.OutputNGVariable;")
  @VariableExpression(skipVariableExpression = true)
  private ParameterField<List<OutputNGVariable>> outputVariables;

  @Builder(builderMethodName = "infoBuilder")
  public ContainerStepInfo(String uuid, String identifier, String name, int retry,
      ParameterField<Map<String, JsonNode>> settings, ParameterField<String> image, ParameterField<String> connectorRef,
      ParameterField<String> uses, ParameterField<List<String>> entrypoint,
      ParameterField<Map<String, String>> envVariables, ParameterField<Boolean> privileged,
      ParameterField<Integer> runAsUser, ParameterField<ImagePullPolicy> imagePullPolicy,
      ParameterField<CIShellType> shellType, ParameterField<String> command,
      ParameterField<List<OutputNGVariable>> outputVariables) {
    this.uuid = uuid;
    this.identifier = identifier;
    this.name = name;
    this.retry = retry;
    this.settings = settings;
    this.image = image;
    this.connectorRef = connectorRef;
    this.uses = uses;
    this.entrypoint = entrypoint;
    this.envVariables = envVariables;
    this.privileged = privileged;
    this.runAsUser = runAsUser;
    this.imagePullPolicy = imagePullPolicy;
    this.shell = shellType;
    this.command = command;
    this.outputVariables = outputVariables;
  }

  @Override
  @JsonIgnore
  public StepType getStepType() {
    return ContainerStepSpecTypeConstants.CONTAINER_STEP_TYPE;
  }

  @Override
  @JsonIgnore
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.TASK_CHAIN;
  }

  @Override
  public SpecParameters getSpecParameters() {
    return this;
  }

  @Override
  public ParameterField<List<TaskSelectorYaml>> fetchDelegateSelectors() {
    return getDelegateSelectors();
  }
  @Override
  public Map<String, ParameterField<String>> extractConnectorRefs() {
    Map<String, ParameterField<String>> connectorRefMap = new HashMap<>();
    connectorRefMap.put(YAMLFieldNameConstants.CONNECTOR_REF, connectorRef);
    return connectorRefMap;
  }

  @Override
  @JsonIgnore
  public ContainerStepType getType() {
    return ContainerStepType.RUN_CONTAINER;
  }
}
