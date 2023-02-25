package io.harness.cdng.gitops.syncstep;

import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.runtime;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.string;

import io.harness.annotation.RecasterAlias;
import io.harness.beans.SwaggerConstants;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.YamlSchemaTypes;

import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@RecasterAlias("io.harness.cdng.gitops.syncstep.SyncOptions")
public class SyncOptions {
  @YamlSchemaTypes(value = {string})
  @ApiModelProperty(dataType = SwaggerConstants.BOOLEAN_CLASSPATH)
  public ParameterField<Boolean> skipSchemaValidation;

  @YamlSchemaTypes(value = {string})
  @ApiModelProperty(dataType = SwaggerConstants.BOOLEAN_CLASSPATH)
  public ParameterField<Boolean> autoCreateNamespace;

  @YamlSchemaTypes(value = {string})
  @ApiModelProperty(dataType = SwaggerConstants.BOOLEAN_CLASSPATH)
  public ParameterField<Boolean> pruneResourcesAtLast;

  @YamlSchemaTypes(value = {string})
  @ApiModelProperty(dataType = SwaggerConstants.BOOLEAN_CLASSPATH)
  public ParameterField<Boolean> applyOutOfSyncOnly;

  @YamlSchemaTypes(value = {string})
  @ApiModelProperty(dataType = SwaggerConstants.BOOLEAN_CLASSPATH)
  public ParameterField<Boolean> replaceResources;

  @YamlSchemaTypes(value = {runtime})
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  public ParameterField<String> prunePropagationPolicy;
}
