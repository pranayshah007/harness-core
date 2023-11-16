/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.k8s.trafficrouting;

import io.harness.beans.SwaggerConstants;
import io.harness.pms.yaml.ParameterField;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.annotations.ApiModelProperty;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
public class TrafficRouteDestination {
  DestinationSpec destination;

  @Value
  @Builder
  @FieldDefaults(level = AccessLevel.PRIVATE)
  @JsonIgnoreProperties(ignoreUnknown = true)
  static class DestinationSpec {
    @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> host;
    @ApiModelProperty(dataType = SwaggerConstants.INTEGER_CLASSPATH) ParameterField<Integer> weight;
  }
}
