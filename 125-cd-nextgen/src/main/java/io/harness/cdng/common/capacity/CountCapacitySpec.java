/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.common.capacity;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.SwaggerConstants.INTEGER_CLASSPATH;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.expression;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.yaml.YamlSchemaTypes;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import javax.validation.constraints.Min;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;

@Data
@Builder
@JsonTypeName(CapacitySpecType.COUNT)
@OwnedBy(CDP)
@RecasterAlias("io.harness.cdng.common.capacity.CountCapacitySpec")
public class CountCapacitySpec implements CapacitySpec {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  String uuid;

  @ApiModelProperty(dataType = INTEGER_CLASSPATH)
  @YamlSchemaTypes(value = {expression})
  @JsonProperty("count")
  @Min(0)
  ParameterField<Integer> count;

  @Override
  public String getType() {
    return CapacitySpecType.COUNT;
  }
}
