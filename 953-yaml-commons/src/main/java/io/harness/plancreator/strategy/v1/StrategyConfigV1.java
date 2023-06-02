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
import io.harness.exception.InvalidRequestException;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.serializer.JsonUtils;
import io.harness.yaml.YamlSchemaTypes;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import io.swagger.annotations.ApiModelProperty;
import java.io.IOException;
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
  @YamlSchemaTypes(value = {runtime}) ParameterField<StrategyInfoConfigV1> strategyInfoConfig;

  @JsonSetter("spec")
  public void setSpec(Object value) {
    try {
      String jsonString = JsonUtils.asJson(value);
      switch (this.getType()) {
        case MATRIX:
          strategyInfoConfig = ParameterField.createValueField(YamlUtils.read(jsonString, MatrixConfigV1.class));
          break;
        case FOR:
          strategyInfoConfig = ParameterField.createValueField(YamlUtils.read(jsonString, ForConfigV1.class));
          break;
        default:
          throw new InvalidRequestException(String.format("Strategy Type %s not supported.", this.getType()));
      }
    } catch (IOException ex) {
      throw new InvalidRequestException(
          "Error in mapping the strategy yaml into object. Please check that provided strategy is correct.");
    }
  }
}
