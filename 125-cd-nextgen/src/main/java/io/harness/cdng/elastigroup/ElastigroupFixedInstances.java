/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.elastigroup;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.SwaggerConstants.INTEGER_CLASSPATH;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.expression;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.YamlSchemaTypes;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Data;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

import java.util.Map;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.SwaggerConstants.INTEGER_CLASSPATH;
import static io.harness.beans.SwaggerConstants.STRING_CLASSPATH;
import static io.harness.beans.SwaggerConstants.STRING_MAP_CLASSPATH;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.expression;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.runtime;

@OwnedBy(CDP)
@Value
@Data
@Builder
@JsonTypeName("Fixed")
@TypeAlias("ElastigroupFixedInstances")
@RecasterAlias("io.harness.cdng.elastigroup.ElastigroupFixedInstances")
public class ElastigroupFixedInstances implements ElastigroupInstancesSpec {
  @YamlSchemaTypes({runtime})
  @ApiModelProperty(dataType = STRING_CLASSPATH)
  ParameterField<String> desired;

  @YamlSchemaTypes({runtime})
  @ApiModelProperty(dataType = STRING_CLASSPATH)
  ParameterField<String> min;

  @YamlSchemaTypes({runtime})
  @ApiModelProperty(dataType = STRING_CLASSPATH)
  ParameterField<String> max;

  @Override
  @JsonIgnore
  public ElastigroupInstancesType getType() {
    return ElastigroupInstancesType.FIXED;
  }
}
