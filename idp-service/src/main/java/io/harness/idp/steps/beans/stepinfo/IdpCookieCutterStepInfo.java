/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.steps.beans.stepinfo;

import static io.harness.beans.SwaggerConstants.STRING_CLASSPATH;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.string;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.plugin.compatible.PluginCompatibleStep;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.TypeInfo;
import io.harness.idp.steps.Constants;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.yaml.YamlSchemaTypes;
import io.harness.yaml.core.VariableExpression;
import io.harness.yaml.extended.ci.container.ContainerResource;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@Data
@NoArgsConstructor
@JsonTypeName(Constants.IDP_COOKIECUTTER)
@TypeAlias("IdpCookieCutterStepInfo")
@OwnedBy(HarnessTeam.IDP)
@Builder
@AllArgsConstructor
@RecasterAlias("io.harness.idp.pipeline.steps.beans.stepinfo.IdpCookieCutterStepInfo")
public class IdpCookieCutterStepInfo implements PluginCompatibleStep {
  @VariableExpression(skipVariableExpression = true) public static final int DEFAULT_RETRY = 1;

  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private String uuid;

  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) private String identifier;
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) private String name;

  @ApiModelProperty(dataType = STRING_CLASSPATH) ParameterField<String> isPublicTemplate;
  @ApiModelProperty(dataType = STRING_CLASSPATH) ParameterField<String> publicTemplateUrl;
  @ApiModelProperty(dataType = STRING_CLASSPATH) ParameterField<String> pathForTemplate;
  @ApiModelProperty(dataType = STRING_CLASSPATH) ParameterField<String> outputDirectory;

  ContainerResource resources;

  @VariableExpression(skipVariableExpression = true)
  @YamlSchemaTypes(value = {string})
  protected ParameterField<Map<String, JsonNode>> cookieCutterVariables;

  @Override
  public TypeInfo getNonYamlInfo() {
    return TypeInfo.builder().stepInfoType(CIStepInfoType.IDP_COOKIECUTTER).build();
  }

  @Override
  public StepType getStepType() {
    return Constants.IDP_COOKIECUTTER_STEP_TYPE;
  }

  @Override
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.ASYNC;
  }

  @Override
  @ApiModelProperty(hidden = true)
  public ParameterField<Integer> getRunAsUser() {
    return null;
  }

  @Override
  @ApiModelProperty(hidden = true)
  public ParameterField<String> getConnectorRef() {
    return null;
  }

  @Override
  @ApiModelProperty(hidden = true)
  public ParameterField<List<String>> getBaseImageConnectorRefs() {
    return new ParameterField<>();
  }
}
