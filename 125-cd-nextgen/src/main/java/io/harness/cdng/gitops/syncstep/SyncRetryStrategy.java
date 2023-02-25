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
@RecasterAlias("io.harness.cdng.gitops.syncstep.SyncRetryStrategy")
public class SyncRetryStrategy {
  @YamlSchemaTypes(value = {string})
  @ApiModelProperty(dataType = SwaggerConstants.INTEGER_CLASSPATH)
  public ParameterField<Integer> limit;

  @YamlSchemaTypes(value = {runtime})
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  public ParameterField<String> retryBackoffBaseDuration;

  @YamlSchemaTypes(value = {string})
  @ApiModelProperty(dataType = SwaggerConstants.INTEGER_CLASSPATH)
  public ParameterField<Integer> waitAfterRetryFactor;

  @YamlSchemaTypes(value = {runtime})
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  public ParameterField<String> maxRetryBackoffDuration;
}
