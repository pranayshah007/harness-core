/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.yaml.clone;

import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.runtime;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.yaml.YamlSchemaTypes;
import io.harness.yaml.extended.ci.container.ContainerResource;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.CI)
public class Clone {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  String uuid;
  @ApiModelProperty(dataType = SwaggerConstants.BOOLEAN_CLASSPATH) boolean disabled;
  @YamlSchemaTypes({runtime})
  @ApiModelProperty(dataType = SwaggerConstants.BOOLEAN_CLASSPATH)
  ParameterField<Boolean> insecure;
  @YamlSchemaTypes({runtime})
  @ApiModelProperty(dataType = SwaggerConstants.INTEGER_CLASSPATH)
  ParameterField<Integer> depth;
  @YamlSchemaTypes({runtime})
  @ApiModelProperty(dataType = SwaggerConstants.BOOLEAN_CLASSPATH)
  ParameterField<Boolean> trace;
  @YamlSchemaTypes({runtime})
  @ApiModelProperty(dataType = "io.harness.yaml.clone.Strategy")
  @Builder.Default
  Strategy strategy = Strategy.MERGE;
  public Strategy getStrategy() {
    if (strategy == null) {
      return Strategy.MERGE;
    }
    return strategy;
  }
  @YamlSchemaTypes({runtime}) ParameterField<Ref> ref;
  @YamlSchemaTypes({runtime}) ContainerResource resources;
}
