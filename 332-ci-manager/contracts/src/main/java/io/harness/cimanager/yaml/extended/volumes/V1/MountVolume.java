/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.yaml.extended.volumes.V1;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.yaml.ParameterField;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

import javax.validation.constraints.NotNull;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.beans.SwaggerConstants.STRING_CLASSPATH;

@Value
@Builder
@AllArgsConstructor
@OwnedBy(CI)
@RecasterAlias("io.harness.beans.yaml.extended.volumes.V1.MountVolume")
public class MountVolume {
  @NotNull @ApiModelProperty(dataType = STRING_CLASSPATH) ParameterField<String> name;
  @NotNull @ApiModelProperty(dataType = STRING_CLASSPATH) ParameterField<String> target;
}
