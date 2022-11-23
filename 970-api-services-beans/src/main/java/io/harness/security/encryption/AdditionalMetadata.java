/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.security.encryption;

import io.harness.beans.SwaggerConstants;

import io.swagger.annotations.ApiModelProperty;
import java.util.Map;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Singular;
import lombok.Value;

@Value
@Builder
@EqualsAndHashCode
public class AdditionalMetadata {
  @ApiModelProperty(dataType = SwaggerConstants.STRING_MAP_CLASSPATH) @Singular private Map<String, Object> values;
}
