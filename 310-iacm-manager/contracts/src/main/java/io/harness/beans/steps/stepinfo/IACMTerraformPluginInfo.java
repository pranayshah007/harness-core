/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.steps.stepinfo;

import static io.harness.annotations.dev.HarnessTeam.IACM;
import static io.harness.beans.SwaggerConstants.STRING_CLASSPATH;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.stepinfo.IACMStepInfo;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.TypeInfo;
import io.harness.pms.yaml.ParameterField;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.beans.ConstructorProperties;
import java.util.Map;
import java.util.Optional;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.springframework.data.annotation.TypeAlias;

@Data
@JsonTypeName("IACMTerraformPlugin")
@JsonIgnoreProperties(ignoreUnknown = true)
@TypeAlias("iacmTerraformPluginInfo")
@OwnedBy(IACM)
@RecasterAlias("io.harness.beans.steps.stepinfo.IACMTerraformPluginInfo")
@EqualsAndHashCode(callSuper = true)
public class IACMTerraformPluginInfo extends IACMStepInfo {
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) private String workspace;

  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private ParameterField<Map<String, String>> envVariables;

  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private ParameterField<Map<String, String>> envVariablesFromConnector;

  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private ParameterField<Map<String, String>> secretVariables;

  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private ParameterField<Map<String, String>> secretVariablesFromConnector;

  @NotNull @ApiModelProperty(dataType = STRING_CLASSPATH) private ParameterField<String> command;
  @ApiModelProperty(dataType = STRING_CLASSPATH) private ParameterField<String> image;

  @Override
  public TypeInfo getNonYamlInfo() {
    return TypeInfo.builder().stepInfoType(CIStepInfoType.IACM_TERRAFORM_PLUGIN).build();
  }

  @Builder
  @ConstructorProperties({"identifier", "name", "retry", "settings", "resources", "outputVariables", "runAsUser",
      "privileged", "imagePullPolicy", "env", "operation", "image"})
  public IACMTerraformPluginInfo(String identifier, String name, Integer retry,
      ParameterField<Map<String, String>> envVariables, ParameterField<String> operation,
      ParameterField<String> image) {
    super.identifier = identifier;
    super.name = name;
    super.retry = Optional.ofNullable(retry).orElse(DEFAULT_RETRY);
    this.envVariables = envVariables;
    this.command = operation;
    this.image = image;
  }
}
