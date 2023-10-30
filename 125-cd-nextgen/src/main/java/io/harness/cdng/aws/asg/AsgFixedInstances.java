/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.aws.asg;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.SwaggerConstants.INTEGER_CLASSPATH;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.YamlSchemaTypes;
import io.harness.yaml.schema.beans.SupportedPossibleFieldTypes;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(CDP)
@Value
@Data
@Builder
@JsonTypeName("Fixed")
@TypeAlias("AsgFixedInstances")
@RecasterAlias("io.harness.cdng.aws.asg.AsgFixedInstances")
public class AsgFixedInstances implements AsgInstancesSpec {
  @YamlSchemaTypes({SupportedPossibleFieldTypes.expression})
  @ApiModelProperty(dataType = INTEGER_CLASSPATH)
  @NotNull
  ParameterField<Integer> desired;

  @YamlSchemaTypes({SupportedPossibleFieldTypes.expression})
  @ApiModelProperty(dataType = INTEGER_CLASSPATH)
  @NotNull
  ParameterField<Integer> min;

  @YamlSchemaTypes({SupportedPossibleFieldTypes.expression})
  @ApiModelProperty(dataType = INTEGER_CLASSPATH)
  @NotNull
  ParameterField<Integer> max;

  @Override
  @JsonIgnore
  public AsgInstancesType getType() {
    return AsgInstancesType.FIXED;
  }
}
