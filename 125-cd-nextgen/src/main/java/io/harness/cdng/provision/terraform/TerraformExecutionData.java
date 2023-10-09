/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.provision.terraform;

import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.string;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.SwaggerConstants;
import io.harness.cdng.provision.terraform.TerraformExecutionDataParameters.TerraformExecutionDataParametersBuilder;
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
@RecasterAlias("io.harness.cdng.provision.terraform.TerraformExecutionData")
public class TerraformExecutionData {
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) String uuid;

  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> workspace;
  @NotNull @JsonProperty("configFiles") TerraformConfigFilesWrapper terraformConfigFilesWrapper;
  @JsonProperty("varFiles") List<TerraformVarFileWrapper> terraformVarFiles;
  @JsonProperty("backendConfig") TerraformBackendConfig terraformBackendConfig;
  @YamlSchemaTypes(value = {string})
  @ApiModelProperty(dataType = SwaggerConstants.STRING_LIST_CLASSPATH)
  ParameterField<List<String>> targets;
  List<NGVariable> environmentVariables;

  @JsonProperty("providerCredential") TerraformProviderCredential providerCredential;

  public TerraformExecutionDataParameters toStepParameters() {
    validateParams();
    TerraformExecutionDataParametersBuilder builder =
        TerraformExecutionDataParameters.builder()
            .workspace(workspace)
            .configFiles(terraformConfigFilesWrapper)
            .backendConfig(terraformBackendConfig)
            .targets(targets)
            .environmentVariables(NGVariablesUtils.getMapOfVariables(environmentVariables, 0L));
    LinkedHashMap<String, TerraformVarFile> varFiles = new LinkedHashMap<>();
    if (EmptyPredicate.isNotEmpty(terraformVarFiles)) {
      terraformVarFiles.forEach(terraformVarFile -> {
        if (terraformVarFile != null) {
          TerraformVarFile varFile = terraformVarFile.getVarFile();
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
    builder.isTerraformCloudCli(ParameterField.createValueField(false));
    builder.providerCredential(providerCredential);
    return builder.build();
  }

  void validateParams() {
    Validator.notNullCheck("Config files are null", terraformConfigFilesWrapper);
    terraformConfigFilesWrapper.validateParams();
  }
}
