/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.steps.stepinfo.security.shared;

import static io.harness.annotations.dev.HarnessTeam.STO;
import static io.harness.beans.SwaggerConstants.BOOLEAN_CLASSPATH;
import static io.harness.beans.SwaggerConstants.STRING_CLASSPATH;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.runtime;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.YamlSchemaTypes;
import io.harness.yaml.sto.variables.STOYamlAuthType;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.annotations.ApiModelProperty;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@OwnedBy(STO)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class STOYamlAuth {
  @ApiModelProperty(dataType = STRING_CLASSPATH, name = "access_id") protected ParameterField<String> accessId;

  @NotNull
  @ApiModelProperty(dataType = STRING_CLASSPATH, name = "access_token")
  protected ParameterField<String> accessToken;

  @ApiModelProperty(dataType = STRING_CLASSPATH) protected ParameterField<String> region;

  @ApiModelProperty(dataType = STRING_CLASSPATH) protected ParameterField<String> version;

  @ApiModelProperty(dataType = STRING_CLASSPATH) protected ParameterField<String> domain;

  @YamlSchemaTypes(value = {runtime})
  @ApiModelProperty(dataType = "io.harness.yaml.sto.variables.STOYamlAuthType")
  protected STOYamlAuthType type;

  @YamlSchemaTypes(value = {runtime})
  @ApiModelProperty(dataType = BOOLEAN_CLASSPATH)
  protected ParameterField<Boolean> ssl;
}
