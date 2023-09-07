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

import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.string;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.SwaggerConstants;
import io.harness.cdng.provision.terragrunt.TerragruntExecutionDataParameters.TerragruntExecutionDataParametersBuilder;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.pms.yaml.ParameterField;
import io.harness.validation.Validator;
import io.harness.yaml.YamlSchemaTypes;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.utils.NGVariablesUtils;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;
import java.util.LinkedHashMap;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.apache.commons.lang3.StringUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_INFRA_PROVISIONERS})
@Data
@NoArgsConstructor
@OwnedBy(HarnessTeam.CDP)
@FieldDefaults(level = AccessLevel.PRIVATE)
@RecasterAlias("io.harness.cdng.provision.terragrunt.TerragruntExecutionData")
public class TerragruntExecutionData {
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) String uuid;

  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> workspace;
  @NotNull @JsonProperty("configFiles") TerragruntConfigFilesWrapper terragruntConfigFilesWrapper;
  @JsonProperty("varFiles") List<TerragruntVarFileWrapper> terragruntVarFiles;
  @JsonProperty("backendConfig") TerragruntBackendConfig terragruntBackendConfig;
  @YamlSchemaTypes(value = {string})
  @ApiModelProperty(dataType = SwaggerConstants.STRING_LIST_CLASSPATH)
  ParameterField<List<String>> targets;
  List<NGVariable> environmentVariables;
  @NotNull @JsonProperty("moduleConfig") TerragruntModuleConfig terragruntModuleConfig;

  public TerragruntExecutionDataParameters toStepParameters() {
    validateParams();
    TerragruntExecutionDataParametersBuilder builder =
        TerragruntExecutionDataParameters.builder()
            .workspace(workspace)
            .configFiles(terragruntConfigFilesWrapper)
            .backendConfig(terragruntBackendConfig)
            .targets(targets)
            .terragruntModuleConfig(terragruntModuleConfig)
            .moduleConfig(terragruntModuleConfig)
            .environmentVariables(NGVariablesUtils.getMapOfVariables(environmentVariables, 0L));
    LinkedHashMap<String, TerragruntVarFile> varFiles = new LinkedHashMap<>();
    if (EmptyPredicate.isNotEmpty(terragruntVarFiles)) {
      terragruntVarFiles.forEach(terragruntVarFile -> {
        if (terragruntVarFile != null) {
          TerragruntVarFile varFile = terragruntVarFile.getVarFile();
          if (varFile != null) {
            if (StringUtils.isEmpty(varFile.getIdentifier())) {
              throw new InvalidRequestException("Identifier in Var File can't be empty", WingsException.USER);
            }
            varFiles.put(varFile.getIdentifier(), varFile);
          }
        }
      });
    }
    builder.varFiles(varFiles);
    return builder.build();
  }

  void validateParams() {
    Validator.notNullCheck("Config files are null", terragruntConfigFilesWrapper);
    terragruntConfigFilesWrapper.validateParams();
    Validator.notNullCheck("Module Config is null", terragruntModuleConfig);
    terragruntModuleConfig.validateParams();
  }
}
