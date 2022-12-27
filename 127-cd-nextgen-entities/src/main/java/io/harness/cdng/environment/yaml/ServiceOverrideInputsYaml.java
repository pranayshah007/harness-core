/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.environment.yaml;

import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.runtime;

import io.harness.beans.SwaggerConstants;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.YamlSchemaTypes;

import io.swagger.annotations.ApiModelProperty;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ServiceOverrideInputsYaml {
  @NotNull String serviceRef;
  @ApiModelProperty(dataType = SwaggerConstants.JSON_NODE_CLASSPATH)
  @YamlSchemaTypes(runtime)
  ParameterField<Map<String, Object>> serviceOverrideInputs;
}
