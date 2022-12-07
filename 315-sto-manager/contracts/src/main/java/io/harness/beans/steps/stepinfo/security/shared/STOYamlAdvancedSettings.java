/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.steps.stepinfo.security.shared;

import static io.harness.annotations.dev.HarnessTeam.STO;
import static io.harness.beans.SwaggerConstants.BOOLEAN_CLASSPATH;
import static io.harness.beans.SwaggerConstants.INTEGER_CLASSPATH;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.runtime;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.YamlSchemaTypes;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.codehaus.jackson.annotate.JsonProperty;

@Data
@OwnedBy(STO)
public class STOYamlAdvancedSettings {
  @YamlSchemaTypes(value = {runtime})
  @ApiModelProperty(dataType = "io.harness.beans.steps.stepinfo.security.shared.STOYamlLog")
  protected STOYamlLog log;

  @YamlSchemaTypes(value = {runtime})
  @ApiModelProperty(dataType = "io.harness.beans.steps.stepinfo.security.shared.STOYamlArgs")
  protected STOYamlArgs args;

  @YamlSchemaTypes(value = {runtime})
  @ApiModelProperty(dataType = BOOLEAN_CLASSPATH)
  @JsonProperty("include_raw")
  protected ParameterField<Boolean> includeRaw;

  @YamlSchemaTypes(value = {runtime})
  @ApiModelProperty(dataType = INTEGER_CLASSPATH)
  @JsonProperty("fail_on_severity")
  protected ParameterField<Integer> failOnSeverity;

  @YamlSchemaTypes(value = {runtime})
  @ApiModelProperty(dataType = BOOLEAN_CLASSPATH)
  @JsonProperty("ssl")
  protected ParameterField<Boolean> ssl;
}
