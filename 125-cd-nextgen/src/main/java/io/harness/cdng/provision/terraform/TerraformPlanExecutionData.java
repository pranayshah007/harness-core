package io.harness.cdng.provision.terraform;

import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.string;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.common.SwaggerConstants;
import io.harness.cdng.provision.terraform.TerraformPlanExecutionDataParameters.TerraformPlanExecutionDataParametersBuilder;
import io.harness.data.structure.EmptyPredicate;
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
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.CDP)
public class TerraformPlanExecutionData {
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> workspace;
  @NotNull @JsonProperty("configFiles") TerraformConfigFilesWrapper terraformConfigFilesWrapper;
  @JsonProperty("varFiles") List<TerraformVarFileWrapper> terraformVarFiles;
  @JsonProperty("backendConfig") TerraformBackendConfig terraformBackendConfig;
  @YamlSchemaTypes(value = {string})
  @ApiModelProperty(dataType = SwaggerConstants.STRING_LIST_CLASSPATH)
  ParameterField<List<String>> targets;
  List<NGVariable> environmentVariables;

  @NotNull TerraformPlanCommand command;
  @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> secretManagerRef;

  public TerraformPlanExecutionDataParameters toStepParameters() {
    validateParams();
    TerraformPlanExecutionDataParametersBuilder builder =
        TerraformPlanExecutionDataParameters.builder()
            .workspace(workspace)
            .configFiles(terraformConfigFilesWrapper)
            .backendConfig(terraformBackendConfig)
            .targets(targets)
            .environmentVariables(NGVariablesUtils.getMapOfVariables(environmentVariables, 0L))
            .command(command)
            .secretManagerRef(secretManagerRef);
    LinkedHashMap<String, TerraformVarFile> varFiles = new LinkedHashMap<>();
    if (EmptyPredicate.isNotEmpty(terraformVarFiles)) {
      terraformVarFiles.forEach(terraformVarFile -> {
        if (terraformVarFile != null) {
          TerraformVarFile varFile = terraformVarFile.getVarFile();
          if (varFile != null) {
            varFiles.put(varFile.getIdentifier(), varFile);
          }
        }
      });
    }
    builder.varFiles(varFiles);
    return builder.build();
  }

  public void validateParams() {
    Validator.notNullCheck("Config files are null", terraformConfigFilesWrapper);
    terraformConfigFilesWrapper.validateParams();
    Validator.notNullCheck("Terraform Plan command is null", command);
    Validator.notNullCheck("Secret Manager Ref for Tf plan is null", secretManagerRef);
  }
}
