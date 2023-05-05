/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.steps.stepinfo;

import static io.harness.annotations.dev.HarnessTeam.STO;
import static io.harness.beans.SwaggerConstants.BOOLEAN_CLASSPATH;
import static io.harness.beans.SwaggerConstants.INTEGER_CLASSPATH;
import static io.harness.beans.SwaggerConstants.STRING_CLASSPATH;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.expression;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.runtime;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.string;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.plugin.compatible.PluginCompatibleStep;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.TypeInfo;
import io.harness.beans.yaml.extended.ImagePullPolicy;
import io.harness.data.structure.CollectionUtils;
import io.harness.data.validator.EntityIdentifier;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.yaml.YamlSchemaTypes;
import io.harness.yaml.core.VariableExpression;
import io.harness.yaml.core.variables.OutputNGVariable;
import io.harness.yaml.extended.ci.container.ContainerResource;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.annotations.ApiModelProperty;
import java.beans.ConstructorProperties;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.TypeAlias;

@Data
@SuperBuilder
@NoArgsConstructor
@JsonTypeName("Security")
@JsonIgnoreProperties(ignoreUnknown = true)
@TypeAlias("securityStepInfo")
@OwnedBy(STO)
@RecasterAlias("io.harness.beans.steps.stepinfo.SecurityStepInfo")
public class SecurityStepInfo implements PluginCompatibleStep {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private String uuid;

  @VariableExpression(skipVariableExpression = true) public static final int DEFAULT_RETRY = 1;

  @JsonIgnore
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(CIStepInfoType.SECURITY.getDisplayName())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  @NotNull
  @EntityIdentifier
  protected String identifier;
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) protected String name;
  @VariableExpression(skipVariableExpression = true) @Min(MIN_RETRY) @Max(MAX_RETRY) protected int retry;

  @VariableExpression(skipVariableExpression = true)
  @YamlSchemaTypes(value = {string})
  protected ParameterField<Map<String, JsonNode>> settings;

  @VariableExpression(skipVariableExpression = true)
  @ApiModelProperty(dataType = STRING_CLASSPATH, hidden = true)
  protected ParameterField<String> connectorRef;
  protected ContainerResource resources;

  @YamlSchemaTypes(value = {runtime})
  @VariableExpression(skipVariableExpression = true)
  @ApiModelProperty(dataType = "[Lio.harness.yaml.core.variables.OutputNGVariable;")
  protected ParameterField<List<OutputNGVariable>> outputVariables;

  @YamlSchemaTypes({runtime})
  @ApiModelProperty(dataType = BOOLEAN_CLASSPATH)
  protected ParameterField<Boolean> privileged;
  @YamlSchemaTypes({string})
  @ApiModelProperty(dataType = INTEGER_CLASSPATH)
  protected ParameterField<Integer> runAsUser;
  @YamlSchemaTypes({expression})
  @ApiModelProperty(dataType = "io.harness.beans.yaml.extended.ImagePullPolicy")
  protected ParameterField<ImagePullPolicy> imagePullPolicy;

  @VariableExpression(skipVariableExpression = true) protected static List<OutputNGVariable> defaultOutputVariables;

  static {
    defaultOutputVariables = Arrays.asList(OutputNGVariable.builder().name("JOB_ID").build(),
        OutputNGVariable.builder().name("JOB_STATUS").build(), OutputNGVariable.builder().name("CRITICAL").build(),
        OutputNGVariable.builder().name("HIGH").build(), OutputNGVariable.builder().name("MEDIUM").build(),
        OutputNGVariable.builder().name("LOW").build(), OutputNGVariable.builder().name("INFO").build(),
        OutputNGVariable.builder().name("UNASSIGNED").build(), OutputNGVariable.builder().name("TOTAL").build(),
        OutputNGVariable.builder().name("NEW_CRITICAL").build(), OutputNGVariable.builder().name("NEW_HIGH").build(),
        OutputNGVariable.builder().name("NEW_MEDIUM").build(), OutputNGVariable.builder().name("NEW_LOW").build(),
        OutputNGVariable.builder().name("NEW_INFO").build(), OutputNGVariable.builder().name("NEW_UNASSIGNED").build(),
        OutputNGVariable.builder().name("NEW_TOTAL").build());
  }

  @Builder
  @ConstructorProperties({"identifier", "name", "retry", "settings", "resources", "outputVariables", "runAsUser",
      "privileged", "imagePullPolicy"})
  public SecurityStepInfo(String identifier, String name, Integer retry, ParameterField<Map<String, JsonNode>> settings,
      ContainerResource resources, ParameterField<List<OutputNGVariable>> outputVariables,
      ParameterField<Integer> runAsUser, ParameterField<Boolean> privileged,
      ParameterField<ImagePullPolicy> imagePullPolicy) {
    this.identifier = identifier;
    this.name = name;
    this.retry = Optional.ofNullable(retry).orElse(DEFAULT_RETRY);
    this.settings = settings;
    this.resources = resources;
    this.outputVariables = outputVariables;

    this.runAsUser = runAsUser;
    this.privileged = privileged;
    this.imagePullPolicy = imagePullPolicy;
  }
  public ParameterField<Map<String, JsonNode>> getSettings() {
    return this.settings;
  }

  @Override
  public TypeInfo getNonYamlInfo() {
    return TypeInfo.builder().stepInfoType(CIStepInfoType.SECURITY).build();
  }

  protected String getTypeName() {
    return this.getClass().getAnnotation(JsonTypeName.class).value();
  }

  @Override
  public StepType getStepType() {
    return StepType.newBuilder().setType(getTypeName()).setStepCategory(StepCategory.STEP).build();
  }

  @Override
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.ASYNC;
  }

  public ParameterField<List<OutputNGVariable>> getOutputVariables() {
    return ParameterField.createValueField(
        Stream
            .concat(defaultOutputVariables.stream(),
                (CollectionUtils.emptyIfNull((List<OutputNGVariable>) outputVariables.fetchFinalValue())).stream())
            .collect(Collectors.toSet())
            .stream()
            .collect(Collectors.toList()));
  }
}
