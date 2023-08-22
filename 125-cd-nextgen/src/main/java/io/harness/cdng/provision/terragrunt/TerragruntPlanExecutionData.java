/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

/*

  * Copyright 2022 Harness Inc. All rights reserved.
  * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
  * that can be found in the licenses directory at the root of this repository, also available at
  * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

 */

package io.harness.cdng.provision.terragrunt;

import static io.harness.beans.SwaggerConstants.BOOLEAN_CLASSPATH;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.string;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.cdng.provision.terragrunt.TerragruntPlanExecutionDataParameters.TerragruntPlanExecutionDataParametersBuilder;
import io.harness.data.structure.EmptyPredicate;
import io.harness.pms.yaml.ParameterField;
import io.harness.validation.Validator;
import io.harness.yaml.YamlSchemaTypes;
import io.harness.yaml.core.VariableExpression;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.utils.NGVariablesUtils;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;
import java.util.LinkedHashMap;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.CDP)
@FieldNameConstants(innerTypeName = "TerragruntPlanExecutionDataKeys")
@RecasterAlias("io.harness.cdng.provision.terragrunt.TerragruntPlanExecutionData")
public class TerragruntPlanExecutionData {
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) String uuid;
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> workspace;
  @NotNull @JsonProperty("configFiles") TerragruntConfigFilesWrapper terragruntConfigFilesWrapper;
  @JsonProperty("varFiles") List<TerragruntVarFileWrapper> terragruntVarFiles;
  @JsonProperty("backendConfig") TerragruntBackendConfig terragruntBackendConfig;
  @YamlSchemaTypes(value = {string})
  @ApiModelProperty(dataType = SwaggerConstants.STRING_LIST_CLASSPATH)
  ParameterField<List<String>> targets;
  List<NGVariable> environmentVariables;
  @NotNull TerragruntPlanCommand command;
  @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> secretManagerRef;
  @ApiModelProperty(dataType = BOOLEAN_CLASSPATH)
  @YamlSchemaTypes({string})
  ParameterField<Boolean> exportTerragruntPlanJson;
  @NotNull @JsonProperty("moduleConfig") TerragruntModuleConfig terragruntModuleConfig;
  @VariableExpression(skipVariableExpression = true) List<TerragruntCliOptionFlag> commandFlags;

  public TerragruntPlanExecutionDataParameters toStepParameters() {
    validateParams();
    TerragruntPlanExecutionDataParametersBuilder builder =
        TerragruntPlanExecutionDataParameters.builder()
            .workspace(workspace)
            .configFiles(terragruntConfigFilesWrapper)
            .backendConfig(terragruntBackendConfig)
            .targets(targets)
            .environmentVariables(NGVariablesUtils.getMapOfVariables(environmentVariables, 0L))
            .command(command)
            .secretManagerRef(secretManagerRef)
            .terragruntModuleConfig(terragruntModuleConfig)
            .exportTerragruntPlanJson(exportTerragruntPlanJson);
    LinkedHashMap<String, TerragruntVarFile> varFiles = new LinkedHashMap<>();
    if (EmptyPredicate.isNotEmpty(terragruntVarFiles)) {
      terragruntVarFiles.forEach(terragruntVarFile -> {
        if (terragruntVarFile != null) {
          TerragruntVarFile varFile = terragruntVarFile.getVarFile();
          if (varFile != null) {
            varFiles.put(varFile.getIdentifier(), varFile);
          }
        }
      });
    }
    builder.varFiles(varFiles);
    builder.cliOptionFlags(commandFlags);
    return builder.build();
  }

  public void validateParams() {
    Validator.notNullCheck("Config files are null", terragruntConfigFilesWrapper);
    terragruntConfigFilesWrapper.validateParams();
    Validator.notNullCheck("Terragrunt Plan command is null", command);
    Validator.notNullCheck("Secret Manager Ref for Terragrunt plan is null", secretManagerRef);
  }
}
