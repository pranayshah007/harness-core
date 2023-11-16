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
import java.util.List;
import javax.validation.constraints.NotEmpty;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Value;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@NoArgsConstructor
public class TrafficRoutingHeaderRuleSpec extends TrafficRoutingRuleSpec {
  List<HeaderSpec> values;
  @FieldDefaults(level = AccessLevel.PRIVATE)
  @JsonIgnoreProperties(ignoreUnknown = true)
  @Builder
  @AllArgsConstructor
  @Value
  static class HeaderSpec {
    @NotEmpty @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> key;
    @NotEmpty @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> value;
    MatchType matchType;
  }
}
