/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.gitops.syncstep;

import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.runtime;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.string;

import io.harness.annotation.RecasterAlias;
import io.harness.beans.SwaggerConstants;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.YamlSchemaTypes;

import io.swagger.annotations.ApiModelProperty;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@RecasterAlias("io.harness.cdng.gitops.syncstep.SyncRetryStrategy")
public class SyncRetryStrategy {
  @YamlSchemaTypes(value = {string})
  @ApiModelProperty(dataType = SwaggerConstants.INTEGER_CLASSPATH)
  @Min(0)
  @NotNull
  public ParameterField<Integer> limit;

  @YamlSchemaTypes(value = {runtime})
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  @NotNull
  public ParameterField<String> baseBackoffDuration;

  @YamlSchemaTypes(value = {string})
  @ApiModelProperty(dataType = SwaggerConstants.INTEGER_CLASSPATH)
  @Min(0)
  @NotNull
  public ParameterField<Integer> increaseBackoffByFactor;

  @YamlSchemaTypes(value = {runtime})
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  @NotNull
  public ParameterField<String> maxBackoffDuration;
}
