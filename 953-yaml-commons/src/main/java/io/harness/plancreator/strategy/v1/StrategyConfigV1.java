/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.strategy.v1;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.runtime;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.yaml.YamlSchemaTypes;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@OwnedBy(PIPELINE)
@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@RecasterAlias("io.harness.plancreator.strategy.v1.StrategyConfigV1")
public class StrategyConfigV1 {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  String uuid;
  @JsonProperty("type") StrategyTypeV1 type;
  @JsonProperty("spec") @YamlSchemaTypes(value = {runtime}) ParameterField<MatrixConfigV1> matrixConfig;
}
