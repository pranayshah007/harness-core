/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.gitops.syncstep;

import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.runtime;

import io.harness.beans.SwaggerConstants;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.YamlSchemaTypes;

import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AgentApplicationTargets {
  @YamlSchemaTypes(value = {runtime})
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  public ParameterField<String> agentId;

  @YamlSchemaTypes(value = {runtime})
  @ApiModelProperty(dataType = SwaggerConstants.STRING_LIST_CLASSPATH)
  public ParameterField<String> applicationName;
}
