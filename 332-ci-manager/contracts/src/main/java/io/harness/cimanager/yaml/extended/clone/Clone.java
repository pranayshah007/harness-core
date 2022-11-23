/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.yaml.extended.clone;

import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.runtime;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.beans.yaml.extended.repository.Repository;
import io.harness.data.structure.UUIDGenerator;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.yaml.YamlSchemaTypes;
import io.harness.yaml.core.VariableExpression;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@OwnedBy(HarnessTeam.CI)
@Data
@Builder
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@RecasterAlias("io.harness.beans.Clone")
public class Clone {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  @Builder.Default
  String uuid = UUIDGenerator.generateUuid();
  @YamlSchemaTypes({runtime})
  @ApiModelProperty(dataType = SwaggerConstants.BOOLEAN_CLASSPATH)
  @Builder.Default
  ParameterField<Boolean> disabled = ParameterField.createValueField(false);
  @YamlSchemaTypes({runtime})
  @ApiModelProperty(dataType = SwaggerConstants.BOOLEAN_CLASSPATH)
  @Builder.Default
  ParameterField<Boolean> insecure = ParameterField.createValueField(false);
  @YamlSchemaTypes({runtime})
  @ApiModelProperty(dataType = SwaggerConstants.INTEGER_CLASSPATH)
  ParameterField<Integer> depth;
  @YamlSchemaTypes({runtime})
  @ApiModelProperty(dataType = SwaggerConstants.BOOLEAN_CLASSPATH)
  @Builder.Default
  ParameterField<Boolean> trace = ParameterField.createValueField(false);
  @YamlSchemaTypes({runtime}) @VariableExpression(skipVariableExpression = true) ParameterField<Repository> repository;
}
