/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.steps.stepinfo.security.shared;

import static io.harness.annotations.dev.HarnessTeam.STO;
import static io.harness.beans.SwaggerConstants.STRING_CLASSPATH;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.yaml.ParameterField;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.codehaus.jackson.annotate.JsonProperty;

@Data
@OwnedBy(STO)
public class STOYamlMendToolData {
  @ApiModelProperty(dataType = STRING_CLASSPATH) protected ParameterField<String> include;

  @ApiModelProperty(dataType = STRING_CLASSPATH) protected ParameterField<String> exclude;

  @ApiModelProperty(dataType = STRING_CLASSPATH, name = "product_name")
  @JsonProperty("product_name")
  protected ParameterField<String> productName;

  @ApiModelProperty(dataType = STRING_CLASSPATH, name = "product_token")
  @JsonProperty("product_token")
  protected ParameterField<String> productToken;

  @ApiModelProperty(dataType = STRING_CLASSPATH, name = "project_name")
  @JsonProperty("project_name")
  protected ParameterField<String> projectName;

  @ApiModelProperty(dataType = STRING_CLASSPATH, name = "project_token")
  @JsonProperty("project_token")
  protected ParameterField<String> projectToken;
}
