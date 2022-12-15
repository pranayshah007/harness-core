/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.strategy;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.beans.SwaggerConstants.INTEGER_CLASSPATH;
import static io.harness.beans.SwaggerConstants.STRING_LIST_CLASSPATH;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.expression;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.yaml.ParameterField;
import io.harness.validation.OneOfField;
import io.harness.yaml.YamlSchemaTypes;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import javax.validation.constraints.Min;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@OwnedBy(PIPELINE)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@RecasterAlias("io.harness.plancreator.strategy.HarnessForConfig")
@OneOfField(fields = {"items", "times"})
public class HarnessForConfig {
  @YamlSchemaTypes(value = {expression})
  @JsonProperty("times")
  @ApiModelProperty(dataType = INTEGER_CLASSPATH)
  @Min(value = 0)
  ParameterField<Integer> times;

  @ApiModelProperty(dataType = INTEGER_CLASSPATH)
  @YamlSchemaTypes(value = {expression})
  @JsonProperty("maxConcurrency")
  @Min(value = 0)
  ParameterField<Integer> maxConcurrency;

  @YamlSchemaTypes(value = {expression})
  @ApiModelProperty(dataType = STRING_LIST_CLASSPATH)
  @JsonProperty("items")
  ParameterField<List<String>> items;

  @ApiModelProperty(dataType = INTEGER_CLASSPATH)
  @YamlSchemaTypes(value = {expression})
  @JsonProperty("partitionSize")
  @Min(0)
  ParameterField<Integer> partitionSize;

  @ApiModelProperty(dataType = INTEGER_CLASSPATH)
  @YamlSchemaTypes(value = {expression})
  @JsonProperty("start")
  @Min(0)
  ParameterField<Integer> start;

  @ApiModelProperty(dataType = INTEGER_CLASSPATH)
  @YamlSchemaTypes(value = {expression})
  @JsonProperty("end")
  @Min(0)
  ParameterField<Integer> end;

  RepeatUnit unit;
}
